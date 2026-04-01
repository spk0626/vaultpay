package com.vaultpay.common.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Activates Spring Data JPA Auditing.
 *
 * Once enabled, any entity annotated with:
 *   @CreatedDate    → automatically set to now() when the entity is first persisted
 *   @LastModifiedDate → automatically updated on every save
 *   @CreatedBy      → automatically set to the current user's email
 *
 * This means we never need to manually set timestamps — Spring handles it.
 */

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditConfig {
    /**
     * AuditorAware tells Spring WHO the current user is for @CreatedBy / @LastModifiedBy fields.
     * We extract the email from the SecurityContext (set during JWT authentication).
     */
    @Bean
    public AuditorAware<String> auditorProvider(){
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return Optional.of("SYSTEM");
            }
            return Optional.of(auth.getName());
        };
    }
}
