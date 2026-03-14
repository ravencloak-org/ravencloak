# ADR-001: Use Spring WebFlux with R2DBC for Reactive Stack

## Status

Accepted

## Context

The auth backend serves as a management control plane for multiple tenants. It needs to handle many concurrent requests from admin portals, SDK clients, and SCIM provisioning — all of which are I/O-bound (database queries, Keycloak API calls).

## Decision

Use **Spring WebFlux** (reactive, non-blocking) with **R2DBC** for database access instead of Spring MVC with JDBC.

- Controllers use Kotlin coroutines (`suspend fun`) for ergonomic async code
- Repositories return `Mono<T>` and `Flux<T>` (Project Reactor types)
- Keycloak admin client uses `WebClient` for non-blocking HTTP
- Flyway migrations still use JDBC (Flyway doesn't support R2DBC), running at startup before the reactive stack initializes

## Consequences

- **Positive**: High concurrency with minimal threads — suitable for a multi-tenant control plane
- **Positive**: Natural backpressure handling for streaming operations (e.g., user listing)
- **Negative**: R2DBC has fewer features than JPA/Hibernate (no lazy loading, no entity graphs)
- **Negative**: Debugging reactive stack traces is harder than imperative code
- **Mitigated**: Kotlin coroutines make async code read almost like synchronous code
