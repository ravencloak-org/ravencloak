# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build the entire project (all modules)
./gradlew build

# Run the main application
./gradlew bootRun

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.keeplearning.auth.KosAuthBackendApplicationTests"

# Clean build
./gradlew clean build

# Build keycloak-spi module only (produces fat JAR)
./gradlew :keycloak-spi:shadowJar
```

## Environment Setup

1. Copy `.env.sample` to `.env` and configure database credentials
2. Start Keycloak: `docker compose up -d`
3. Keycloak admin UI available at http://localhost:8088 (admin/admin)

Required environment variables:
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` - PostgreSQL connection
- `KEYCLOAK_ISSUER_PREFIX` - Base URL for Keycloak realms (e.g., `https://keycloak.example.com/realms/`)
- `KEYCLOAK_SAAS_ISSUER_URI` - Full issuer URI for saas-admin realm
- `SAAS_ADMIN_CLIENT_SECRET` - OAuth2 client secret for admin-console

## Project Structure

This is a multi-module Gradle project:

| Module | Description |
|--------|-------------|
| `auth` (root) | Main Spring Boot authentication backend |
| `keycloak-spi` | Keycloak User Storage SPI for external user validation |

## Architecture

### Tech Stack
- Kotlin 2.2.21 / Java 21
- Spring Boot 4.0.1 with WebFlux (reactive)
- R2DBC for reactive PostgreSQL connectivity
- Flyway for database migrations (uses JDBC, not R2DBC)
- Spring Security with OAuth2/OIDC (Keycloak)

### Keycloak User Storage SPI (`keycloak-spi`)

A read-only User Storage Provider that validates users against the auth backend API.

**Key Classes:**
- `ExternalUser` - Data class for user data from REST API
- `ExternalUserAdapter` - Maps ExternalUser to Keycloak's UserModel (read-only)
- `ExternalUserStorageProvider` - Implements user lookup via HTTP client
- `ExternalUserStorageProviderFactory` - Factory registered with Keycloak SPI

**Dependencies:**
- `compileOnly` for Keycloak SPI (provided at runtime by Keycloak)
- Kotlin stdlib bundled and relocated via Shadow plugin
- Uses native Java 11+ `java.net.http.HttpClient` (no external HTTP libraries)

**API Endpoint Called:**
- `GET http://auth-backend:8080/api/users/{email}` - Returns user if exists (200) or not found (404)

**CI/CD (Drone CI):**
- `.drone.yml` builds the SPI JAR on push to `master`/`main`
- JAR is copied to `/opt/keycloak-providers/` shared volume
- Keycloak mounts this folder and loads the SPI on restart

### Multi-Tenant Authentication

The system supports multiple Keycloak realms via dynamic JWT issuer validation:

- `JwtIssuerReactiveAuthenticationManagerResolver` validates JWTs from any realm matching `KEYCLOAK_ISSUER_PREFIX`
- `JwtAuthorityConverter` extracts roles from Keycloak's `realm_access.roles` claim, prefixing with `ROLE_`
- Super admin access requires tokens from the `saas-admin` realm with `ROLE_SUPER_ADMIN` authority

### API Route Authorization

| Path Pattern | Authorization |
|--------------|---------------|
| `/api/public/**` | Public |
| `/auth/super/login`, `/oauth2/**` | Public (OAuth2 flow) |
| `/api/super/**` | Super admin only (via `SuperAdminAuthorizationManager`) |
| `/api/account/**` | `ACCOUNT_ADMIN` or `INSTITUTE_ADMIN` role |
| All other routes | Authenticated |

### Domain Model (see `V1__admin_schema.sql`)

Multi-tenant SaaS structure:
- **Account** → top-level tenant with dedicated Keycloak realm
- **Institute** → organizational unit within an account
- **App** → feature module that can be enabled per institute
- **User** → shadow record linked to Keycloak user, scoped to account
- **Role/RoleAssignment** → RBAC with account, institute, or app scope
