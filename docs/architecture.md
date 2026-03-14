# Architecture

This document explains the high-level architecture of the KOS Auth Backend, how the major components interact, and the design decisions that shape the system.

## System Overview

KOS Auth is a **multi-tenant authentication and identity management backend** that acts as a control plane for Keycloak. It provides a management API for tenants, users, clients, roles, groups, and identity providers, while Keycloak handles the runtime authentication (OAuth2/OIDC token issuance).

```mermaid
graph TB
    subgraph "External Clients"
        WebAdmin["Vue 3 Admin Portal<br/>(web/)"]
        SDK["Forge SDK<br/>(Spring Boot services)"]
        ExtApps["External Applications"]
    end

    subgraph "Auth Backend (Spring Boot 4)"
        API["REST API Layer<br/>(WebFlux Controllers)"]
        Security["Security Layer<br/>(OAuth2 JWT validation)"]
        Services["Service Layer<br/>(Business Logic)"]
        Repos["Repository Layer<br/>(R2DBC)"]
        SCIM["SCIM 2.0 API<br/>(User Provisioning)"]
        Audit["Audit Trail<br/>(Entity Change Tracking)"]
        KCSync["Keycloak Sync<br/>(Bidirectional)"]
    end

    subgraph "Infrastructure"
        PG["PostgreSQL<br/>(ParadeDB + pgvector)"]
        KC["Keycloak<br/>(Identity Provider)"]
    end

    WebAdmin -->|"JWT Bearer"| API
    SDK -->|"OAuth2 Client Credentials"| SCIM
    ExtApps -->|"JWT Bearer"| API

    API --> Security
    Security --> Services
    SCIM --> Services
    Services --> Repos
    Services --> Audit
    Services --> KCSync

    Repos --> PG
    KCSync -->|"Admin REST API"| KC

    KC -->|"User Storage SPI"| API
```

## Multi-Tenant Authentication

The system supports multiple Keycloak realms, each representing a tenant. JWT tokens from any realm are accepted, with the issuer dynamically resolved at runtime.

```mermaid
sequenceDiagram
    participant Client
    participant AuthBackend
    participant Keycloak

    Client->>Keycloak: Login (realm-specific)
    Keycloak-->>Client: JWT (issuer = realm URL)

    Client->>AuthBackend: API Request + JWT
    AuthBackend->>AuthBackend: Extract issuer from JWT
    AuthBackend->>AuthBackend: Validate issuer matches KEYCLOAK_ISSUER_PREFIX
    AuthBackend->>Keycloak: Fetch JWK Set for realm
    AuthBackend->>AuthBackend: Verify JWT signature + claims
    AuthBackend->>AuthBackend: Extract roles from realm_access.roles
    AuthBackend-->>Client: Response
```

### Key Security Components

| Component | File | Responsibility |
|-----------|------|----------------|
| `SecurityConfig` | `config/SecurityConfig.kt` | Defines route authorization rules and filter chain |
| `JwtAuthorityConverter` | `config/JwtAuthorityConverter.kt` | Extracts `realm_access.roles` from JWT, prefixes with `ROLE_` |
| `SuperAdminAuthorizationManager` | `security/SuperAdminAuthorizationManager.kt` | Validates super admin access via saas-admin realm |
| `SecurityGuards` | `security/SecurityGuards.kt` | Utility functions for realm/role checks |

### API Route Authorization

| Path Pattern | Authorization |
|--------------|---------------|
| `/api/public/**` | Public |
| `/auth/super/login`, `/oauth2/**` | Public (OAuth2 flow) |
| `/api/super/**` | Super admin only (`SuperAdminAuthorizationManager`) |
| `/api/account/**` | `ACCOUNT_ADMIN` or `INSTITUTE_ADMIN` role |
| `/api/scim/v2/**` | Authenticated (OAuth2 JWT) |
| All other routes | Authenticated |

## Request Lifecycle

A typical authenticated API request flows through these layers:

```mermaid
graph LR
    A["HTTP Request"] --> B["Spring WebFlux<br/>Netty Server"]
    B --> C["SecurityWebFilterChain<br/>(JWT validation)"]
    C --> D["JwtAuthorityConverter<br/>(role extraction)"]
    D --> E["Authorization Manager<br/>(route-level checks)"]
    E --> F["Controller<br/>(DTO mapping)"]
    F --> G["Service<br/>(business logic)"]
    G --> H["Repository<br/>(R2DBC queries)"]
    H --> I["PostgreSQL"]
    G --> J["Keycloak Admin API<br/>(when sync needed)"]
    G --> K["AuditService<br/>(change logging)"]
```

## Module Architecture

The project is organized as a multi-module Gradle build:

