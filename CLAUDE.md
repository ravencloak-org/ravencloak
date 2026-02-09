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
| `scim-common` | Shared SCIM 2.0 DTOs (ScimUserResource, ScimListResponse, etc.) |
| `forge` | Spring Boot Starter client SDK wrapping the SCIM API (`com.keeplearning.forge`) |
| `scim/` | SCIM 2.0 provisioning API docs (see [scim/README.md](scim/README.md)) |

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

**CI/CD:**
- GitHub Actions (`.github/workflows/`): Build, test, publish SDKs, release keycloak-spi
- Woodpecker CI (`.woodpecker/`): Auth backend builds, deployment to EC2
- See [.woodpecker/README.md](.woodpecker/README.md) for Woodpecker CI documentation

## GitHub Actions

| Workflow | Trigger | Description |
|----------|---------|-------------|
| `keycloak-spi.yml` | Push to `keycloak-spi/**`, tag `spi-v*`, manual | Build, test & release SPI JAR |
| `auth-sdk-publish.yml` | Push to `forge/**`/`scim-common/**`, tag `sdk-v*`, manual | Build, test & publish Forge SDK |
| `deploy-docs.yml` | Push to `docs/**`/`mkdocs.yml`, release published, manual | Build & deploy MkDocs to GitHub Pages |

### Manual Release (CLI)

```bash
# Keycloak SPI release
gh workflow run keycloak-spi.yml -f version=1.0.1

# Forge SDK release
gh workflow run auth-sdk-publish.yml -f version=0.2.0
```

### GitHub Actions Secrets

| Secret | Purpose |
|--------|---------|
| `GITHUB_TOKEN` | Auto-provided, GitHub Packages & Releases |
| `S3_BUILD_CACHE_BUCKET` | Remote Gradle build cache (Cloudflare R2) |
| `S3_BUILD_CACHE_REGION` | R2 region (`auto`) |
| `S3_BUILD_CACHE_ACCESS_KEY_ID` | R2 access key |
| `S3_BUILD_CACHE_SECRET_KEY` | R2 secret key |
| `S3_BUILD_CACHE_ENDPOINT` | R2 endpoint URL |

## Woodpecker CI

### Pipeline Overview

| Pipeline | Trigger | Description |
|----------|---------|-------------|
| `auth.yml` | Push to `src/**` | Build auth backend |
| `auth-release.yml` | Tag `v*` | Release auth backend |
| `keycloak-spi-release.yml` | Manual (`DEPLOY_TO=keycloak-spi`) | Deploy SPI from GitHub Release |
| `release-all.yml` | Tag `release-v*` or manual | Release both modules |

### Manual Deploy Triggers (CLI)

```bash
# Deploy latest keycloak-spi release to Keycloak
woodpecker-cli pipeline create dsjkeeplearning/kos-auth-backend --branch main --var DEPLOY_TO=keycloak-spi

# Combined release (auto-increments version)
woodpecker-cli pipeline create dsjkeeplearning/kos-auth-backend --branch main --var DEPLOY_TO=release-all
```

### Caching

- **Gradle dependencies**: Volume mount at `/var/lib/woodpecker/cache/gradle:/root/.gradle`
- **S3 build cache**: Cloudflare R2 via `com.github.burrunan.s3-build-cache` plugin
- **Docker cache**: Registry-based cache at `ghcr.io/dsjkeeplearning/kos-auth-backend:cache`

### Woodpecker Secrets

| Secret | Purpose |
|--------|---------|
| `github_token` | GitHub releases, README updates, Docker registry |
| `s3_build_cache_*` | Remote Gradle build cache |
| `db_*`, `keycloak_*` | Application deployment |

### Deployment Flow

- **Auth Backend**: Docker image pushed to ghcr.io, container restarted on EC2
- **Keycloak SPI**: GH Action builds & creates GitHub Release → Woodpecker downloads JAR & deploys to `/opt/keycloak-providers/`
- **Forge SDK**: GH Action publishes to GitHub Packages (Maven)

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
| `/api/scim/v2/**` | Authenticated (OAuth2 JWT) |
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
| `entity_action_logs` | Audit trail for entity changes (V6) |

#### Paired Clients (V7)

Full-stack applications can create paired frontend/backend clients:
- `kc_clients.paired_client_id` links frontend → backend client
- Frontend client: public, `-web` suffix, configured redirect URIs
- Backend client: confidential, `-backend` suffix, service accounts enabled
- Query paired client: `KcClientRepository.findByPairedClientId()`

#### Audit Trail (V6)

Entity action logging with before/after state JSONB:

