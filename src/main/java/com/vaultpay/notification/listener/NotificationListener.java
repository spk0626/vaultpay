package com.vaultpay.notification.listener;

import com.vaultpay.notification.config.NotificationEvent;
import com.vaultpay.notification.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ message consumer — the notification handler.  Acts on domain events published by DomainEventPublisher.
 *
 * @RabbitListener binds this method to the notification queue.
 * When a message arrives, Spring AMQP:
 *   1. Deserializes the JSON body into a NotificationEvent (via Jackson2JsonMessageConverter)
 *   2. Calls this method with the typed object
 *   3. ACKs the message automatically if the method returns normally
 *   4. Routes to DLQ if the method throws (configured in RabbitMQConfig)
 *
 * WHAT A REAL IMPLEMENTATION WOULD DO:
 *   - Look up the user's email by userId from the payload
 *   - Call an email service (SendGrid, SES) to send a "transfer received" notification
 *   - Push a mobile notification via FCM/APNs
 *   - Store an in-app notification record
 *
 *  the log statement can be later extended for a email. 
 */
@Slf4j
@Component
public class NotificationListener {

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void handleNotification(NotificationEvent event) {
        log.info("═══ NOTIFICATION RECEIVED ══════════════════════════════");
        log.info("  Event Type   : {}", event.eventType());
        log.info("  Transaction  : {}", event.aggregateId());
        log.info("  Occurred At  : {}", event.occurredAt());
        log.info("  Payload      : {}", event.payload());
        log.info("═══════════════════════════════════════════════════════");

        switch (event.eventType()) {
            case "TRANSFER_COMPLETED" -> handleTransferCompleted(event);
            case "DEPOSIT_COMPLETED"  -> handleDepositCompleted(event);
            case "WITHDRAWAL_COMPLETED" -> handleWithdrawalCompleted(event);
            default -> log.warn("Unhandled event type: {}", event.eventType());
        }
    }

    private void handleTransferCompleted(NotificationEvent event) {
        Object senderId   = event.payload().get("senderId");
        Object receiverId = event.payload().get("receiverId");
        Object amount     = event.payload().get("amountInCents");

        log.info("[NOTIFICATION] Transfer completed — sender={}, receiver={}, amount={} cents",
                senderId, receiverId, amount);
        // TODO: send email to both sender (debit confirmation) and receiver (credit alert)
    }

    private void handleDepositCompleted(NotificationEvent event) {
        Object userId = event.payload().get("userId");
        Object amount = event.payload().get("amountInCents");

        log.info("[NOTIFICATION] Deposit completed — user={}, amount={} cents", userId, amount);
        // TODO: send deposit confirmation email
    }

    private void handleWithdrawalCompleted(NotificationEvent event) {
        Object userId = event.payload().get("userId");
        Object amount = event.payload().get("amountInCents");

        log.info("[NOTIFICATION] Withdrawal completed — user={}, amount={} cents", userId, amount);
        // TODO: send withdrawal confirmation email
    }
}