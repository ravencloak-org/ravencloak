# Production Deployment Guide

This guide covers deploying the Auth Frontend to EC2 with Cloudflare Tunnel.

## Prerequisites

- EC2 instance with Docker installed
- SSH access to the EC2 instance
- Cloudflare account with domain `keeplearningos.com`
- GitHub account with access to `ghcr.io/dsjkeeplearning/kos-auth-backend-frontend`

## Architecture

```
Internet → Cloudflare Tunnel → EC2 Instance → Docker Container
           forge.keeplearningos.com     :8090      auth-frontend
           api.forge.keeplearningos.com :8091      auth-backend
```

## Deployment Steps

### 1. Copy files to EC2

```bash
# From your local machine
cd /Users/jobinlawrance/Project/dsj/auth
scp deploy-production.sh setup-cloudflare-tunnel.sh ubuntu@<EC2_IP>:~
```

### 2. SSH into EC2

```bash
ssh ubuntu@<EC2_IP>
```

### 3. Deploy Docker Container

```bash
chmod +x deploy-production.sh
./deploy-production.sh
```

This will:
- Authenticate with GitHub Container Registry
- Pull the latest image (`1.0.13`)
- Stop old container (if exists)
- Start new container on port `8090`
- Verify deployment

### 4. Setup Cloudflare Tunnel

```bash
chmod +x setup-cloudflare-tunnel.sh
sudo ./setup-cloudflare-tunnel.sh
```

This will:
- Install `cloudflared`
- Authenticate with Cloudflare (opens browser)
- Create tunnel `auth-frontend-prod`
- Configure routing:
  - `forge.keeplearningos.com` → `localhost:8090` (frontend)
  - `api.forge.keeplearningos.com` → `localhost:8091` (backend)
- Create DNS CNAME records automatically
- Install as systemd service
- Start tunnel service

### 5. Verify Deployment

```bash
# Check Docker container
docker ps | grep auth-frontend
docker logs auth-frontend

# Check Cloudflare Tunnel
sudo systemctl status cloudflared
cloudflared tunnel list

# Test endpoints
curl http://localhost:8090
curl https://forge.keeplearningos.com
```

## Updating Production

When a new version is released:

```bash
# On EC2
./deploy-production.sh

# Enter your GitHub token when prompted
# The script will pull the latest image and restart the container
# Cloudflare Tunnel requires no changes
```

## Rollback

```bash
# Deploy specific version
docker pull ghcr.io/dsjkeeplearning/kos-auth-backend-frontend:1.0.12
docker stop auth-frontend
docker rm auth-frontend
docker run -d --name auth-frontend --restart unless-stopped -p 8090:80 \
  ghcr.io/dsjkeeplearning/kos-auth-backend-frontend:1.0.12
```

## Troubleshooting

### Container not accessible

```bash
# Check if container is running
docker ps | grep auth-frontend

# Check logs
docker logs auth-frontend -f

# Check port binding
netstat -tlnp | grep 8090
```

### Cloudflare Tunnel not working

```bash
# Check service status
sudo systemctl status cloudflared

# Check logs
sudo journalctl -u cloudflared -f

# Restart service
sudo systemctl restart cloudflared

# Validate configuration
cloudflared tunnel info auth-frontend-prod
```

### DNS not resolving

```bash
# Check DNS records
dig forge.keeplearningos.com
dig api.forge.keeplearningos.com

# Should show CNAME to <TUNNEL_ID>.cfargotunnel.com
```

## Environment Variables

The Docker image is pre-built with these production values (baked at build time):

```
VITE_API_BASE_URL=https://api.forge.keeplearningos.com
VITE_KEYCLOAK_URL=https://auth.keeplearningos.com
VITE_KEYCLOAK_REALM=saas-admin
VITE_KEYCLOAK_CLIENT_ID=kos-admin-web
```

To change these, you need to rebuild the image with new build args.

## Security Notes

- Cloudflare Tunnel provides automatic HTTPS
- No need to expose ports in security groups (tunnel uses outbound connections)
- Container runs with `--restart unless-stopped` for automatic recovery
- Credentials stored in `/root/.cloudflared/`

## Monitoring

```bash
# Container stats
docker stats auth-frontend

# Tunnel metrics
cloudflared tunnel info auth-frontend-prod

# Nginx access logs
docker logs auth-frontend -f
```
