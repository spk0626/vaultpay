package com.vaultpay.transaction.domain;

/**
 * Lifecycle status of an outbox domain event.
 *
 * PENDING   → written to DB, not yet published to RabbitMQ
 * PUBLISHED → successfully published to RabbitMQ
 * FAILED    → publishing attempted and failed (will be retried)
 */
public enum EventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}