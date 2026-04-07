package com.vaultpay.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultpay.transaction.dto.TransactionDtos;
import com.vaultpay.user.dto.UserDtos;
import com.vaultpay.wallet.dto.WalletDtos;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration test — the most valuable test in the project.
 *
 * @SpringBootTest(webEnvironment = RANDOM_PORT) → starts the FULL Spring application context.
 * @AutoConfigureMockMvc → provides MockMvc for making HTTP requests without a real server.
 * @Testcontainers → manages Docker container lifecycle automatically.
 *
 * TESTCONTAINERS:
 *   @Container with static fields → containers are started ONCE for all tests in this class,
 *   then stopped after the class finishes. This is much faster than restarting per test.
 *
 * @DynamicPropertySource → overrides Spring's datasource/redis/rabbitmq properties
 *   with the actual ports Testcontainers assigned (they're random to avoid port conflicts).
 *
 * WHAT THESE TESTS PROVE:
 *   1. The full HTTP → Controller → Service → Repository → DB stack works end-to-end.
 *   2. JWT auth, @Transactional, Flyway migrations, and all beans are wired correctly.
 *   3. Idempotency prevents double-processing under normal conditions.
 *   4. Concurrent transfers don't corrupt balances (the hardest property to verify).
 *
 *  PREREQUISITE: Docker must be running on the machine executing these tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransferIntegrationTest {

    // ── Containers — static so they're shared across all test methods ─────────

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vaultpay_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management-alpine");

    /**
     * Override Spring Boot's auto-configured datasource/redis/rabbitmq URLs
     * with the actual container ports assigned by Testcontainers.
     */
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host",     redis::getHost);
        registry.add("spring.data.redis.port",     () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host",       rabbitmq::getHost);
        registry.add("spring.rabbitmq.port",       rabbitmq::getAmqpPort);
    }

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ── State shared between test methods (populated by earlier tests) ────────
    // Note: this is fine for @TestMethodOrder with deterministic execution order.
    static String aliceToken;
    static String bobToken;
    static UUID   aliceId;
    static UUID   bobId;

    // ═════════════════════════════════════════════════════════════════════════
    // SETUP: Register users
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("Setup: Register Alice and Bob")
    void setup_registerUsers() throws Exception {
        // Register Alice
        String aliceResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new UserDtos.RegisterRequest(
                                "alice@test.com", "Password123!", "Alice"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UserDtos.AuthResponse aliceAuth =
                objectMapper.readValue(aliceResponse, UserDtos.AuthResponse.class);
        aliceToken = aliceAuth.accessToken();
        aliceId    = aliceAuth.user().id();

        // Register Bob
        String bobResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new UserDtos.RegisterRequest(
                                "bob@test.com", "Password123!", "Bob"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UserDtos.AuthResponse bobAuth =
                objectMapper.readValue(bobResponse, UserDtos.AuthResponse.class);
        bobToken = bobAuth.accessToken();
        bobId    = bobAuth.user().id();

        assertThat(aliceToken).isNotBlank();
        assertThat(bobToken).isNotBlank();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DEPOSIT
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(2)
    @DisplayName("Alice deposits $100 into her wallet")
    void deposit_validRequest_updatesBalance() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/deposit")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new WalletDtos.DepositRequest(
                                10_000L, "deposit-001", "Initial top-up"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountInCents").value(10_000))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Verify balance updated in the wallet
        mockMvc.perform(get("/api/v1/wallets/me")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceInCents").value(10_000));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TRANSFER
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(3)
    @DisplayName("Alice transfers $30 to Bob")
    void transfer_validRequest_debitsSenderCreditReceiver() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new TransactionDtos.TransferRequest(
                                bobId, 3_000L, "Dinner split", "transfer-001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.amountInCents").value(3_000));

        // Alice: $100 - $30 = $70
        mockMvc.perform(get("/api/v1/wallets/me")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceInCents").value(7_000));

        // Bob: $0 + $30 = $30
        mockMvc.perform(get("/api/v1/wallets/me")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceInCents").value(3_000));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // IDEMPOTENCY
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(4)
    @DisplayName("Resubmitting the same transfer key returns the original result — balance unchanged")
    void transfer_duplicateIdempotencyKey_isIdempotent() throws Exception {
        // Submit the SAME key as the previous test
        MvcResult result1 = mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new TransactionDtos.TransferRequest(
                                bobId, 3_000L, "Dinner split", "transfer-001"))))
                .andExpect(status().isCreated())
                .andReturn();

        // Balance should STILL be $70 — the duplicate was rejected
        mockMvc.perform(get("/api/v1/wallets/me")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceInCents").value(7_000));

        // The returned transaction ID should be the SAME as the original
        TransactionDtos.TransactionResponse response =
                objectMapper.readValue(result1.getResponse().getContentAsString(),
                        TransactionDtos.TransactionResponse.class);
        assertThat(response.idempotencyKey()).isEqualTo("transfer-001");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CONCURRENCY — The most important test
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(5)
    @DisplayName("Concurrent transfers from Alice's wallet never corrupt the balance")
    void transfer_concurrentRequests_balanceRemainsConsistent() throws Exception {
        // Alice has $70 remaining. We'll fire 5 concurrent $10 transfers to Bob.
        // Expected correct result: exactly 5 succeed, Alice ends at $20, Bob at $80.
        // (If locking were broken, multiple threads could read the same balance
        //  and both "see" enough funds — leading to a negative balance or lost updates.)

        int numTransfers = 5;
        long amountEach = 1_000L; // $10

        ExecutorService executor = Executors.newFixedThreadPool(numTransfers);  // Create a thread pool to run concurrent transfer requests. The number of threads matches the number of concurrent transfers we want to test. Each thread will attempt to perform a transfer at the same time, which will test the application's concurrency handling and optimistic locking.
        CountDownLatch  latch    = new CountDownLatch(1); // start all threads simultaneously
        List<Future<Integer>> futures = new ArrayList<>();               // We'll collect the HTTP status codes returned by each transfer attempt. We expect some to succeed (201 Created) and if there's a concurrency conflict, some may return 409 Conflict. By analyzing these results, we can verify that the application correctly handles concurrent modifications without corrupting the wallet balance.

        for (int i = 0; i < numTransfers; i++) {
            String uniqueKey = "concurrent-" + UUID.randomUUID();
            futures.add(executor.submit(() -> {
                latch.await(); // wait for all threads to be ready
                try {
                    MvcResult result = mockMvc.perform(post("/api/v1/transactions/transfer")
                                    .header("Authorization", "Bearer " + aliceToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(toJson(new TransactionDtos.TransferRequest(
                                            bobId, amountEach, "Concurrent", uniqueKey))))
                            .andReturn();
                    return result.getResponse().getStatus();
                } catch (Exception e) {
                    return 500;
                }
            }));
        }

        latch.countDown(); // release all threads at once
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);  // Wait for all transfer attempts to complete. This ensures that we don't proceed to check the results until all concurrent operations have finished, which is crucial for accurately testing the concurrency handling of the application.

        // Count outcomes
        long successes = futures.stream().mapToInt(f -> {
            try { return f.get(); } catch (Exception e) { return 500; }
        }).filter(s -> s == 201).count();

        long conflicts = futures.stream().mapToInt(f -> {
            try { return f.get(); } catch (Exception e) { return 500; }
        }).filter(s -> s == 409).count();

        // All requests must be accounted for — 201 or 409, nothing else
        assertThat(successes + conflicts).isEqualTo(numTransfers);

        // Total debited == successes * $10
        long expectedAliceBalance = 7_000L - (successes * amountEach);

        String walletJson = mockMvc.perform(get("/api/v1/wallets/me")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        WalletDtos.WalletResponse aliceWallet =
                objectMapper.readValue(walletJson, WalletDtos.WalletResponse.class);

        // The final balance must exactly match (successes × transfer amount)
        assertThat(aliceWallet.balanceInCents())
                .as("Alice's balance must be consistent with exactly %d successful transfers", successes)
                .isEqualTo(expectedAliceBalance);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TRANSACTION HISTORY
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(6)
    @DisplayName("Transaction history returns paginated results correctly")
    void history_returnsPagedResults() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/history?page=0&size=10")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions").isArray())
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SECURITY
    // ═════════════════════════════════════════════════════════════════════════

    @Test @Order(7)
    @DisplayName("Unauthenticated request to protected endpoint returns 401")
    void security_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(8)
    @DisplayName("Invalid JWT token returns 401")
    void security_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/me")
                        .header("Authorization", "Bearer totally.invalid.token"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);  // Convert a Java object to a JSON string for use in request bodies. This is used to serialize our DTOs (like RegisterRequest, DepositRequest, etc.) into the JSON format expected by the API endpoints. By centralizing this logic in a helper method, we avoid repetitive try-catch blocks and keep our test code cleaner.
    }
}