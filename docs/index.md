# KOS Auth Backend

A multi-tenant authentication backend built with **Spring Boot 4** and **Kotlin**, featuring a **Keycloak User Storage SPI** for federated user validation via REST API.

## Why KOS Auth?

Managing identity across multiple tenants, services, and applications is hard. KOS Auth provides:

- **Centralized multi-tenant identity** — one backend manages users across multiple Keycloak realms, each representing a tenant
- **Standards-based provisioning** — SCIM 2.0 API ([RFC 7644](https://datatracker.ietf.org/doc/html/rfc7644)) for interoperable user management
- **SDK-first integration** — the [Forge SDK](sdk/installation.md) lets any Spring Boot service manage users with a single dependency
- **Audit and rollback** — every entity change is logged with before/after state and can be reverted

## Overview

| Module | Description |
|--------|-------------|
| **auth** (root) | Main Spring Boot authentication backend |
| **keycloak-spi** | Keycloak User Storage SPI for external user validation |
| **scim-common** | Shared SCIM 2.0 DTOs |
| **forge** | Spring Boot Starter client SDK wrapping the SCIM API |

## Key Features

- **Multi-tenant** — supports multiple Keycloak realms with dynamic JWT issuer validation
- **Reactive** — Spring WebFlux with R2DBC for non-blocking database access
- **SCIM 2.0** — standards-based user provisioning API
- **Full-text search** — ParadeDB BM25 indexing on user fields
- **Audit trail** — entity action logging with before/after state and revert capability
- **Paired clients** — full-stack applications can create linked frontend/backend OAuth2 clients

!!! tip "Integrating with KOS Auth?"
    If you're building a service that needs to manage users, head to the **[SDK Integration Guide](sdk/installation.md)** to get started with the Forge SDK. You'll have user CRUD working in minutes.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.2 / Java 21 |
| Framework | Spring Boot 4.0 with WebFlux |
| Database | PostgreSQL with R2DBC |
| Migrations | Flyway (JDBC) |
| Auth | OAuth2/OIDC via Keycloak |
| Search | ParadeDB pg_search (BM25) |
| CI/CD | Woodpecker CI + GitHub Actions |

## Quick Start

```bash
# Clone and configure
cp .env.sample .env
# Edit .env with your database credentials

# Start infrastructure
docker compose up -d

# Build and run
./gradlew bootRun
```

See [Getting Started](getting-started.md) for the full setup guide.