| Column | Description |
|--------|-------------|
| `entity_type` | CLIENT, ROLE, GROUP, IDP |
| `action` | CREATE, UPDATE, DELETE |
| `before_state` | JSONB snapshot before change |
| `after_state` | JSONB snapshot after change |
| `actor_id` | Keycloak user ID from JWT `sub` claim |
| `actor_email` | Email from JWT `email` claim |

**Key Services:**
- `AuditService` - Logs entity actions with JWT actor info
- `AuditQueryService` - Query logs by realm, entity, actor
- `RevertService` - Restore entities to previous state (Keycloak + DB)

**REST Endpoints:**
- `GET /api/super/realms/{realm}/audit` - List audit logs with filters
- `GET /api/super/realms/{realm}/audit/{id}` - Get single log entry
- `POST /api/super/realms/{realm}/audit/{id}/revert` - Revert to before state
- `GET /api/super/my-actions` - Current user's actions across realms

## Vue 3 Admin Portal (`web/`)

### Tech Stack
- Vue 3.5 + TypeScript + Vite
- PrimeVue 4 component library (Aura theme)
- Vue Router with file-based routing (`unplugin-vue-router`)
- Pinia for state management
- Axios for HTTP client

### Build Commands

```bash
cd web
npm install      # Install dependencies
npm run dev      # Start dev server (http://localhost:5173)
npm run build    # Production build with type checking
npm run lint     # ESLint + Prettier
```

### Project Structure

```
web/
├── src/
│   ├── api/           # API clients (axios-based)
│   │   ├── client.ts  # Base axios instance with auth interceptors
│   │   ├── clients.ts # Client CRUD + applications
│   │   ├── audit.ts   # Audit trail queries
│   │   └── ...
│   ├── components/    # Reusable Vue components
│   │   ├── ClientCard.vue      # Client display card
│   │   ├── ClientList.vue      # Card grid for clients
│   │   ├── AuditTimeline.vue   # Timeline view for audit logs
│   │   └── AuditDiffViewer.vue # Before/after diff display
│   ├── pages/         # File-based routing
│   │   └── realms/[name]/      # Dynamic realm routes
│   ├── stores/        # Pinia stores (auth, etc.)
│   ├── types/         # TypeScript interfaces
│   └── utils/         # Utility functions
│       └── urlTransform.ts  # URL auto-formatting
```

### URL Auto-Conversion Utilities

`web/src/utils/urlTransform.ts` provides automatic URL formatting for Keycloak:

```typescript
// Redirect URIs: adds scheme and wildcard
transformToRedirectUri('localhost:5173')  // → 'http://localhost:5173/*'
transformToRedirectUri('example.com')     // → 'https://example.com/*'
transformToRedirectUri('example.com/app') // → 'https://example.com/app/*'

// Web Origins: adds scheme, no path
transformToWebOrigin('localhost:5173')    // → 'http://localhost:5173'
transformToWebOrigin('example.com')       // → 'https://example.com'
```

### API Types

Key TypeScript interfaces in `web/src/types/index.ts`:

```typescript
// Application creation (paired clients)
type ApplicationType = 'FRONTEND_ONLY' | 'BACKEND_ONLY' | 'FULL_STACK'

interface CreateApplicationRequest {
  applicationName: string
  applicationType: ApplicationType
  // URLs applied to frontend client
  rootUrl?: string
  redirectUris?: string[]
  webOrigins?: string[]
}

interface ApplicationResponse {
  frontendClient?: ClientDetailResponse
  backendClient?: ClientDetailResponse
}
```

## SCIM 2.0 Provisioning API

Header-based API versioning via Spring Framework 7 (`API-Version: 1.0`). See [scim/README.md](scim/README.md) for full documentation.

**Key packages:** `com.keeplearning.auth.scim`

| Class | Purpose |
|-------|---------|
| `ScimUserController` | SCIM User CRUD (`/api/scim/v2/Users`) |
| `ScimDiscoveryController` | ServiceProviderConfig, Schemas, ResourceTypes |
| `ScimUserService` | Business logic wrapping UserRepository |
| `ScimUserMapper` | User entity ↔ SCIM resource mapping |
| `ScimFilterTranslator` | SCIM filter → R2DBC SQL translation |
| `ScimExceptionHandler` | RFC 7644 §3.12 error responses |

### ParadeDB Setup (Required for V2+ migrations)

PostgreSQL must have ParadeDB extensions installed:

1. Install ParadeDB binaries: https://docs.paradedb.com/deploy/self-hosted/extension
2. **Postgres 17+**: No config changes needed, extensions load dynamically
3. **Postgres 16 and earlier**: Add to `postgresql.conf` and restart:
   ```ini
   shared_preload_libraries = 'pg_search'
   ```
4. Extensions created by migration: `pg_search` (BM25), `vector` (pgvector)
