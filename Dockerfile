# ══════════════════════════════════════════════════════════════════════════════
# Multi-Stage Build
#
# Stage 1 (builder):  JDK image — compiles the code, produces a JAR file
# Stage 2 (runtime):  Lean JRE image — runs the JAR only, no compiler included
#
# Result: The final image is ~200MB instead of ~600MB because we discard the compiler and Maven cache before shipping.
# ══════════════════════════════════════════════════════════════════════════════

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper files FIRST (before source code).
# Docker caches each layer. If pom.xml doesn't change, the dependency download layer is reused on the next build — saving 30–60 seconds every time.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download all Maven dependencies (this layer is cached if pom.xml is unchanged)
RUN ./mvnw dependency:go-offline -B

# Now copy source code and build the JAR
COPY src ./src
RUN ./mvnw package -DskipTests -B

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Security: Run as a non-root user — should never run JVM apps as root in production
RUN addgroup -S vaultpay && adduser -S vaultpay -G vaultpay
USER vaultpay

# Copy ONLY the compiled JAR from Stage 1 — the Maven cache and JDK are left behind
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]