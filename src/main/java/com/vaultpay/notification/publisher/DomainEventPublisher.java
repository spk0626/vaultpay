package com.vaultpay.notification.publisher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultpay.notification.config.NotificationEvent;
import com.vaultpay.notification.config.RabbitMQConfig;
import com.vaultpay.transaction.domain.DomainEvent;
import com.vaultpay.transaction.domain.EventStatus;
import com.vaultpay.transaction.repository.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The Outbox Pattern publisher — the second half of event delivery.
 *
 * WHAT THIS DOES:
 *   Every 5 seconds, this job:
 *     1. Reads up to 50 PENDING events from the domain_events table
 *     2. For each event, publishes a message to RabbitMQ
 *     3. Marks the event PUBLISHED (or FAILED if publishing threw)
 *
 * WHY THIS IS BETTER THAN PUBLISHING DIRECTLY IN THE TRANSACTION:
 *
 *   Naïve approach inside @Transactional:
 *     update wallet balance     ← DB operation
 *     save transaction record   ← DB operation
 *     rabbitTemplate.send(...)  ← RabbitMQ operation  ← PROBLEM
 *
 *   If the app crashes after the DB commits but before/during the RabbitMQ call,
 *   the event is silently lost. The user's balance moved but the notification
 *   was never sent. There's no retry, no record that it failed.
 *
 *   With the Outbox Pattern:
 *     update wallet balance             ← same DB transaction
 *     save transaction record           ← same DB transaction
 *     INSERT INTO domain_events (PENDING) ← same DB transaction  ← atomic!
 *     [transaction commits]
 *
 *   Then this scheduler runs and publishes. If it crashes mid-publish,
 *   the event is still PENDING in the DB and will be retried on the next cycle.
 *   At-least-once delivery is guaranteed.
 *
 * ROUTING KEY STRATEGY:
 *   We derive the routing key from the event type:
 *     "TRANSFER_COMPLETED" → "transaction.transfer_completed"
 *   The topic exchange routes all "transaction.*" to our notification queue.
 *
 * @Scheduled(fixedDelay = 5000) → runs 5 seconds after the PREVIOUS run finishes.
 * This avoids overlapping executions if one cycle takes longer than 5 seconds.
 * (fixedRate would cause overlapping; fixedDelay is safer for DB polling jobs.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final DomainEventRepository domainEventRepository;
    private final RabbitTemplate        rabbitTemplate;
    private final ObjectMapper          objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<DomainEvent> pendingEvents =
                domainEventRepository.findTop50ByStatusOrderByCreatedAtAsc(EventStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Publishing {} pending domain events", pendingEvents.size());

        for (DomainEvent event : pendingEvents) {
            try {
                Map<String, Object> payload = deserializePayload(event.getPayload());  // We get a JSON string from the DB, we need to convert it back to a Map to construct the NotificationEvent. The NotificationListener on the consumer side will do the same deserialization.  DB (JSON string) <-> Java Map (NotificationEvent) <-> JSON string (RabbitMQ message) <-> Java object (NotificationListener). Why not just send the json string got from the DB directly to RabbitMQ? Because we want to construct a strongly-typed NotificationEvent object that includes metadata like eventId, occurredAt, etc. This also allows us to have a clear contract for the message structure and leverage Jackson's automatic serialization when sending to RabbitMQ.
// event - > DB (payload as JSON string) 
                NotificationEvent message = new NotificationEvent(
                        event.getId(),
                        event.getEventType(),
                        event.getAggregateId(),
                        payload,
                        Instant.now()    // The occurredAt timestamp is set to now() on publish. This represents when the event was actually published to RabbitMQ, which may be a few seconds after it was created in the DB. This is useful for consumers to know the actual publish time, especially if there are delays or retries.
                );

                // Routing key: e.g. "TRANSFER_COMPLETED" → "transaction.transfer_completed"
                String routingKey = "transaction." + event.getEventType().toLowerCase();

                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_NAME,
                        routingKey,
                        message
                );

                // Mark as published within the same @Transactional — atomic update
                event.setStatus(EventStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                domainEventRepository.save(event);

                log.debug("Published event {} [{}]", event.getEventType(), event.getId());

            } catch (AmqpException e) {
                // RabbitMQ unavailable or message rejected — mark FAILED for inspection
                log.error("Failed to publish event {} [{}]: {}",
                        event.getEventType(), event.getId(), e.getMessage());
                event.setStatus(EventStatus.FAILED);
                domainEventRepository.save(event);

            } catch (Exception e) {
                // Unexpected error — log and continue; don't let one bad event block others
                log.error("Unexpected error publishing event [{}]", event.getId(), e);
                event.setStatus(EventStatus.FAILED);
                domainEventRepository.save(event);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializePayload(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});  // Deserialize JSON string back into a Map
        } catch (Exception e) {
            throw new IllegalStateException("Cannot deserialize event payload: " + json, e);
        }
    }
}