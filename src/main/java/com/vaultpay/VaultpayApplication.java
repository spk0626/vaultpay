package com.vaultpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * VaultPay Application Entry Point.
 *
 * @SpringBootApplication is a convenience annotation that combines:
 *   - @Configuration      → this class can define Spring beans
 *   - @EnableAutoConfiguration → auto-configure beans based on classpath (e.g. if PostgreSQL driver
 *                                is present, auto-create a DataSource)
 *   - @ComponentScan      → scan this package and all sub-packages for @Component, @Service, etc.
 *
 * @EnableScheduling activates Spring's scheduled task executor, required for
 * the DomainEventPublisher outbox polling job (@Scheduled methods).
 */

@SpringBootApplication
@EnableScheduling
public class VaultpayApplication {

	public static void main(String[] args) {
		SpringApplication.run(VaultpayApplication.class, args);
	}

}
