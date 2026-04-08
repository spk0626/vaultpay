package com.vaultpay.transaction.domain;

import jakarta.persistence.*;     // persistence is the java standard for mapping objects to relational databases. It defines annotations and interfaces that allow developers to work with databases using Java objects.
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

// rabbitmq

/**
 * DomainEvent — the outbox table entry for the Outbox Pattern.  Outbox pattern is a reliable way to ensure that important events are not lost even if the app crashes after DB commit but before publishing to RabbitMQ.
 *
 * THE PROBLEM THIS SOLVES:
 *   Naïve approach: inside @Transactional, do DB work then call rabbitTemplate.send().
 *   Risk: DB commits successfully, then the app crashes before RabbitMQ call. Event is lost.
 *
 * THE OUTBOX SOLUTION:
 *   Inside the SAME DB transaction, write the event to this table.
 *   A separate @Scheduled job (DomainEventPublisher) polls for PENDING events,
 *   publishes to RabbitMQ, then marks them PUBLISHED.
 *   If the app crashes after DB commit but before publishing, the event is still
 *   PENDING in the DB and will be published on the next scheduler poll cycle.
 *
 * PAYLOAD AS JSON STRING:
 *   We store the event payload as a JSON string (maps to PostgreSQL JSONB column).
 *   This avoids the need for custom JPA converters while remaining queryable in the DB.
 *   The NotificationListener deserializes it back to a typed object.
 *
 * NOT extending Auditable because:
 *   - We don't need updatedAt (events don't change, only their status field updates)
 *   - We manage publishedAt manually (only set when status becomes PUBLISHED)
 */
@Entity
@Table(name = "domain_events")
@EntityListeners(AuditingEntityListener.class) // Automatically sets createdAt when the entity is persisted
@Getter
@Setter       // Setter only on status and publishedAt — both are updated by the publisher job
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA requires a no-args constructor; protected to prevent direct instantiation without using the builder
@AllArgsConstructor   // All-args constructor for the builder; not intended for direct use
public class DomainEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Which aggregate type produced this event, e.g. "TRANSACTION" */
    @Column(name = "aggregate_type", nullable = false, updatable = false)
    private String aggregateType;

    /** The ID of the aggregate (e.g. the Transaction UUID) */
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    /** Human-readable event name, e.g. "TRANSFER_COMPLETED", "DEPOSIT_COMPLETED" */
    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    /** JSON payload — serialized by TransactionService, deserialized by NotificationListener */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EventStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Set when the event is successfully published to RabbitMQ */
    @Column(name = "published_at")
    private Instant publishedAt;
}