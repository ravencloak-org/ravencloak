# Getting Started

## Prerequisites

- Java 21
- PostgreSQL with [ParadeDB](https://docs.paradedb.com/deploy/self-hosted/extension) extensions
- Gradle (included via wrapper)
- Docker & Docker Compose (for Keycloak)

## Environment Configuration

1. Copy the sample environment file:

    ```bash
    cp .env.sample .env
    ```

2. Update `.env` with your database and Keycloak settings:

    ```env
    DB_HOST=localhost
    DB_PORT=5433
    DB_NAME=kos-auth
    DB_USERNAME=postgres
    DB_PASSWORD=your_password_here

    KEYCLOAK_ISSUER_PREFIX=http://localhost:8088/realms/
    KEYCLOAK_SAAS_ISSUER_URI=http://localhost:8088/realms/saas-admin
    SAAS_ADMIN_CLIENT_SECRET=your_client_secret
    ```

## Database Setup

PostgreSQL must have ParadeDB extensions installed for full-text search:

1. Install ParadeDB binaries from [docs.paradedb.com](https://docs.paradedb.com/deploy/self-hosted/extension)
2. **Postgres 17+**: No config changes needed
3. **Postgres 16 and earlier**: Add to `postgresql.conf` and restart:

    ```ini
    shared_preload_libraries = 'pg_search'
    ```

4. Extensions (`pg_search`, `vector`) are created automatically by Flyway migrations

!!! tip
    The easiest way to get started is with the [Docker Compose setup](deployment/docker.md), which uses the `paradedb/paradedb` image with extensions pre-installed.

## Start Infrastructure

```bash
docker compose up -d
```

This starts:

- **ParadeDB** (PostgreSQL + extensions) on port `5234`
- **Keycloak** on port `8088` (admin UI: `http://localhost:8088`, credentials: `admin/admin`)

## Build & Run

```bash
# Build the entire project
./gradlew build

# Run the application
./gradlew bootRun
```

The application starts on port **8080**.

## Common Build Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build all modules |
| `./gradlew bootRun` | Run the application |
| `./gradlew test` | Run all tests |
| `./gradlew test --tests "com.keeplearning.auth.SomeTest"` | Run a single test class |
| `./gradlew clean build` | Clean and rebuild |
| `./gradlew :keycloak-spi:shadowJar` | Build keycloak-spi fat JAR |

## Speeding Up Gradle Builds

### Local Build Cache

Gradle caches task outputs locally by default. Subsequent builds reuse cached results when inputs haven't changed.

### Remote Build Cache (Optional)

Share build cache across your team using S3-compatible storage (MinIO, Cloudflare R2, Backblaze B2):

1. Create an S3 bucket
2. Add to your `.env`:

    ```env
    S3_BUILD_CACHE_BUCKET=your-gradle-cache-bucket
    S3_BUILD_CACHE_REGION=us-east-1
    S3_BUILD_CACHE_ACCESS_KEY_ID=your_access_key
    S3_BUILD_CACHE_SECRET_KEY=your_secret_key
    # For MinIO/R2:
    # S3_BUILD_CACHE_ENDPOINT=https://minio.example.com:9000
    ```

3. Source the env before building:

    ```bash
    export $(cat .env | xargs)
    ./gradlew build
    ```

### Verify Cache Hits

```bash
./gradlew build --info | grep -i "cache"
# Should show: Task :compileKotlin FROM-CACHE
```

### Build Optimizations

These are configured in `gradle.properties`:

| Setting | Description |
|---------|-------------|
| `org.gradle.parallel=true` | Build modules in parallel |
| `org.gradle.caching=true` | Enable local build cache |
| `org.gradle.configuration-cache=true` | Cache task graph |
