# Deployment Guide

## Overview

**CI:** Woodpecker (build, test, Docker image push, GitHub releases)
**CD:** GitHub Actions (production deployment via SSH + docker-compose)

## How It Works

### 1. Create a Release
```bash
git tag -a v1.0.X -m "Release v1.0.X: Description"
git push origin v1.0.X
```

### 2. Automated Pipeline
1. **Woodpecker CI** (on tag push):
   - Builds JAR with Gradle
   - Pushes Docker image to GHCR (versioned + latest tags)
   - Creates GitHub release with changelog
   - Uploads JAR artifacts

2. **GitHub Actions** (on release published):
   - SSHs into production server
   - Creates `.env` file from GitHub secrets
   - Pulls latest Docker image
   - Restarts auth-backend via docker-compose
   - Shows deployment status

## Required GitHub Secrets

Configure these in: `Settings > Secrets and variables > Actions > Repository secrets`

### SSH Access
| Secret | Example Value | Description |
|--------|---------------|-------------|
| `PRODUCTION_HOST` | `insight-service.jobin.wtf` | Production server hostname/IP |
| `PRODUCTION_USER` | `ubuntu` | SSH username |
| `PRODUCTION_SSH_KEY` | `-----BEGIN OPENSSH PRIVATE KEY-----...` | SSH private key (full PEM format) |

### Database
| Secret | Example Value | Description |
|--------|---------------|-------------|
| `DB_HOST` | `172.17.0.1` | PostgreSQL host (Docker bridge or external) |
| `DB_PORT` | `5433` | PostgreSQL port |
| `DB_NAME` | `kos-auth` | Database name |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `your-secure-password` | Database password |

### Keycloak
| Secret | Example Value | Description |
|--------|---------------|-------------|
| `KEYCLOAK_BASE_URL` | `https://auth.keeplearningos.com` | Keycloak base URL |
| `KEYCLOAK_ISSUER_PREFIX` | `https://auth.keeplearningos.com/realms/` | JWT issuer prefix |
| `KEYCLOAK_SAAS_ISSUER_URI` | `https://auth.keeplearningos.com/realms/master` | SaaS admin realm issuer |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `kos-admin-api` | Admin client ID |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | `your-client-secret` | Admin client secret |
| `SAAS_ADMIN_CLIENT_SECRET` | `your-saas-secret` | SaaS admin client secret |

### Spring Profile
| Secret | Example Value | Description |
|--------|---------------|-------------|
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring profile (use `prod` for production) |

## Manual Deployment (Emergency)

If GitHub Actions fails, deploy manually:

```bash
ssh insight-service.jobin.wtf
cd /home/ubuntu

# Create .env with production values
cat > .env <<'EOF'
DB_HOST=172.17.0.1
DB_PORT=5433
DB_NAME=kos-auth
DB_USERNAME=postgres
DB_PASSWORD=your-password
KEYCLOAK_BASE_URL=https://auth.keeplearningos.com
KEYCLOAK_ISSUER_PREFIX=https://auth.keeplearningos.com/realms/
KEYCLOAK_SAAS_ISSUER_URI=https://auth.keeplearningos.com/realms/master
KEYCLOAK_ADMIN_CLIENT_ID=kos-admin-api
KEYCLOAK_ADMIN_CLIENT_SECRET=your-secret
SAAS_ADMIN_CLIENT_SECRET=your-secret
SPRING_PROFILES_ACTIVE=prod
OTEL_SDK_DISABLED=true
EOF

# Pull and restart
docker-compose pull auth-backend
docker-compose up -d auth-backend
docker logs auth-backend --tail 50
```

## Monitoring

Check deployment status:
```bash
ssh insight-service.jobin.wtf "docker ps | grep auth-backend"
ssh insight-service.jobin.wtf "docker logs auth-backend --tail 100"
```

Health check:
```bash
curl https://api.forge.keeplearningos.com/actuator/health
```

## Rollback

To rollback to a previous version:

```bash
ssh insight-service.jobin.wtf
cd /home/ubuntu

# Edit docker-compose.yml to use specific version tag
# Change: image: ghcr.io/dsjkeeplearning/kos-auth-backend:latest
# To:     image: ghcr.io/dsjkeeplearning/kos-auth-backend:1.0.19

docker-compose pull auth-backend
docker-compose up -d auth-backend
```

Or retag and redeploy:
```bash
git tag -d v1.0.X                    # Delete local tag
git push origin :refs/tags/v1.0.X    # Delete remote tag
git tag -a v1.0.X <commit-hash> -m "Rollback"
git push origin v1.0.X              # Trigger new deployment
```
