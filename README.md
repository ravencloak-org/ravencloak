# KOS Auth Backend

[![status-badge](https://drone.keeplearningos.com/api/badges/1/status.svg?events=push%2Ctag%2Crelease%2Cpull_request%2Cpull_request_closed%2Cpull_request_metadata%2Cdeployment)](https://drone.keeplearningos.com/repos/1)
![Auth](https://img.shields.io/badge/auth-v1.0.7-blue)
![Keycloak SPI](https://img.shields.io/badge/keycloak--spi-v0.0.0-green)

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

## Speeding Up Gradle Builds

This project uses several optimizations to speed up Gradle builds for both local development and CI.

### Build Cache (Enabled by Default)

Gradle caches task outputs locally. Subsequent builds reuse cached results when inputs haven't changed.

### Remote Build Cache (Optional)

Share build cache across your team and CI using S3 or S3-compatible storage (MinIO, Cloudflare R2, Backblaze B2).

**Setup:**

1. Create an S3 bucket (or use existing S3-compatible storage)

2. Add to your `.env`:
   ```env
   S3_BUILD_CACHE_BUCKET=your-gradle-cache-bucket
   S3_BUILD_CACHE_REGION=us-east-1
   S3_BUILD_CACHE_ACCESS_KEY_ID=your_access_key
   S3_BUILD_CACHE_SECRET_KEY=your_secret_key
   # For MinIO/R2/etc:
   # S3_BUILD_CACHE_ENDPOINT=https://minio.example.com:9000
   ```

3. Source the env before building:
   ```bash
   export $(cat .env | xargs)
   ./gradlew build
   ```

**How it works:**
- First build: Compiles and pushes results to S3
- Subsequent builds: Pulls cached results from S3 (local or CI)
- Team members share cache, so builds are faster for everyone

**S3 Bucket Policy (minimum required):**
```json
{
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:GetObject", "s3:PutObject"],
    "Resource": "arn:aws:s3:::your-bucket/gradle-cache/*"
  }]
}
```

**Recommended:** Add S3 lifecycle policy to auto-delete cache entries older than 30 days.

### Other Optimizations

These are already configured in `gradle.properties`:

| Setting | Description |
|---------|-------------|
| `org.gradle.parallel=true` | Build modules in parallel |
| `org.gradle.caching=true` | Enable local build cache |
| `org.gradle.configuration-cache=true` | Cache task graph (local dev) |

### Verifying Cache Hits

Run with `--info` to see cache statistics:
```bash
./gradlew build --info | grep -i "cache"
```

You should see output like:
```
> Task :compileKotlin FROM-CACHE
S3 cache: reads: 5, hits: 4, elapsed: 120ms
```

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
| `auth.yml` | Push/PR to `src/**` | Compile, build bootJar |
| `keycloak-spi.yml` | Push/PR to `keycloak-spi/**` | Compile, test, build JAR |
| `auth-release.yml` | Tag `v*` | Build auth backend, GitHub release |
| `keycloak-spi-release.yml` | Tag `spi-v*` | Build, test, deploy SPI, GitHub release |
| `release-all.yml` | Tag `release-v*` | Build both modules, GitHub release |

### Automatic Builds

- **Pull Requests**: Runs compile and tests for affected modules only
- **Push to main**: Runs build for affected modules
- **Path filtering**: Changes to `keycloak-spi/**` only trigger SPI pipeline, changes to `src/**` only trigger auth pipeline
- **Gradle caching**: Dependencies cached at `/opt/woodpecker-cache/gradle` for faster builds

### Release Workflow (Tag-Based)

All releases are triggered by git tags:

```bash
# Auth backend release
git tag v1.0.0 && git push origin v1.0.0

# Keycloak SPI release
git tag spi-v1.0.0 && git push origin spi-v1.0.0

# Combined release (both modules)
git tag release-v1.0.0 && git push origin release-v1.0.0
```

Each release pipeline will:
1. Build and test the module(s)
2. Generate changelog from commits
3. Update version badge in README
4. Create GitHub release with artifacts

### Release Pipeline Actions

| Step | Auth Backend | Keycloak SPI | Combined |
|------|--------------|--------------|----------|
| Build | `./gradlew bootJar` | `./gradlew :keycloak-spi:shadowJar` | Both |
| Test | Skipped (tested in PR) | `./gradlew :keycloak-spi:test` | SPI only |
| Deploy JAR | - | Copy to `/opt/keycloak-providers/` | SPI only |
| Changelog | Generate from commits | Generate from commits | Generate from commits |
| Update README | Update version badge | Update version badge | Both badges |
| GitHub Release | Create with JAR | Create with JAR | Both JARs |

### After SPI Release

Restart Keycloak to load the new SPI:
```bash
docker compose restart keycloak
```

Verify the SPI is loaded:
```bash
# Check Keycloak logs
docker compose logs keycloak | grep "kos-auth-storage"

# Or check in Keycloak Admin Console
# Go to: Realm Settings → User Federation → Add provider
# You should see "kos-auth-storage" in the list
```

### Woodpecker Endpoints

| Endpoint | URL |
|----------|-----|
| Dashboard | https://drone.keeplearningos.com/dsjkeeplearning/kos-auth-backend |
| Build Status Badge | `https://drone.keeplearningos.com/api/badges/dsjkeeplearning/kos-auth-backend/status.svg` |

### Troubleshooting Releases

**SPI not loading in Keycloak:**
- Verify JAR exists: `ls -la /opt/keycloak-providers/`
- Check Keycloak has read access to the volume
- Restart Keycloak: `docker compose restart keycloak`
- Check logs: `docker compose logs keycloak | grep -i error`

**Pipeline failing on README update:**
- This can happen if main branch is protected
- Either allow the CI bot to push, or remove the `update-readme` step

**Changelog showing "No changes":**
- Ensure commits follow the path patterns in changelog generation
- Check that the previous tag exists for comparison

## Configuration

The application uses environment variables for configuration. All database-related settings can be configured via the `.env` file:

- `DB_HOST`: Database host (default: localhost)
- `DB_PORT`: Database port (default: 5432, configured as 5433 in .env)
- `DB_NAME`: Database name (default: kos-auth)
- `DB_USERNAME`: Database username (default: postgres)
- `DB_PASSWORD`: Database password
