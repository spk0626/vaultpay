package com.vaultpay.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/*
 a Type-safe configuration binding for app.jwt.* properties in application.yml.

 @ConfigurationProperties binds YAML/properties values to Java fields.
 This is the CORRECT way to read custom config.
 We should never use @Value for groups of related properties because it becomes messy and hard to test.

 Spring auto-generates metadata for IDE autocomplete with this annotation.
 
 */

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret;
    private long expirationMs;    
}

// binds the JWT configuration properties from application.yml to Java fields.

// lombok is used to generate getters/setters, so we can easily access these properties in our JwtService without boilerplate code.

// @Getter - generates getter methods for all fields (e.g., getSecret(), getExpirationMs()).
// @Setter - generates setter methods for all fields (e.g., setSecret(String secret), setExpirationMs(long expirationMs)).
// @Component - registers this class as a Spring bean, so it can be injected into other components (like JwtService).
// @ConfigurationProperties(prefix = "app.jwt") - tells Spring to bind properties with the prefix "app.jwt" from application.yml to the fields of this class. For example, app.jwt.secret will be bound to the secret field, and app.jwt.expirationMs will be bound to the expirationMs field.