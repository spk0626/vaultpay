# VaultPay - Digital Wallet & Payment System

VaultPay is a secure, backend-focused digital wallet and payment platform for managing user accounts, balances, and peered-to-peered (P2P) money transfers. It is designed with a strong emphasis on reliability, transaction safety, and clean architecture principles.

## 🚀 Key Features & Architectural Decisions

* **Clean Architecture**: Decoupled layers (Web, Application/Service, Domain, Infrastructure) for high maintainability and testability.
* **Concurrency Management**: Utilizes **Optimistic Locking** to handle concurrent transactions and prevent race conditions.
* **Idempotency**: Uses **Redis** caching to ensure duplicate requests (e.g., from network retries) do not result in double processing.
* **Event-Driven Processing**: Leverages **RabbitMQ** to publish and consume domain events asynchronously. Includes a Dead-Letter Queue (DLQ) setup for robust failure handling.
* **Security**: Role and JWT-based authentication for secure REST API access.
* **Automated Quality**: Integrated unit and integration test suites, automated via a **GitHub Actions CI/CD pipeline**.

## 🛠️ Technologies Used

* **Language/Framework**: Java 21, Spring Boot 3
* **Databases/Data Stores**: PostgreSQL (Relational Data), Redis (Caching & Idempotency)
* **Message Broker**: RabbitMQ
* **Deployment/Tooling**: Docker, Docker Compose, GitHub Actions, Maven

## 📂 Project Structure

```text
src/main/java/com/vaultpay/
├── common/         # Cross-cutting concerns (security configuraton, global exception handling)
├── notification/   # RabbitMQ listeners and publishers
├── security/       # JWT and authentication filters
├── transaction/    # Transaction processing, deposit, withdrawal workflows
├── user/           # User registration and profile management
└── wallet/         # Wallet balance management and optimistic locking
```

## ⚙️ How to Run

### Prerequisites
* Docker and Docker Compose
* Java 21 installed locally (if running outside Docker)

### Running Infrastructure (PostgreSQL, Redis, RabbitMQ)
```bash
docker-compose up -d
```

### Running the Application
Using Maven wrapper:
```bash
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`.

## 🧪 Testing

Run all unit and integration tests using:
```bash
./mvnw test
```
