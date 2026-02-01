# KOS Auth Backend

[![Build Status](https://drone.keeplearningos.com/api/badges/dsjkeeplearning/kos-auth-backend/status.svg)](https://drone.keeplearningos.com/dsjkeeplearning/kos-auth-backend)
![Auth](https://img.shields.io/badge/auth-v0.0.0-blue)
![Keycloak SPI](https://img.shields.io/badge/keycloak--spi-spi--v0.0.0-green)

A multi-tenant authentication backend with Spring Boot/Kotlin and a Keycloak User Storage SPI for federated user validation via REST API.

## Setup

### Prerequisites
- Java 21
- PostgreSQL database
- Gradle (included via wrapper)

### Environment Configuration

1. Copy the sample environment file:
   ```bash
   cp .env.sample .env
   ```

2. Update the `.env` file with your database configuration:
   ```env
   DB_HOST=localhost
   DB_PORT=5433
   DB_NAME=kos-auth
   DB_USERNAME=postgres
   DB_PASSWORD=your_password_here
   ```

### Database Setup

Make sure you have a PostgreSQL database running on the configured host and port. The application will use Flyway to automatically run database migrations.

### Running the Application

1. Build the project:
   ```bash
   ./gradlew build
   ```

2. Run the application:
   ```bash
   ./gradlew bootRun
   ```

The application will start on the default Spring Boot port (8080).

## Features

- R2DBC reactive database connectivity
- Flyway database migrations
- Spring Security integration
- OAuth2 client support
- WebFlux reactive web framework

## Modules

### Main Application (`auth`)

The core authentication backend service.

### Keycloak User Storage SPI (`keycloak-spi`)

A read-only Keycloak User Storage Provider that validates users by calling the auth backend's REST API.

#### Building the SPI

```bash
./gradlew :keycloak-spi:shadowJar
```

The fat JAR will be created at `keycloak-spi/build/libs/keycloak-user-storage-spi-0.0.1-SNAPSHOT.jar`.

#### Deploying to Keycloak

**Option 1: Local/Standalone Keycloak**

1. Copy the JAR to Keycloak's providers directory:
   ```bash
   cp keycloak-spi/build/libs/keycloak-user-storage-spi-0.0.1-SNAPSHOT.jar $KEYCLOAK_HOME/providers/
   ```

2. Rebuild Keycloak (or restart):
   ```bash
   $KEYCLOAK_HOME/bin/kc.sh build
   ```

3. In Keycloak Admin Console, go to **User Federation** and add `kos-auth-storage` provider.

**Option 2: Docker Compose with Woodpecker CI**

Woodpecker CI builds the SPI on release tags and places the JAR in a shared volume accessible to Keycloak.

1. Create the shared providers directory on the host:
   ```bash
   sudo mkdir -p /opt/keycloak-providers
   sudo chmod 755 /opt/keycloak-providers
   ```

2. Add volume mount to your Keycloak service in docker-compose:
   ```yaml
   volumes:
     - /opt/keycloak-providers:/opt/keycloak/providers:ro
   ```

3. Create a release tag to deploy (see CI/CD section below).

**Option 3: Docker Compose (Local Development)**

For local development without Woodpecker CI:

```bash
# Build the SPI first
./gradlew :keycloak-spi:shadowJar

# Copy to shared providers folder
cp keycloak-spi/build/libs/keycloak-user-storage-spi-*.jar /opt/keycloak-providers/keycloak-user-storage-spi.jar

# Start Keycloak
docker compose up -d
```

#### How it Works

- The SPI calls `http://auth-backend:8080/api/users/{email}` to validate if a user exists
- Returns user data (id, email, firstName, lastName) on HTTP 200
- Returns null (user not found) on HTTP 404
- Uses Java 11+ HttpClient for REST calls
- Uses Keycloak's JsonSerialization for JSON parsing

## CI/CD

This project uses [Woodpecker CI](https://woodpecker-ci.org/) for continuous integration and deployment.

**Woodpecker Dashboard:** https://drone.keeplearningos.com/dsjkeeplearning/kos-auth-backend

### Pipelines

| Pipeline | Trigger | Description |
|----------|---------|-------------|
| `keycloak-spi.yml` | Push/PR to `keycloak-spi/**` | Compile, test, build JAR |
| `keycloak-spi-release.yml` | Tag `spi-v*` | Build, deploy to Keycloak, GitHub release |
| `auth.yml` | Push/PR to `src/**` | Compile, test, build bootJar |
| `auth-release.yml` | Tag `v*` | Build, GitHub release |

### Woodpecker Endpoints

| Endpoint | URL |
|----------|-----|
| Dashboard | https://drone.keeplearningos.com/dsjkeeplearning/kos-auth-backend |
| Build Status Badge | `https://drone.keeplearningos.com/api/badges/dsjkeeplearning/kos-auth-backend/status.svg` |

### Automatic Builds

- **Pull Requests**: Automatically runs compile and tests for affected modules
- **Push to main**: Runs full build and test suite for affected modules
- Build status appears on GitHub PRs

### Creating Releases

**Keycloak SPI Release:**
```bash
git tag spi-v1.0.0
git push origin spi-v1.0.0
```
This will:
- Build the SPI JAR with version `1.0.0`
- Deploy JAR to `/opt/keycloak-providers/`
- Create a GitHub Release with the JAR attached

**Auth Backend Release:**
```bash
git tag v1.0.0
git push origin v1.0.0
```
This will:
- Build the Auth Backend JAR with version `1.0.0`
- Create a GitHub Release with the JAR attached

### After SPI Release

Restart Keycloak to load the new SPI:
```bash
docker compose restart keycloak
```

## Configuration

The application uses environment variables for configuration. All database-related settings can be configured via the `.env` file:

- `DB_HOST`: Database host (default: localhost)
- `DB_PORT`: Database port (default: 5432, configured as 5433 in .env)
- `DB_NAME`: Database name (default: kos-auth)
- `DB_USERNAME`: Database username (default: postgres)
- `DB_PASSWORD`: Database password
