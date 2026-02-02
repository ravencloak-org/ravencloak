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

**CI/CD (Woodpecker CI):**
- `.woodpecker/` contains all pipeline configurations
- JAR is copied to `/opt/keycloak-providers/` shared volume on release
- Keycloak mounts this folder and loads the SPI on restart
- See [.woodpecker/README.md](.woodpecker/README.md) for detailed CI/CD documentation

## Woodpecker CI

### Pipeline Overview

| Pipeline | Trigger | Description |
|----------|---------|-------------|
| `auth.yml` | Push to `src/**` | Build auth backend |
| `keycloak-spi.yml` | Push to `keycloak-spi/**` | Build and test SPI |
| `auth-release.yml` | Tag `v*` | Release auth backend |
| `keycloak-spi-release.yml` | Tag `spi-v*` or manual | Release SPI |
| `release-all.yml` | Tag `release-v*` or manual | Release both modules |

### Manual Release Triggers (CLI)

```bash
# Keycloak SPI release (auto-increments version)
woodpecker-cli pipeline create dsjkeeplearning/kos-auth-backend --branch main --var DEPLOY_TO=keycloak-spi

# Combined release (auto-increments version)
woodpecker-cli pipeline create dsjkeeplearning/kos-auth-backend --branch main --var DEPLOY_TO=release-all
```

### Caching

- **Gradle dependencies**: Volume mount at `/var/lib/woodpecker/cache/gradle:/root/.gradle`
- **S3 build cache**: Cloudflare R2 via `com.github.burrunan.s3-build-cache` plugin
- **Docker cache**: Registry-based cache at `ghcr.io/dsjkeeplearning/kos-auth-backend:cache`

### Required Secrets

| Secret | Purpose |
|--------|---------|
| `github_token` | GitHub releases, README updates, Docker registry |
| `s3_build_cache_*` | Remote Gradle build cache |
| `db_*`, `keycloak_*` | Application deployment |

### Deployment Flow

- **Auth Backend**: Docker image pushed to ghcr.io, container restarted on EC2
- **Keycloak SPI**: JAR copied to `/opt/keycloak-providers/`, Keycloak restarted

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

### Domain Model

#### Core Entities (V1__admin_schema.sql)

Multi-tenant SaaS structure:
- **Account** → top-level tenant with dedicated Keycloak realm
- **Institute** → organizational unit within an account
- **App** → feature module that can be enabled per institute
- **User** → shadow record linked to Keycloak user, scoped to account
- **Role/RoleAssignment** → RBAC with account, institute, or app scope

#### User Search (V3 - ParadeDB BM25)

Enhanced user table with full-text search:
- BM25 index on: email, display_name, first_name, last_name, bio, job_title, department
- Search operators: `|||` (any term), `&&&` (all terms)
- Relevance scoring: `pdb.score(id)`

```sql
-- Example: Search users by name or role
SELECT id, email, display_name, pdb.score(id) as relevance
FROM users
WHERE (email, display_name, first_name, last_name) ||| 'john developer'
  AND account_id = :account_id
ORDER BY relevance DESC;
```

#### Keycloak Entity Mapping (V3)

Shadow tables for Keycloak entities (enables custom admin frontend):

| Table | Description |
|-------|-------------|
| `kc_realms` | Keycloak realms linked to accounts |
| `kc_clients` | OAuth2 clients per realm |
| `kc_client_scopes` | OAuth2 scopes |
| `kc_client_scope_mappings` | Client-to-scope assignments (DEFAULT/OPTIONAL) |
| `kc_groups` | Hierarchical groups (self-referencing via parent_id) |
| `kc_roles` | Realm and client roles |
| `kc_role_composites` | Composite role mappings |
| `kc_user_groups` | User-to-group assignments |
| `kc_user_roles` | Direct user-to-role assignments |
| `kc_group_roles` | Group-to-role assignments |
| `kc_identity_providers` | SSO federation providers (Google, SAML, OIDC) |
| `kc_sync_log` | Sync status tracking between DB and Keycloak |

### ParadeDB Setup (Required for V2+ migrations)

PostgreSQL must have ParadeDB extensions installed:

1. Install ParadeDB binaries: https://docs.paradedb.com/deploy/self-hosted/extension
2. **Postgres 17+**: No config changes needed, extensions load dynamically
3. **Postgres 16 and earlier**: Add to `postgresql.conf` and restart:
   ```ini
   shared_preload_libraries = 'pg_search'
   ```
4. Extensions created by migration: `pg_search` (BM25), `vector` (pgvector)
