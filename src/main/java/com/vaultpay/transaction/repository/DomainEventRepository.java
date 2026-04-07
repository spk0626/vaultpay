package com.vaultpay.transaction.repository;

import com.vaultpay.transaction.domain.DomainEvent;
import com.vaultpay.transaction.domain.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the outbox domain events table.
 *
 * findTop50ByStatusOrderByCreatedAtAsc → the polling query used by DomainEventPublisher.
 *   - "Top50" limits the batch size per poll cycle to avoid memory pressure.
 *   - "OrderByCreatedAtAsc" ensures events are published in the order they were created.
 *   - "ByStatus" filters only PENDING events — already published ones are skipped.
 *
 * Spring Data generates the full query from the method name:
 *   SELECT * FROM domain_events WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 50
 */
public interface DomainEventRepository extends JpaRepository<DomainEvent, UUID> {

    List<DomainEvent> findTop50ByStatusOrderByCreatedAtAsc(EventStatus status);
}