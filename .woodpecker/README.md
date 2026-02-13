# Woodpecker CI Configuration

This document details the CI/CD setup using [Woodpecker CI](https://woodpecker-ci.org/) for the KOS Auth Backend project.

## Pipeline Overview

| Pipeline | File | Trigger | Description |
|----------|------|---------|-------------|
| Auth Release | `auth-release.yml` | Tag `v*` | Full release for auth backend |
| Keycloak SPI Release | `keycloak-spi-release.yml` | Tag `spi-v*` or manual | Full release for SPI |
| Combined Release | `release-all.yml` | Tag `release-v*` or manual | Release both modules together |
| Deploy | `deploy.yml` | Manual only | Deploy existing release |

> **Note:** Auth build & test on push/PR is handled by GitHub Actions (`.github/workflows/build.yml`).

## Caching Strategy

### Gradle Dependencies (Volume Mount)

All pipelines use a shared volume mount for Gradle dependencies:

```yaml
volumes:
  - /var/lib/woodpecker/cache/gradle:/root/.gradle
```

This provides instant cache restoration without network overhead.

**Host Setup:**
```bash
sudo mkdir -p /var/lib/woodpecker/cache/gradle
sudo chown -R 1000:1000 /var/lib/woodpecker/cache/gradle
```

### S3 Build Cache (Remote)

For sharing build outputs across CI runs, the project uses S3-compatible storage (Cloudflare R2):

```yaml
environment:
  S3_BUILD_CACHE_BUCKET:
    from_secret: s3_build_cache_bucket
  S3_BUILD_CACHE_REGION:
    from_secret: s3_build_cache_region
  S3_BUILD_CACHE_ACCESS_KEY_ID:
    from_secret: s3_build_cache_access_key_id
  S3_BUILD_CACHE_SECRET_KEY:
    from_secret: s3_build_cache_secret_key
  S3_BUILD_CACHE_ENDPOINT:
    from_secret: s3_build_cache_endpoint
```

The Gradle build is configured via `settings.gradle.kts` to use the `com.github.burrunan.s3-build-cache` plugin.

### Docker Build Cache (Registry)

Docker builds use buildx with registry-based caching:

```yaml
docker buildx build \
  --cache-from type=registry,ref=ghcr.io/dsjkeeplearning/kos-auth-backend:cache \
  --cache-to type=registry,ref=ghcr.io/dsjkeeplearning/kos-auth-backend:cache,mode=max \
  ...
```

## Release Flow

### Tag-Based Releases (Automatic)

Create and push a git tag to trigger the corresponding release pipeline:

```bash
# Auth backend only
git tag v1.0.0 && git push origin v1.0.0

# Keycloak SPI only
git tag spi-v1.0.0 && git push origin spi-v1.0.0

# Combined release (both modules)
git tag release-v1.0.0 && git push origin release-v1.0.0
```

### Manual Releases (CLI)

Trigger releases via Woodpecker CLI with automatic version increment:

```bash
# Install Woodpecker CLI
# See: https://woodpecker-ci.org/docs/usage/cli

# Set up authentication
export WOODPECKER_SERVER=https://drone.keeplearningos.com
export WOODPECKER_TOKEN=<your-token>

# Keycloak SPI release (auto-increments patch version)
woodpecker-cli pipeline create dsjkeeplearning/kos-auth-backend \
  --branch main \
  --var DEPLOY_TO=keycloak-spi

# Combined release (auto-increments patch version)
woodpecker-cli pipeline create dsjkeeplearning/kos-auth-backend \
  --branch main \
  --var DEPLOY_TO=release-all

# Deploy only (uses latest release)
woodpecker-cli pipeline create dsjkeeplearning/kos-auth-backend \
  --branch main \
  --var DEPLOY_TO=deploy
```

### Release Pipeline Steps

Each release pipeline performs:

1. **Determine Version** - Use tag or auto-increment from latest
2. **Build & Test** - Compile, run tests, create artifacts
3. **Generate Changelog** - Extract commits since last release
4. **Deploy** - Copy artifacts to target location
5. **Update README** - Update version badges
6. **GitHub Release** - Create release with artifacts and changelog

### Version Auto-Increment Logic

When triggered manually, the pipeline finds the latest tag and increments the patch version:

```bash
LATEST_TAG=$(git tag -l 'spi-v*' --sort=-v:refname | head -n1)
# spi-v1.0.15 -> spi-v1.0.16
```

## Deployment

### Auth Backend (Docker)

The auth backend is deployed as a Docker container:

1. Build image with version tag
2. Push to GitHub Container Registry (ghcr.io)
3. Pull image on deployment server
4. Stop and remove existing container
5. Start new container with environment variables

```yaml
docker run -d --name auth-backend \
  --restart unless-stopped \
  -p 8080:8080 \
  -e KEYCLOAK_ISSUER_PREFIX="$KEYCLOAK_ISSUER_PREFIX" \
  -e KEYCLOAK_SAAS_ISSUER_URI="$KEYCLOAK_SAAS_ISSUER_URI" \
  ... \
  "ghcr.io/dsjkeeplearning/kos-auth-backend:$VERSION"
```

### Keycloak SPI (JAR)

The SPI JAR is deployed to a shared volume:

1. Build fat JAR with Shadow plugin
2. Copy to `/opt/keycloak-providers/`
3. Restart Keycloak container to load new SPI

```yaml
volumes:
  - /opt/keycloak-providers:/shared/providers
  - /var/run/docker.sock:/var/run/docker.sock
commands:
  - cp release-assets/keycloak-user-storage-spi*.jar /shared/providers/
  - docker restart keycloak
```

**Host Setup:**
```bash
sudo mkdir -p /opt/keycloak-providers
sudo chmod 755 /opt/keycloak-providers
```

Keycloak's docker-compose must mount this volume:
```yaml
volumes:
  - /opt/keycloak-providers:/opt/keycloak/providers:ro
```

## Secrets Configuration

### Required Secrets

| Secret | Description | Used By |
|--------|-------------|---------|
| `github_token` | GitHub PAT for releases and README updates | All release pipelines |
| `s3_build_cache_bucket` | S3 bucket name | All build steps |
| `s3_build_cache_region` | S3 region | All build steps |
| `s3_build_cache_access_key_id` | S3 access key | All build steps |
| `s3_build_cache_secret_key` | S3 secret key | All build steps |
| `s3_build_cache_endpoint` | S3 endpoint (for R2/MinIO) | All build steps |

### Application Secrets (Deploy)

| Secret | Description |
|--------|-------------|
| `keycloak_issuer_prefix` | Base URL for Keycloak realms |
| `keycloak_saas_issuer_uri` | Full issuer URI for saas-admin |
| `saas_admin_client_secret` | OAuth2 client secret |
| `db_host` | PostgreSQL host |
| `db_port` | PostgreSQL port |
| `db_name` | Database name |
| `db_username` | Database user |
| `db_password` | Database password |
| `spring_profiles_active` | Active Spring profiles |

### Adding Secrets via CLI

```bash
# Using the helper script
./scripts/add-woodpecker-secrets.sh

# Or manually
woodpecker-cli secret add \
  --repository dsjkeeplearning/kos-auth-backend \
  --name github_token \
  --value "<token>"
```

## Troubleshooting

### Gradle Cache Issues

**Symptoms:** Build downloads all dependencies every time

**Solution:** Verify volume mount exists and has correct permissions:
```bash
ls -la /var/lib/woodpecker/cache/gradle
# Should show .gradle cache directories
```

### JAR Not Found in Release

**Symptoms:** `no matches found for release-assets/*.jar`

**Solutions:**
1. Add `clean` task before build to remove stale artifacts
2. Add debug logging: `ls -la build/libs/`
3. Use dynamic JAR finding: `find release-assets -name "*.jar"`

### Docker Buildx Cache Not Working

**Symptoms:** Docker build doesn't use cache

**Solution:** Ensure buildx builder uses docker-container driver:
```yaml
docker buildx create --name multiarch --driver docker-container --use || docker buildx use multiarch
```

### README Update Failing

**Symptoms:** Push to main fails in update-readme step

**Solutions:**
1. Ensure `github_token` has repo write permissions
2. Check if branch protection rules block CI pushes
3. Verify git remote URL is set correctly

### Keycloak Not Loading SPI

**Symptoms:** SPI not available in User Federation

**Solutions:**
1. Verify JAR exists: `ls -la /opt/keycloak-providers/`
2. Check Keycloak volume mount is correct
3. Restart Keycloak: `docker restart keycloak`
4. Check logs: `docker logs keycloak | grep -i spi`

### YAML Parsing Errors

**Common Issues:**
- Colons in command strings - wrap in quotes or use different character
- Variable expansion `${VAR}` - use `$VAR` instead
- Multi-line commands - use `|` block scalar

**Example Fix:**
```yaml
# Bad - YAML sees this as a key-value pair
- echo "Status: Failed"

# Good - quoted string
- 'echo "Status: Failed"'

# Good - different separator
- echo "Status - Failed"
```

## Pipeline Files Reference

```
.woodpecker/
├── README.md              # This documentation
├── auth-release.yml       # Auth backend release (tag v*)
├── keycloak-spi-release.yml # Keycloak SPI release (tag spi-v*)
├── release-all.yml        # Combined release (tag release-v*)
└── deploy.yml             # Manual deployment
```
