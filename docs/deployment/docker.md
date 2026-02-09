# Docker Compose Setup

The project provides a `docker-compose.yml` for local development with all required infrastructure.

## Services

| Service | Image | Port | Description |
|---------|-------|------|-------------|
| `paradedb` | `paradedb/paradedb:latest` | `5234` → `5432` | PostgreSQL with ParadeDB extensions |
| `keycloak` | `quay.io/keycloak/keycloak:26.5.0` | `8088` → `8080` | Identity provider |
| `auth-backend` | `ghcr.io/dsjkeeplearning/kos-auth-backend:latest` | `8080` → `8080` | Auth backend service |

## Quick Start

```bash
# Start all services
docker compose up -d

# Or start only infrastructure (run app locally)
docker compose up -d paradedb keycloak
```

## Service Details

### ParadeDB (PostgreSQL)

PostgreSQL with ParadeDB extensions pre-installed (`pg_search` for BM25 full-text search, `pgvector` for vector embeddings).

- **Port:** `5234` (mapped from container's `5432`)
- **Credentials:** `postgres` / `postgres`
- **Init script:** `docker/init-db.sql` creates required databases
- **Health check:** `pg_isready` every 5 seconds

### Keycloak

Identity and access management server.

- **Admin UI:** [http://localhost:8088](http://localhost:8088)
- **Admin credentials:** `admin` / `admin`
- **Database:** uses the ParadeDB PostgreSQL instance (`keycloak` database)
- **SPI providers:** mounts `keycloak-spi/build/libs/` for local development

!!! tip
    Build the SPI JAR before starting Keycloak:
    ```bash
    ./gradlew :keycloak-spi:shadowJar
    docker compose up -d
    ```

### Auth Backend

The main authentication backend service.

- **Port:** `8080`
- **Database:** connects to ParadeDB on internal port `5432`
- **Keycloak:** connects to Keycloak on internal port `8080`

## Environment Variables

The auth-backend container uses these environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `paradedb` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `kos-auth` | Database name |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `KEYCLOAK_BASE_URL` | `http://keycloak:8080` | Keycloak internal URL |
| `KEYCLOAK_ISSUER_PREFIX` | `http://localhost:8088/realms/` | JWT issuer prefix |
| `KEYCLOAK_SAAS_ISSUER_URI` | `http://localhost:8088/realms/saas-admin` | SaaS admin issuer |
| `SAAS_ADMIN_CLIENT_SECRET` | — | OAuth2 client secret |

## Running Locally (Without Docker for the App)

For development, you may want to run only infrastructure in Docker and run the app with Gradle:

```bash
# Start only database and Keycloak
docker compose up -d paradedb keycloak

# Configure .env for local ports
cat > .env << 'EOF'
DB_HOST=localhost
DB_PORT=5234
DB_NAME=kos-auth
DB_USERNAME=postgres
DB_PASSWORD=postgres
KEYCLOAK_ISSUER_PREFIX=http://localhost:8088/realms/
KEYCLOAK_SAAS_ISSUER_URI=http://localhost:8088/realms/saas-admin
EOF

# Run the application
./gradlew bootRun
```

## Production Deployment

For production, the auth backend is deployed as a Docker container:

```bash
docker run -d --name auth-backend \
  --restart unless-stopped \
  -p 8080:8080 \
  -e KEYCLOAK_ISSUER_PREFIX="$KEYCLOAK_ISSUER_PREFIX" \
  -e KEYCLOAK_SAAS_ISSUER_URI="$KEYCLOAK_SAAS_ISSUER_URI" \
  -e SAAS_ADMIN_CLIENT_SECRET="$SAAS_ADMIN_CLIENT_SECRET" \
  -e DB_HOST="$DB_HOST" \
  -e DB_PORT="$DB_PORT" \
  -e DB_NAME="$DB_NAME" \
  -e DB_USERNAME="$DB_USERNAME" \
  -e DB_PASSWORD="$DB_PASSWORD" \
  "ghcr.io/dsjkeeplearning/kos-auth-backend:$VERSION"
```

See [CI/CD](ci-cd.md) for the automated deployment pipeline.
