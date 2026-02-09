# Keycloak User Storage SPI

A read-only Keycloak User Storage Provider that validates users by calling the auth backend's REST API.

## How It Works

```
Keycloak login request
  → ExternalUserStorageProvider
    → GET http://auth-backend:8080/api/users/{email}
      → 200: user exists → ExternalUserAdapter wraps the response
      → 404: user not found → null (Keycloak tries next provider)
```

- Uses Java 11+ `HttpClient` for REST calls (no external HTTP libraries)
- Uses Keycloak's `JsonSerialization` for JSON parsing
- Fully read-only — no user creation or modification through the SPI

## Key Classes

| Class | Purpose |
|-------|---------|
| `ExternalUser` | Data class for user data from REST API |
| `ExternalUserAdapter` | Maps `ExternalUser` to Keycloak's `UserModel` (read-only) |
| `ExternalUserStorageProvider` | Implements user lookup via HTTP client |
| `ExternalUserStorageProviderFactory` | Factory registered with Keycloak SPI |

## Building

```bash
./gradlew :keycloak-spi:shadowJar
```

The fat JAR is created at:

```
keycloak-spi/build/libs/keycloak-user-storage-spi-<version>.jar
```

!!! info
    The Shadow plugin bundles and relocates the Kotlin stdlib. Keycloak SPI dependencies are `compileOnly` (provided by Keycloak at runtime).

## Deploying to Keycloak

### Option 1: Local / Standalone Keycloak

1. Copy the JAR to Keycloak's providers directory:

    ```bash
    cp keycloak-spi/build/libs/keycloak-user-storage-spi-*.jar \
       $KEYCLOAK_HOME/providers/
    ```

2. Rebuild Keycloak:

    ```bash
    $KEYCLOAK_HOME/bin/kc.sh build
    ```

3. In Keycloak Admin Console, go to **User Federation** and add the `kos-auth-storage` provider.

### Option 2: Docker Compose (Local Development)

```bash
# Build the SPI
./gradlew :keycloak-spi:shadowJar

# Copy to shared providers folder
cp keycloak-spi/build/libs/keycloak-user-storage-spi-*.jar \
   /opt/keycloak-providers/keycloak-user-storage-spi.jar

# Start Keycloak
docker compose up -d
```

### Option 3: CI/CD Automated Deployment

Woodpecker CI builds the SPI on release tags and deploys the JAR to a shared volume accessible to Keycloak.

1. Create the shared providers directory on the host:

    ```bash
    sudo mkdir -p /opt/keycloak-providers
    sudo chmod 755 /opt/keycloak-providers
    ```

2. Add volume mount to your Keycloak service in `docker-compose.yml`:

    ```yaml
    volumes:
      - /opt/keycloak-providers:/opt/keycloak/providers:ro
    ```

3. Create a release tag to trigger deployment:

    ```bash
    git tag spi-v1.0.0 && git push origin spi-v1.0.0
    ```

## Release

### Via GitHub Actions

```bash
# Trigger a release with a specific version
gh workflow run keycloak-spi.yml -f version=1.0.1
```

### Via Git Tag

```bash
git tag spi-v1.0.0 && git push origin spi-v1.0.0
```

### Via Woodpecker CLI

```bash
woodpecker-cli pipeline create dsjkeeplearning/kos-auth-backend \
  --branch main --var DEPLOY_TO=keycloak-spi
```

## Troubleshooting

**SPI not available in User Federation:**

1. Verify JAR exists: `ls -la /opt/keycloak-providers/`
2. Check Keycloak volume mount is correct
3. Restart Keycloak: `docker restart keycloak`
4. Check logs: `docker logs keycloak | grep -i spi`
