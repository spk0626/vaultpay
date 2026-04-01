package com.vaultpay.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 / Swagger configuration.
 *
 * Access the interactive UI at: http://localhost:8080/swagger-ui.html
 * Access the raw JSON spec at:  http://localhost:8080/v3/api-docs
 *
 * The security scheme registered here ("bearerAuth") lets the Swagger UI show an "Authorize" button where testers can paste their JWT token and then make authenticated requests directly in the browser.
 */

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI vaultPayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("VaultPay API")
                        .description("Digital Wallet & P2P Payment Service")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("VaultPay")
                                .email("dev@vaultpay.com")))
                // Register JWT as the global security mechanism
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token (without 'Bearer ' prefix)")));
    }
}