```mermaid
graph TB
    subgraph "Multi-Module Gradle Project"
        Auth["auth (root)<br/>Spring Boot 4 Backend"]
        SPI["keycloak-spi<br/>User Storage Provider"]
        ScimCommon["scim-common<br/>Shared SCIM DTOs"]
        Forge["forge<br/>Spring Boot Starter SDK"]
    end

    subgraph "Deployable Artifacts"
        Docker["Docker Image<br/>(ghcr.io)"]
        SPIJAR["Fat JAR<br/>(Shadow)"]
        Maven["Maven Package<br/>(GitHub Packages)"]
    end

    Auth --> Docker
    SPI --> SPIJAR
    Forge --> Maven
    ScimCommon --> Maven

    Forge -.->|"depends on"| ScimCommon
```

| Module | Technology | Purpose | Artifact |
|--------|-----------|---------|----------|
| `auth` (root) | Kotlin, Spring Boot 4, WebFlux, R2DBC | Main backend service | Docker image |
| `keycloak-spi` | Kotlin, Keycloak SPI | Read-only user validation provider | Fat JAR (Shadow) |
| `scim-common` | Kotlin | Shared SCIM 2.0 DTOs and utilities | Maven package |
| `forge` | Kotlin, Spring Boot Starter | Client SDK for SCIM API consumption | Maven package |

## Keycloak Integration

The system integrates with Keycloak in two directions:

### 1. Auth Backend → Keycloak (Admin API)

The `KeycloakAdminClient` uses Keycloak's REST Admin API to manage realms, clients, roles, groups, users, and identity providers. Changes made through the Auth Backend API are pushed to Keycloak.

### 2. Keycloak → Auth Backend (User Storage SPI)

The `keycloak-spi` module deploys a read-only User Storage Provider into Keycloak. During login, Keycloak calls the Auth Backend to validate whether a user exists:

```mermaid
sequenceDiagram
    participant User
    participant Keycloak
    participant SPI as "User Storage SPI"
    participant Backend as "Auth Backend"

    User->>Keycloak: Login (email + password)
    Keycloak->>SPI: getUserByEmail(email)
    SPI->>Backend: GET /api/users/{email}
    alt User exists (200)
        Backend-->>SPI: User data
        SPI-->>Keycloak: ExternalUserAdapter
        Keycloak->>Keycloak: Validate password
        Keycloak-->>User: JWT Token
    else User not found (404)
        Backend-->>SPI: Not found
        SPI-->>Keycloak: null
        Keycloak->>Keycloak: Try next provider
    end
```

### 3. Bidirectional Sync

The `KeycloakSyncService` synchronizes state between the Auth Backend's database and Keycloak. On startup (when enabled), it pulls realms, clients, roles, groups, and users from Keycloak into shadow tables, enabling the admin UI to render Keycloak state without direct Keycloak API calls.

## SCIM 2.0 Provisioning

The SCIM API enables external services to manage users via a standards-based protocol:

```mermaid
graph LR
    subgraph "Consumer Service"
        ForgeSDK["Forge SDK"]
        StartupSync["Startup Sync"]
    end

    subgraph "Auth Backend"
        ScimAPI["SCIM 2.0 API"]
        ScimService["ScimUserService"]
        UserRepo["UserRepository"]
    end

    ForgeSDK -->|"CRUD via SCIM"| ScimAPI
    StartupSync -->|"Checksum + Bulk"| ScimAPI
    ScimAPI --> ScimService
    ScimService --> UserRepo
    UserRepo --> PG["PostgreSQL"]
```

The Forge SDK provides automatic startup sync: it checksums local users against the Auth Backend, and if there's a drift, it performs a bulk sync to reconcile differences.

## Data Flow Patterns

### Entity CRUD with Audit

All entity modifications (clients, roles, groups, IDPs) follow this pattern:

1. Controller receives request, validates DTO
2. Service performs the operation on the database
3. Service calls `KeycloakAdminClient` to sync the change to Keycloak
4. `AuditService` logs the change with before/after JSONB snapshots
5. Response returned to client

### Paired Client Creation

Full-stack applications create linked frontend/backend clients:

1. `POST /api/super/realms/{realm}/applications` with `applicationType: FULL_STACK`
2. Backend creates a public frontend client (`-web` suffix) with redirect URIs
3. Backend creates a confidential backend client (`-backend` suffix) with service accounts
4. Links them via `kc_clients.paired_client_id`
5. Both clients are created in Keycloak
6. Integration snippets are generated for the developer

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.2 |
| JVM | Java | 21 |
| Framework | Spring Boot | 4.0 |
| Reactive | Spring WebFlux | (Spring Framework 7) |
| Database Access | R2DBC | (reactive, non-blocking) |
| Migrations | Flyway | (JDBC, not R2DBC) |
| Database | PostgreSQL | 17+ recommended |
| Search | ParadeDB pg_search | BM25 full-text |
| Vectors | pgvector | Semantic search ready |
| Auth | Keycloak | 26.x |
| API Docs | SpringDoc OpenAPI | 3.0 |
| Build | Gradle | Kotlin DSL |
| Observability | Micrometer Tracing | (trace_id, span_id) |
