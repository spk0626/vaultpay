package com.vaultpay.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base class for all entities that need audit timestamps.
 *
 * @MappedSuperclass → JPA knows to include these fields in every subclass table.
 *                     This class itself has no table — its columns appear in the
 *                     tables of entities that extend it (User, Wallet, Transaction).
 *
 * @EntityListeners(AuditingEntityListener.class) → wires up Spring Data's auditing
 *                     listener that populates @CreatedDate and @LastModifiedDate.
 *
 * We use Instant (UTC timestamp) instead of LocalDateTime because financial systems
 * must never be ambiguous about timezone.
 */

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class Auditable {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

}
