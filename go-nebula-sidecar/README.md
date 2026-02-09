# Nebula Certificate Sidecar

A lightweight Go service for Nebula certificate generation and management. Runs alongside the Spring Boot auth backend and delegates token validation to it.

## Architecture

```
┌──────────────┐     ┌─────────────────┐     ┌──────────────┐
│ Auth Backend │────▶│ Nebula Sidecar  │────▶│  PostgreSQL   │
│ (Spring Boot)│◀────│   (Go / Gin)    │     │  (shared DB)  │
└──────────────┘     └─────────────────┘     └──────────────┘
  JWT validation       Cert generation         Same instance
```

- **Not exposed externally** — accessed only by the auth backend on the Docker network
- **Shared PostgreSQL** — uses the same database as the auth backend (`kos-auth`)
- **Token validation** — validates JWTs by calling the auth backend

## Quick Start

### Prerequisites

- Go 1.25+
- PostgreSQL (via docker-compose)
- Nebula CA certificate and key

### Generate CA (if needed)

```bash
nebula-cert ca -name "My Org" -duration 8760h
# Creates ca.crt and ca.key
mkdir -p nebula-ca
mv ca.crt ca.key nebula-ca/
```

### Run with Docker Compose

```bash
# Set your lighthouse external IP
export NEBULA_LIGHTHOUSE_EXTERNAL_IP=your.lighthouse.ip
export NEBULA_CA_DIR=./nebula-ca

docker compose up -d
```

### Run Locally (Development)

```bash
cd go-nebula-sidecar

# Set environment variables
export DB_HOST=localhost DB_PORT=5234 DB_NAME=kos-auth
export DB_USERNAME=postgres DB_PASSWORD=postgres
export NEBULA_LIGHTHOUSE_EXTERNAL_IP=1.2.3.4
export NEBULA_CA_CERT_PATH=./nebula-ca/ca.crt
export NEBULA_CA_KEY_PATH=./nebula-ca/ca.key
export AUTH_BACKEND_URL=http://localhost:8080

go run .
```

## API Reference

### Public Endpoints

#### `GET /health`

Health check for DB and auth backend connectivity.

```bash
curl http://localhost:8081/health
```

```json
{
  "status": "healthy",
  "database": "connected",
  "authBackend": "reachable"
}
```

#### `GET /api/nebula/crl`

Certificate Revocation List (cached 1 hour).

```bash
curl http://localhost:8081/api/nebula/crl
```

### Authenticated Endpoints

All require `Authorization: Bearer <token>` header.

#### `POST /api/nebula/generate-cert`

Generate a certificate for a laptop/device.

```bash
curl -X POST http://localhost:8081/api/nebula/generate-cert \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nodeType": "laptop",
    "nodeName": "jobin-macbook-m2",
    "deviceInfo": "MacBook Pro M2"
  }'
```

**Response:** Certificate PEM, private key PEM, ready-to-use config.yaml, IP assignment.

**Status codes:**
- `200` — Success
- `400` — Invalid request
- `401` — Invalid/expired token
- `409` — Certificate already exists for this node
- `429` — Rate limit exceeded (10/hour)

#### `POST /api/nebula/generate-ec2-cert`

Generate a certificate for an EC2 instance. Requires `admin` or `devops` role.

```bash
curl -X POST http://localhost:8081/api/nebula/generate-ec2-cert \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "uat",
    "instanceName": "uat-api-server-01"
  }'
```

#### `GET /api/nebula/list-certs`

List your certificates.

```bash
curl http://localhost:8081/api/nebula/list-certs \
  -H "Authorization: Bearer $TOKEN"

# Filter by type
curl "http://localhost:8081/api/nebula/list-certs?nodeType=laptop&limit=10" \
  -H "Authorization: Bearer $TOKEN"
```

#### `POST /api/nebula/revoke-cert`

Revoke a certificate.

```bash
curl -X POST http://localhost:8081/api/nebula/revoke-cert \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nodeName": "jobin-macbook-m2",
    "reason": "device-lost"
  }'
```

## IP Allocation

| Node Type   | Range               | Strategy                    |
|-------------|---------------------|-----------------------------|
| Lighthouse  | `192.168.100.1`     | Fixed                       |
| EC2         | `192.168.100.10-99` | Sequential per environment  |
| Laptop      | `192.168.100.100-199` | Hash of user ID (mod 100) |

## Configuration

See [`.env.sample`](.env.sample) for all environment variables.

| Variable | Default | Description |
|----------|---------|-------------|
| `AUTH_BACKEND_URL` | `http://auth-backend:8080` | Spring Boot auth backend URL |
| `DB_HOST` | `localhost` | PostgreSQL host (shared with auth backend) |
| `DB_NAME` | `kos-auth` | Database name |
| `NEBULA_LIGHTHOUSE_EXTERNAL_IP` | *required* | Public IP of Nebula lighthouse |
| `NEBULA_CA_CERT_PATH` | `/etc/nebula/ca.crt` | Path to CA certificate |
| `NEBULA_CA_KEY_PATH` | `/etc/nebula/ca.key` | Path to CA private key |
| `SERVICE_PORT` | `8081` | HTTP listen port |
| `CERT_VALIDITY_DAYS` | `365` | Certificate validity period |
| `TOKEN_CACHE_TTL_SECONDS` | `300` | Token validation cache TTL |

## Testing

```bash
cd go-nebula-sidecar
go test ./... -v
```

## Troubleshooting

**"failed to initialize nebula service"**
- Ensure CA cert and key files exist at the configured paths
- Verify the CA cert is not expired
- Check file permissions (must be readable by the service)

**"token validation failed"**
- Verify the auth backend is running and reachable
- Check `AUTH_BACKEND_URL` is correct
- Ensure the token validation endpoint exists on the auth backend

**"IP allocation failed"**
- Check if the IP range is exhausted (90 IPs for EC2, 100 for laptops)
- Verify database connectivity

**"database migration failed"**
- Ensure PostgreSQL is running and accessible
- Check `DB_HOST`, `DB_PORT`, `DB_NAME` configuration
- Verify the database user has CREATE TABLE permissions
