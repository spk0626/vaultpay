package com.vaultpay.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
 
import java.io.IOException;

/**
 * JWT Authentication Filter — runs on every HTTP request.
 *
 * REQUEST FLOW:
 *   1. Client sends:  Authorization: Bearer eyJhbGc...
 *   2. This filter extracts the token from the header
 *   3. Validates the token signature and expiry
 *   4. Loads the user from DB (to verify they still exist and are active)
 *   5. Sets the Authentication in SecurityContextHolder
 *   6. Downstream code (@PreAuthorize, controllers) can now access the current user
 *
 * If the token is missing or invalid, the filter simply passes the request along
 * without setting Authentication. Spring Security's authorization rules then
 * reject the request if the endpoint requires authentication.
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
 
    private static final String BEARER_PREFIX = "Bearer ";   // standard prefix for JWT tokens in the Authorization header
    private static final String AUTH_HEADER   = "Authorization";  // the HTTP header where the JWT is expected to be sent by the client
 
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
 
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {                            // this method is called for every HTTP request. We will attempt to authenticate the user based on the JWT token in the Authorization header.
      
        final String authHeader = request.getHeader(AUTH_HEADER);
 
        // No Bearer token → skip (the request may be to a public endpoint like /auth/login)
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
 
        final String token = authHeader.substring(BEARER_PREFIX.length());
 
        try {
            final String email = jwtService.extractEmail(token);
 
            // Only authenticate if: email extracted AND no existing authentication in context
            // (avoids re-authenticating on the same request if already set)
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
 
                if (jwtService.isTokenValid(token, userDetails)) {
                    // Create an authentication token (no credentials needed — JWT already verified)
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,                       // credentials — not needed post-auth
                                    userDetails.getAuthorities()
                            );                                                       // authToken is a fully authenticated token with user details and authorities (roles/permissions)
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));  // adds request info (like IP) to the authentication details
 
                    // Place authentication into the SecurityContext for this request's thread
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authenticated user: {}", email);
                }
            }
        } catch (Exception e) {
            log.warn("JWT authentication failed: {}", e.getMessage());
            // Don't throw — just let the request continue unauthenticated
        }
 
        filterChain.doFilter(request, response);
    }
    // inputs:
    // - HttpServletRequest: the incoming HTTP request, from which we read the Authorization header.
    // - HttpServletResponse: the HTTP response, which we can modify if needed (e.g., to add error messages).
    // - FilterChain: allows us to pass the request to the next filter in the chain after we're done processing. Next filter could be Spring Security's authorization filter, which will check if the user is authenticated and has access to the endpoint.

    // process:
    // 1. Read the Authorization header and check if it starts with "Bearer ". If not, skip authentication and continue the filter chain.
    // 2. Extract the JWT token from the header by removing the "Bearer " prefix.
    // 3. Use JwtService to extract the email (subject) from the token.
    // 4. If an email is extracted and there's no existing authentication in the SecurityContext, load the user details from the database.
    // 5. Validate the token against the user details (check signature, expiry, and that the email matches).
    // 6. If valid, create an authenticated UsernamePasswordAuthenticationToken and set it in the SecurityContext.
    // 7. If any step fails (e.g., token invalid, user not found), log a warning but don't throw an exception. Just continue the filter chain without setting authentication, which will lead to a 401 Unauthorized if the endpoint requires authentication.

    // output:
    // - If authentication is successful, the SecurityContext will have an authenticated token with user details for the rest of the request processing.
    // - If authentication fails, the SecurityContext will remain unauthenticated, and downstream authorization checks will reject the request if it tries to access protected resources.

    // Difference between token and authToken:
    // - token: the raw JWT string extracted from the Authorization header (e.g., "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...").
    // - authToken: the UsernamePasswordAuthenticationToken object we create after validating the JWT, which contains the user's details and authorities. This is what we set in the SecurityContext to represent the authenticated user for the current request.

    // Flow between jwt folder classes from high level to low level:
    // 1. JwtAuthenticationFilter: intercepts HTTP requests, extracts the JWT token, and uses JwtService to validate it and set authentication.
    // 2. JwtService: contains the logic to create JWTs, extract information from tokens, and validate them. It uses the secret key from JwtProperties to sign and verify tokens.
    // 3. JwtProperties: holds the JWT configuration properties (secret key and expiration time) loaded from application.yml. This allows us to easily manage JWT settings in a centralized configuration file.

    // User -> JwtAuthenticationFilter -> JwtService -> JwtProperties -> application.yml

    // SignUp flow:
    // 1. User registers with email and password.
    // 2. User logs in with email and password.
    // 3. AuthenticationController authenticates credentials and calls JwtService to create a JWT token.
    // 4. JwtService generates a JWT token with the user's email as the subject and signs it with the secret key from JwtProperties.
    // 5. The token is returned to the client, which stores it (e.g., in localStorage).
    // 6. On subsequent requests, the client sends the token in the Authorization header.

    // Why do we need a filter for JWT authentication? Where does it fit in sign up/login flow?
    // - JWTs are stateless tokens that clients send with each request. We need a filter to intercept incoming requests, extract the token, validate it, and set the authentication context for the request. 
    // - This allows our application to know which user is making the request and what permissions they have, without needing to maintain server-side sessions.
    // - The filter fits into the flow after the user has logged in and received a JWT. For all subsequent requests, the filter checks the token and authenticates the user based on it.

}
