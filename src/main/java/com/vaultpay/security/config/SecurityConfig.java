package com.vaultpay.security.config;

import com.vaultpay.common.ratelimit.RateLimitingFilter;
import com.vaultpay.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The central Spring Security configuration.
 *
 * KEY CONCEPTS:

 * SecurityFilterChain — a chain of filters every HTTP request passes through.
 *   It's like a series of security checkpoints.
 *
 * STATELESS session — we don't use HTTP sessions (no cookies, no server-side state).
 *   Every request must carry a JWT. This is standard for REST APIs.
 *
 * CSRF disabled — CSRF protection is for cookie-based session auth. Since we use
 *   stateless JWT (no cookies), CSRF attacks are not possible. Disabling it is correct.  CSRF stands for Cross-Site Request Forgery, which is an attack that tricks a user's browser into making unwanted requests to a different site where the user is authenticated. 
 *
 * AuthenticationProvider — wires together UserDetailsService (loads user from DB)
 *   and PasswordEncoder (verifies BCrypt hash). Spring calls this during login.
 */
@Configuration                          // Marks this class as a source of bean definitions for the application context. Spring will scan this class for @Bean methods and register them as beans in the context.
@EnableWebSecurity                 // Enables Spring Security's web security support and provides the Spring MVC integration. This is required to activate Spring Security in our web application.
@EnableMethodSecurity          // Enables @PreAuthorize on controller methods. We use this to restrict access to certain endpoints based on user roles/permissions.
@RequiredArgsConstructor     // Lombok annotation to generate a constructor for all final fields (like jwtAuthFilter, rateLimitingFilter, userDetailsService). This allows us to use constructor injection for these dependencies.
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimitingFilter      rateLimitingFilter;
    private final UserDetailsService      userDetailsService;

    @Bean                                                                                           // Beans are the building blocks of Spring applications. This method defines a bean of type SecurityFilterChain, which configures how HTTP security works in our application.
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — not needed for stateless JWT APIs
            .csrf(AbstractHttpConfigurer::disable)

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no token required
                .requestMatchers(
                    HttpMethod.POST, "/api/v1/auth/**"
                ).permitAll()
                // Swagger UI and API docs - allow for testing and API exploration without auth 
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()
                // Actuator health — public (metrics are restricted separately)  - allow monitoring tools to check health without auth. Actuator is a set of tools provided by Spring Boot to help monitor and manage the application. The /actuator/health endpoint is used by monitoring systems to check if the application is running and healthy. By permitting all access to this endpoint, we allow external monitoring tools to check the health of our application without requiring authentication.
                .requestMatchers("/actuator/health").permitAll()
                // Everything else requires a valid JWT
                .anyRequest().authenticated()
            )

            // STATELESS: Spring will not create or use an HTTP session
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // to Wire our custom authentication logic using the our method authenticationProvider() below
            .authenticationProvider(authenticationProvider())
 
            // Add JWT filter BEFORE Spring's default username/password filter.
            // Our filter runs first, sets Authentication if JWT is valid, and Spring's filter is then a no-op (already authenticated).
            // Why? Because Spring's default filter looks for form login credentials (username/password) and tries to authenticate. If we put our JWT filter after it, then Spring's filter would run first, see no form credentials, and reject the request before our JWT filter has a chance to authenticate it. By placing our JWT filter before Spring's default filter, we ensure that if a valid JWT is present, the user is authenticated before Spring's filter runs, allowing the request to proceed successfully.
            // why Spring's default filter don't see form credentials? Because we are building a REST API, and clients will send JWTs in the Authorization header instead of form data. Spring's default filter is designed for traditional web applications with form-based login, so it won't find any username/password in the request and would reject it if it ran first. By placing our JWT filter before it, we ensure that our stateless authentication mechanism works correctly without interference from Spring's default behavior.
            // UsernamePasswordAuthenticationFilter.class is the standard Spring Security filter that processes form-based login. 
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // Add rate limiting after JWT auth, so we know who the user is
            .addFilterAfter(rateLimitingFilter, JwtAuthenticationFilter.class);

            // JwtAuthenticationFilter -> RateLimitingFilter -> UsernamePasswordAuthenticationFilter (default Spring filter for form login, which we won't use but must be after our JWT filter)

        return http.build();  // builds the SecurityFilterChain bean that Spring Security uses to secure our application.
    }

    /**
     * Wires UserDetailsService + PasswordEncoder into a single authentication provider.
     * Spring calls this during login to: load the user, then verify the password hash.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();   // deprecated in Spring Security 6.0, but says it still works. 
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());  
        return provider;
    }  // provider consists of:
       // - userDetailsService: loads user info (including hashed password) from the database based on the email (username).
       // - passwordEncoder: used to verify that the provided password matches the stored BCrypt hash during authentication.


    /**
     * BCrypt is the industry standard for password hashing.
     * Strength 10 = ~100ms per hash — slow enough to resist brute force, fast enough for real users logging in.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);    // 10 is strength (log rounds) here. Higher is more secure but slower. 
    }

    /**
     * AuthenticationManager is used in UserService.login() to trigger
     * Spring Security's authentication flow (load user → verify password).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }


    // Purposes of these beans:
    // 1. SecurityFilterChain: defines how HTTP requests are secured (which endpoints require auth, which filters to apply).
    // 2. AuthenticationProvider: defines how to authenticate users (load user details and verify passwords).
    // 3. PasswordEncoder: defines how passwords are hashed and verified (using BCrypt in this case).
    // 4. AuthenticationManager: the main entry point for authentication, which we use in our login service to authenticate user credentials and trigger the authentication flow defined by the AuthenticationProvider.
}

// Highlevel to low-level connection between these classes:
// 1. SecurityConfig: defines the security configuration, including the filter chain and authentication provider.
// 2. JwtAuthenticationFilter: a custom filter that intercepts HTTP requests, extracts and validates JWT tokens, and sets the authentication context.
// 3. JwtService: contains the logic for generating, parsing, and validating JWT tokens. It uses the secret key and expiration settings from JwtProperties.
// 4. JwtProperties: holds the JWT configuration properties (secret key and expiration time) loaded from application.yml. This allows us to easily manage JWT settings in a centralized configuration file.


// During login, the AuthenticationManager (which uses the authenticationProvider) will call the UserDetailsService to load the user from the database and verify the password using the PasswordEncoder. 
// If authentication is successful, we then generate a JWT token for the user using JwtService. 
// The JwtAuthenticationFilter is used for subsequent requests where the user includes the JWT token in the Authorization header. 
// The filter validates the token and sets the authentication context for that request. 

