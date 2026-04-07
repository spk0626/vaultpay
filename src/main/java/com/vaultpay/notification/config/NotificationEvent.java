package com.vaultpay.notification.config;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The typed message published to RabbitMQ.
 *
 * We define an explicit message type rather than passing raw JSON strings.
 * This gives us:
 *   - Compile-time safety when the publisher builds the message
 *   - Easy deserialization on the listener side via Jackson
 *   - A documented contract between publisher and consumer
 *
 * Jackson will serialize/deserialize this record automatically because
 * our RabbitTemplate is configured with Jackson2JsonMessageConverter.          // Jackson is the JSON procssing library that Spring Boot uses by default
 */
public record NotificationEvent(
        UUID        eventId,
        String      eventType,      // e.g. "TRANSFER_COMPLETED"
        UUID        aggregateId,    // the Transaction ID
        Map<String, Object> payload, // the full event data
        Instant     occurredAt
) {}