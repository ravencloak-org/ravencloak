# CI/CD

The project uses **Woodpecker CI** for the auth backend and **GitHub Actions** for SDK/SPI publishing.

## Pipeline Overview

### Woodpecker CI

| Pipeline | File | Trigger | Description |
|----------|------|---------|-------------|
| Auth Build | `auth.yml` | Push/PR to `src/**` | Compile and build Spring Boot app |
| Auth Release | `auth-release.yml` | Tag `v*` | Full release for auth backend |
| Keycloak SPI Release | `keycloak-spi-release.yml` | Tag `spi-v*` or manual | Deploy SPI from GitHub Release |
| Combined Release | `release-all.yml` | Tag `release-v*` or manual | Release both modules together |
| Deploy | `deploy.yml` | Manual only | Deploy existing release |

**Dashboard:** [drone.keeplearningos.com](https://drone.keeplearningos.com/dsjkeeplearning/kos-auth-backend)

### GitHub Actions

| Workflow | Trigger | Description |
|----------|---------|-------------|
| `keycloak-spi.yml` | Push to `keycloak-spi/**`, tag `spi-v*`, manual | Build, test & release SPI JAR |
| `auth-sdk-publish.yml` | Push to `forge/**`/`scim-common/**`, tag `sdk-v*`, manual | Build, test & publish Forge SDK |

## Release Workflow

### Tag-Based Releases (Automatic)

```bash
# Auth backend only
git tag v1.0.0 && git push origin v1.0.0

# Keycloak SPI only
git tag spi-v1.0.0 && git push origin spi-v1.0.0

# Combined release (both modules)
git tag release-v1.0.0 && git push origin release-v1.0.0
```

### Manual Releases (CLI)

=== "Woodpecker CLI"

    ```bash
    # Keycloak SPI release (auto-increments patch version)
    woodpecker-cli pipeline create dsjkeeplearning/kos-auth-backend \
      --branch main --var DEPLOY_TO=keycloak-spi

    # Combined release
    woodpecker-cli pipeline create dsjkeeplearning/kos-auth-backend \
      --branch main --var DEPLOY_TO=release-all

    # Deploy only (uses latest release)
    woodpecker-cli pipeline create dsjkeeplearning/kos-auth-backend \
      --branch main --var DEPLOY_TO=deploy
    ```

=== "GitHub CLI"

    ```bash
    # Keycloak SPI release
    gh workflow run keycloak-spi.yml -f version=1.0.1

    # Forge SDK release
    gh workflow run auth-sdk-publish.yml -f version=0.2.0
    ```

### Release Pipeline Steps

Each release pipeline performs:

1. **Determine Version** — use tag or auto-increment from latest
2. **Build & Test** — compile, run tests, create artifacts
3. **Generate Changelog** — extract commits since last release
4. **Deploy** — copy artifacts to target location
5. **Update README** — update version badges
6. **GitHub Release** — create release with artifacts and changelog

## Caching Strategy

### Gradle Dependencies (Volume Mount)

All Woodpecker pipelines use a shared volume mount:

```yaml
volumes:
  - /var/lib/woodpecker/cache/gradle:/root/.gradle
```

**Host setup:**

```bash
sudo mkdir -p /var/lib/woodpecker/cache/gradle
sudo chown -R 1000:1000 /var/lib/woodpecker/cache/gradle
```

### S3 Build Cache (Remote)

Build outputs are shared across CI runs via Cloudflare R2, configured through the `com.github.burrunan.s3-build-cache` Gradle plugin.

### Docker Build Cache (Registry)

Docker builds use `buildx` with registry-based caching:

```bash
docker buildx build \
  --cache-from type=registry,ref=ghcr.io/dsjkeeplearning/kos-auth-backend:cache \
  --cache-to type=registry,ref=ghcr.io/dsjkeeplearning/kos-auth-backend:cache,mode=max \
  ...
```

## Secrets

### Woodpecker Secrets

| Secret | Purpose |
|--------|---------|
| `github_token` | GitHub releases, Docker registry |
| `s3_build_cache_bucket` | S3 bucket name |
| `s3_build_cache_region` | S3 region |
| `s3_build_cache_access_key_id` | S3 access key |
| `s3_build_cache_secret_key` | S3 secret key |
| `s3_build_cache_endpoint` | S3 endpoint (for R2/MinIO) |
| `db_*`, `keycloak_*` | Application deployment |

### GitHub Actions Secrets

| Secret | Purpose |
|--------|---------|
| `GITHUB_TOKEN` | Auto-provided, GitHub Packages & Releases |
| `S3_BUILD_CACHE_*` | Remote Gradle build cache |

## Deployment Flow

### Auth Backend

1. Docker image built with version tag
2. Pushed to GitHub Container Registry (`ghcr.io`)
3. Pulled on deployment server
4. Existing container stopped and removed
5. New container started with environment variables

### Keycloak SPI

1. GitHub Actions builds fat JAR and creates GitHub Release
2. Woodpecker downloads JAR from release
3. JAR copied to `/opt/keycloak-providers/`
4. Keycloak container restarted

### Forge SDK

GitHub Actions publishes to GitHub Packages (Maven).

## Troubleshooting

??? info "Gradle cache issues"
    **Symptom:** Build downloads all dependencies every time

    Verify volume mount exists and has correct permissions:
    ```bash
    ls -la /var/lib/woodpecker/cache/gradle
    ```

??? info "JAR not found in release"
    **Symptom:** `no matches found for release-assets/*.jar`

    - Add `clean` task before build to remove stale artifacts
    - Add debug logging: `ls -la build/libs/`

??? info "Docker buildx cache not working"
    **Symptom:** Docker build doesn't use cache

    Ensure buildx builder uses docker-container driver:
    ```bash
    docker buildx create --name multiarch --driver docker-container --use \
      || docker buildx use multiarch
    ```

??? info "Keycloak not loading SPI"
    **Symptom:** SPI not available in User Federation

    1. Verify JAR exists: `ls -la /opt/keycloak-providers/`
    2. Check Keycloak volume mount
    3. Restart: `docker restart keycloak`
    4. Check logs: `docker logs keycloak | grep -i spi`
