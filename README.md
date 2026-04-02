# Auth Backend

[![Build](https://github.com/ravencloak-org/ravencloak/actions/workflows/build.yml/badge.svg)](https://github.com/ravencloak-org/ravencloak/actions/workflows/build.yml)
[![codecov](https://codecov.io/gh/ravencloak-org/ravencloak/graph/badge.svg)](https://codecov.io/gh/ravencloak-org/ravencloak)
[![status-badge](https://drone.keeplearningos.com/api/badges/1/status.svg?events=push%2Ctag%2Crelease%2Cpull_request%2Cpull_request_closed%2Cpull_request_metadata%2Cdeployment)](https://drone.keeplearningos.com/repos/1)
![Auth](https://img.shields.io/badge/auth--blue)
![Keycloak SPI](https://img.shields.io/badge/keycloak--spi--green)

Multi-tenant authentication and authorization platform built on Keycloak, Spring Boot, and Vue 3.

## Architecture

```
                      ┌──────────────────────────────────────────────┐
                      │               Docker Compose                 │
                      │                                              │
  Browser ───────┬───►│  auth-frontend  (Vue 3 + Nginx)     :8090   │
                 │    │       │                                       │
                 │    │       ▼                                       │
                 └───►│  auth-backend   (Spring Boot)       :8080   │
                      │       │    │          │                       │
                      │       │    │ gRPC     │                       │
                      │       │    ▼ :9090    ▼                       │
                      │       │           postgres (ParadeDB)  :5432  │
                      │       ▼                                       │
                      │  keycloak (+ SPI JAR)                :8088   │
                      │                                              │
                      │  otel-collector  (optional)    :4317/:4318   │
                      └──────────────────────────────────────────────┘
```

| Module | Description |
|--------|-------------|
| `auth` (root) | Spring Boot backend with WebFlux, R2DBC, gRPC user provisioning |
| `keycloak-spi` | Read-only User Storage SPI for Keycloak — validates users against the auth backend |
| `scim-common` | Shared SCIM 2.0 DTOs |
| `web/` | Vue 3 + PrimeVue admin portal |

## Prerequisites

- **Docker** and **Docker Compose** v2+
- **Java 21** (for building from source)
- **Bun** or **Node.js 18+** (for frontend development)

## Quick Start (Development)

### 1. Clone and configure

```bash
git clone <repo-url> && cd auth
cp .env.sample .env
# Edit .env if you need to change ports or credentials
```

### 2. Start all services

```bash
# Build from source and start everything
docker compose up -d --build
```

This starts:

| Service | Port | Description |
|---------|------|-------------|
| **PostgreSQL** (ParadeDB) | `5432` | Database with BM25 full-text search extensions |
| **Keycloak** | `8088` | Admin UI at http://localhost:8088 (admin/admin) |
| **Auth Backend** | `8080` | Spring Boot API |
| **Auth Backend gRPC** | `9090` | gRPC user provisioning service |
| **Auth Frontend** | `8090` | Vue 3 admin portal |

### 3. (Optional) Enable OpenTelemetry

```bash
docker compose --profile otel up -d
```

This additionally starts the **OpenTelemetry Collector** which exports traces, metrics, and logs to SigNoz (or any OTLP-compatible backend). Configure `SIGNOZ_ENDPOINT` and `SIGNOZ_ACCESS_TOKEN` in `.env`.

### 4. Verify

```bash
# Check all services are healthy
docker compose ps

# Backend health
curl http://localhost:8080/actuator/health

# Keycloak admin console
open http://localhost:8088
```

## Running Without Docker (Backend Dev)

If you prefer running the Spring Boot app directly:

```bash
# Start only infrastructure
docker compose up -d postgres keycloak

# Run the backend with Gradle
./gradlew bootRun

# In another terminal, run the frontend
cd web && npm install && npm run dev
```

## Production Deployment

### 1. Configure environment

```bash
cp .env.sample .env
```

Update `.env` with production values:

```env
# Point to your Keycloak domain
KEYCLOAK_ISSUER_PREFIX=https://auth.yourdomain.com/realms/
KEYCLOAK_SAAS_ISSUER_URI=https://auth.yourdomain.com/realms/saas-admin

# Use pre-built images instead of building locally
AUTH_BACKEND_IMAGE=ghcr.io/dsjkeeplearning/kos-auth-backend:1.0.0
AUTH_FRONTEND_IMAGE=ghcr.io/dsjkeeplearning/kos-auth-backend-web:1.0.0

# Frontend build-time variables (baked into the image)
VITE_API_BASE_URL=https://api.yourdomain.com
VITE_KEYCLOAK_URL=https://auth.yourdomain.com
VITE_KEYCLOAK_REALM=saas-admin
VITE_KEYCLOAK_CLIENT_ID=kos-admin-web

# Strong passwords
DB_PASSWORD=<strong-password>
KEYCLOAK_ADMIN_PASSWORD=<strong-password>

# Enable telemetry
OTEL_SDK_DISABLED=false
```

### 2. Deploy

```bash
# Pull pre-built images and start
docker compose pull
docker compose --profile otel up -d
```

### 3. Reverse proxy

Place a reverse proxy (Nginx, Caddy, or Cloudflare Tunnel) in front:

| Domain | Target |
|--------|--------|
| `auth.yourdomain.com` | `localhost:8088` (Keycloak) |
| `api.yourdomain.com` | `localhost:8080` (Backend) |
| `admin.yourdomain.com` | `localhost:8090` (Frontend) |

## Keycloak User Storage SPI

The `keycloak-spi` module is a read-only [User Storage SPI](https://www.keycloak.org/docs/latest/server_development/#_user-storage-spi) that allows Keycloak to validate users against the auth backend's database. When a user tries to log in, Keycloak calls the SPI, which checks `GET http://auth-backend:8080/api/users/{email}`.

### How the SPI JAR Gets Into Keycloak

The SPI is built as a fat JAR (shadow JAR with relocated Kotlin stdlib) and mounted into Keycloak's `/opt/keycloak/providers/` directory. Here's the full pipeline:

```
  Developer pushes code
       │
       ▼
  CI builds the shadow JAR ──────────────────────────────────────────
  ./gradlew :keycloak-spi:shadowJar                                  │
       │                                                             │
       ▼                                                             │
  GitHub Actions creates a Release (tag: spi-vX.Y.Z)                │
  with the JAR attached as a release asset                           │
       │                                                             │
       ▼                                                             │
  Deployment pipeline (Woodpecker CI) downloads the JAR              │
  from the GitHub Release                                            │
       │                                                             │
       ▼                                                             │
  JAR is copied to the Keycloak providers volume                     │
  (/opt/keycloak/providers/)                                         │
       │                                                             │
       ▼                                                             │
  Keycloak restarts and auto-discovers the new provider ─────────────
```

### CI Workflows

**GitHub Actions** (`.github/workflows/keycloak-spi.yml`):
- Triggered by tag `spi-v*` or manual dispatch
- Builds the shadow JAR: `./gradlew :keycloak-spi:shadowJar -Pversion=X.Y.Z`
- Creates a GitHub Release with the JAR as an artifact

**Woodpecker CI** (`.woodpecker/keycloak-spi-release.yml`):
- Triggered manually with `DEPLOY_TO=keycloak-spi`
- Downloads the latest `spi-v*` release JAR from GitHub
- Copies it to `/opt/keycloak-providers/` on the host
- Restarts Keycloak to load the new provider

**Combined Release** (`.woodpecker/release-all.yml`):
- Triggered by tag `release-v*` or manual dispatch
- Builds both the auth backend and SPI JAR in one pipeline
- Deploys the SPI JAR and restarts Keycloak automatically

### Building & Deploying the SPI Locally

```bash
# 1. Build the shadow JAR
./gradlew :keycloak-spi:shadowJar

# 2. Copy into the running Keycloak container's providers volume
docker cp keycloak-spi/build/libs/keycloak-user-storage-spi-*.jar \
  keycloak:/opt/keycloak/providers/

# 3. Restart Keycloak to load the provider
docker compose restart keycloak
```

After restart, the provider appears in **Keycloak Admin > Realm Settings > User Federation** as an available provider.

### Manual Release (CLI)

```bash
# Trigger a GitHub Actions release
gh workflow run keycloak-spi.yml -f version=1.0.1

# Or trigger Woodpecker to deploy the latest release to the server
woodpecker-cli pipeline create dsjkeeplearning/kos-auth-backend \
  --branch main --var DEPLOY_TO=keycloak-spi
```

## Build Commands

```bash
# Build everything
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.keeplearning.auth.SomeTestClass"

# Build only the SPI shadow JAR
./gradlew :keycloak-spi:shadowJar

# Build the backend Docker image with Jib
./gradlew jib -PjibTag=1.0.0

# Frontend dev server
cd web && npm run dev

# Frontend production build
cd web && npm run build
```

## CI/CD

This project uses both **GitHub Actions** and **Woodpecker CI**.

### Pipelines

| Pipeline | Trigger | Description |
|----------|---------|-------------|
| `build.yml` (GHA) | Push/PR to main | Compile, test, coverage |
| `build-backend.yml` (GHA) | Push to main, tag `v*` | Build & push backend Docker image |
| `build-frontend.yml` (GHA) | Push to main, tag `v*` | Build & push frontend Docker image |
| `keycloak-spi.yml` (GHA) | Tag `spi-v*`, manual | Build SPI, create GitHub Release |
| `auth-release.yml` (WP) | Tag `v*` | Auth backend release + deploy |
| `keycloak-spi-release.yml` (WP) | Manual | Deploy SPI JAR to Keycloak |
| `release-all.yml` (WP) | Tag `release-v*`, manual | Full release: backend + SPI + frontend |
| `deploy-docs.yml` (GHA) | Push to docs/ | MkDocs to GitHub Pages |

### Release Workflow

**Tag-Based (Automatic):**
```bash
# Auth backend release
git tag v1.0.0 && git push origin v1.0.0

# Keycloak SPI release
git tag spi-v1.0.0 && git push origin spi-v1.0.0

# Combined release (both modules)
git tag release-v1.0.0 && git push origin release-v1.0.0
```

**CLI-Based (Manual with Auto-Increment):**
```bash
# Deploy keycloak SPI from latest GitHub Release
woodpecker-cli pipeline create dsjkeeplearning/kos-auth-backend \
  --branch main --var DEPLOY_TO=keycloak-spi

# Combined release (auto-increments patch version)
woodpecker-cli pipeline create dsjkeeplearning/kos-auth-backend \
  --branch main --var DEPLOY_TO=release-all
```

## Environment Variables Reference

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `kos-auth` | Database name |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |

### Keycloak

| Variable | Default | Description |
|----------|---------|-------------|
| `KEYCLOAK_PORT` | `8088` | Keycloak HTTP port |
| `KEYCLOAK_ADMIN` | `admin` | Admin username |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin` | Admin password |
| `KEYCLOAK_BASE_URL` | `http://keycloak:8080` | Internal Keycloak URL (container-to-container) |
| `KEYCLOAK_ISSUER_PREFIX` | `http://localhost:8088/realms/` | JWT issuer prefix for multi-tenant auth |
| `KEYCLOAK_SAAS_ISSUER_URI` | `http://localhost:8088/realms/saas-admin` | Issuer URI for saas-admin realm |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `admin-console` | OAuth2 client ID |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | | OAuth2 client secret |
| `SAAS_ADMIN_CLIENT_SECRET` | | SaaS admin client secret |

### Backend

| Variable | Default | Description |
|----------|---------|-------------|
| `AUTH_BACKEND_PORT` | `8080` | HTTP port |
| `GRPC_PORT` | `9090` | gRPC port |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring profile |
| `AUTH_BACKEND_IMAGE` | `ghcr.io/.../kos-auth-backend:latest` | Docker image override |
| `OTEL_SDK_DISABLED` | `true` | Disable OpenTelemetry |

### Frontend

| Variable | Default | Description |
|----------|---------|-------------|
| `AUTH_FRONTEND_PORT` | `8090` | HTTP port |
| `AUTH_FRONTEND_IMAGE` | `ghcr.io/.../kos-auth-backend-web:latest` | Docker image override |
| `VITE_API_BASE_URL` | `http://localhost:8080` | Backend API URL (baked at build time) |
| `VITE_KEYCLOAK_URL` | `http://localhost:8088` | Keycloak URL (baked at build time) |
| `VITE_KEYCLOAK_REALM` | `saas-admin` | Keycloak realm |
| `VITE_KEYCLOAK_CLIENT_ID` | `kos-admin-web` | Keycloak client ID |

### OpenTelemetry

| Variable | Default | Description |
|----------|---------|-------------|
| `SIGNOZ_ENDPOINT` | | SigNoz/OTLP ingest endpoint |
| `SIGNOZ_ACCESS_TOKEN` | | SigNoz ingestion key |

## Speeding Up Gradle Builds

### Remote Build Cache (Optional)

Share build cache across your team and CI using S3 or S3-compatible storage (MinIO, Cloudflare R2, Backblaze B2).

Add to your `.env`:
```env
S3_BUILD_CACHE_BUCKET=your-gradle-cache-bucket
S3_BUILD_CACHE_REGION=us-east-1
AWS_ACCESS_KEY_ID=your_access_key
AWS_SECRET_ACCESS_KEY=your_secret_key
# For MinIO/R2/etc:
# S3_BUILD_CACHE_ENDPOINT=https://minio.example.com:9000
```

Source the env before building:
```bash
export $(cat .env | xargs)
./gradlew build
```

### Verifying Cache Hits

```bash
./gradlew build --info | grep -i "cache"
# You should see: Task :compileKotlin FROM-CACHE
```

## Project Structure

```
auth/
├── src/                       # Spring Boot backend (Kotlin)
│   ├── main/
│   │   ├── kotlin/            # Application code
│   │   ├── proto/             # gRPC proto definitions
│   │   └── resources/
│   │       └── db/migration/  # Flyway SQL migrations
│   └── test/
├── keycloak-spi/              # Keycloak User Storage SPI module
├── scim-common/               # Shared SCIM 2.0 DTOs
├── web/                       # Vue 3 admin portal
├── docker/                    # OTel collector config
├── .github/workflows/         # GitHub Actions CI/CD
├── .woodpecker/               # Woodpecker CI pipelines
├── docker-compose.yml         # Full stack compose (dev + prod)
├── Dockerfile                 # Backend Docker image
├── DEPLOYMENT.md              # Production deployment guide
└── .env.sample                # Environment template
```

## License

Proprietary. All rights reserved.
