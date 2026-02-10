# Keycloak Production Setup Guide

Production Keycloak: `https://auth.keeplearningos.com`
Frontend: `https://forge.keeplearningos.com`

## Architecture

Everything lives in the **master** realm. No separate realm needed.

```
Frontend (kos-admin-web)  ──PKCE auth code──►  Keycloak (master realm)
        │                                            │
        │ Bearer JWT                                 │
        ▼                                            │
Backend (Spring Boot)  ──client_credentials──►  Keycloak (master realm / kos-admin-api)
```

### Clients in master realm

| Client | Type | Purpose |
|--------|------|---------|
| `kos-admin-api` | Confidential, service account | Backend admin API (full cross-realm access) |
| `kos-admin-web` | Public, PKCE | Frontend login (keycloak-js) |
| `kos-admin-console` | Confidential | Backend OAuth2 (Spring Security registration) |
| `admin-cli` | Public (default) | **Not modified** — used only for script authentication |

### Authentication flow

- Users authenticate via **GitHub IDP** (already configured in master)
- `SUPER_ADMIN` realm role gates admin access in backend and frontend

---

## Automated Setup

Run the setup script (idempotent — safe to re-run):

```bash
KEYCLOAK_ADMIN_PASS='<admin password>' ./scripts/setup-keycloak-production.sh
```

This creates all clients, roles, and grants. See the script output for secrets.

---

## Manual Setup

### Step 1: Create `kos-admin-api` Client (Backend Admin API)

1. In **master** realm → **Clients** → **Create client**
2. **General**:
   - Client ID: `kos-admin-api`
   - Name: `KOS Admin API`
3. **Capability config**:
   - Client authentication: **ON** (confidential)
   - Standard flow: **OFF**
   - Direct access grants: **OFF**
   - Service accounts roles: **ON**
4. Save
5. **Credentials** tab → copy the **Client secret**
6. **Service accounts roles** tab → **Assign role** → select `admin`

> **Save this**: `KEYCLOAK_ADMIN_CLIENT_SECRET=<secret>`

---

### Step 2: Create `kos-admin-web` Client (Frontend — Public)

Used by the Vue frontend (`keycloak-js`) for PKCE login.

1. In **master** realm → **Clients** → **Create client**
2. **General**:
   - Client ID: `kos-admin-web`
   - Name: `KOS Admin Web`
3. **Capability config**:
   - Client authentication: **OFF** (public client)
   - Standard flow: **ON** (Authorization Code + PKCE)
   - Direct access grants: **OFF**
4. **Access settings**:
   - Root URL: `https://forge.keeplearningos.com`
   - Home URL: `https://forge.keeplearningos.com`
   - Valid redirect URIs: `https://forge.keeplearningos.com/*`, `https://auth.keeplearningos.com/*`
   - Web origins: `https://forge.keeplearningos.com`, `https://auth.keeplearningos.com`
5. Save

---

### Step 3: Create `kos-admin-console` Client (Backend — Confidential)

Used by Spring Security OAuth2 registration (`saas-admin` in `application.yml`).

1. In **master** realm → **Clients** → **Create client**
2. **General**:
   - Client ID: `kos-admin-console`
   - Name: `KOS Admin Console`
3. **Capability config**:
   - Client authentication: **ON** (confidential)
   - Standard flow: **ON** (authorization_code)
   - Service accounts roles: **ON**
4. **Access settings**:
   - Root URL: `https://forge.keeplearningos.com`
   - Home URL: `https://forge.keeplearningos.com`
   - Valid redirect URIs: `https://forge.keeplearningos.com/*`, `https://auth.keeplearningos.com/*`
   - Web origins: `https://forge.keeplearningos.com`, `https://auth.keeplearningos.com`
5. Save
6. **Credentials** tab → copy the **Client secret**

> **Save this**: `SAAS_ADMIN_CLIENT_SECRET=<secret>`

---

### Step 4: Create `SUPER_ADMIN` Role

Required by `SuperAdminAuthorizationManager` (backend) and router guards (frontend).

1. In **master** realm → **Realm roles** → **Create role**
2. Role name: `SUPER_ADMIN`
3. Description: `Super administrator with full system access`
4. Save

---

### Step 5: Verify GitHub IDP

GitHub identity provider should already exist in master realm:

1. Go to **Identity providers** in the left sidebar
2. Confirm `github` is listed and enabled
3. Verify the **Redirect URI** is registered in your GitHub OAuth App settings:
   `https://auth.keeplearningos.com/realms/master/broker/github/endpoint`

---

### Step 6: Assign SUPER_ADMIN to Your User

After logging in via GitHub for the first time:

1. Go to **Users** → find your user
2. **Role mapping** tab → **Assign role** → select `SUPER_ADMIN`

---

## Woodpecker CI Secrets

### Backend secrets

```bash
woodpecker-cli repo secret add --repository dsjkeeplearning/kos-auth-backend \
  --name keycloak_base_url --value "https://auth.keeplearningos.com"

woodpecker-cli repo secret add --repository dsjkeeplearning/kos-auth-backend \
  --name keycloak_admin_client_id --value "kos-admin-api"

woodpecker-cli repo secret add --repository dsjkeeplearning/kos-auth-backend \
  --name keycloak_admin_client_secret --value "<kos-admin-api secret>"

woodpecker-cli repo secret add --repository dsjkeeplearning/kos-auth-backend \
  --name saas_admin_client_secret --value "<kos-admin-console secret>"

woodpecker-cli repo secret add --repository dsjkeeplearning/kos-auth-backend \
  --name keycloak_issuer_prefix --value "https://auth.keeplearningos.com/realms/"

woodpecker-cli repo secret add --repository dsjkeeplearning/kos-auth-backend \
  --name keycloak_saas_issuer_uri --value "https://auth.keeplearningos.com/realms/master"
```

### Frontend secrets (baked at Docker build time)

```bash
woodpecker-cli repo secret add --repository dsjkeeplearning/kos-auth-backend \
  --name vite_keycloak_url --value "https://auth.keeplearningos.com"

woodpecker-cli repo secret add --repository dsjkeeplearning/kos-auth-backend \
  --name vite_keycloak_realm --value "master"

woodpecker-cli repo secret add --repository dsjkeeplearning/kos-auth-backend \
  --name vite_keycloak_client_id --value "kos-admin-web"
```

---

## Environment Variable Reference

### Backend (`application.yml` mappings)

| Env Var | Config Property | Value | Purpose |
|---------|----------------|-------|---------|
| `KEYCLOAK_BASE_URL` | `keycloak.admin.base-url` | `https://auth.keeplearningos.com` | Admin REST API base URL |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `keycloak.admin.client-id` | `kos-admin-api` | Admin API client (replaces admin-cli) |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | `keycloak.admin.client-secret` | `<kos-admin-api secret>` | Admin API client credentials |
| `KEYCLOAK_ISSUER_PREFIX` | (JwtIssuerReactiveAuthenticationManagerResolver) | `https://auth.keeplearningos.com/realms/` | Multi-tenant JWT validation |
| `KEYCLOAK_SAAS_ISSUER_URI` | `spring.security.oauth2.client.provider.keycloak.issuer-uri` | `https://auth.keeplearningos.com/realms/master` | OAuth2 provider discovery |
| `SAAS_ADMIN_CLIENT_SECRET` | `spring.security.oauth2.client.registration.saas-admin.client-secret` | `<kos-admin-console secret>` | OAuth2 client credentials |

### Frontend (Vite build args — baked into Docker image)

| Env Var | Value | Purpose |
|---------|-------|---------|
| `VITE_KEYCLOAK_URL` | `https://auth.keeplearningos.com` | keycloak-js server URL |
| `VITE_KEYCLOAK_REALM` | `master` | keycloak-js realm |
| `VITE_KEYCLOAK_CLIENT_ID` | `kos-admin-web` | keycloak-js public client |

### deploy.yml env vars passed to backend container

```yaml
KEYCLOAK_BASE_URL:
  from_secret: keycloak_base_url
KEYCLOAK_ADMIN_CLIENT_ID:
  from_secret: keycloak_admin_client_id
KEYCLOAK_ADMIN_CLIENT_SECRET:
  from_secret: keycloak_admin_client_secret
KEYCLOAK_ISSUER_PREFIX:
  from_secret: keycloak_issuer_prefix
KEYCLOAK_SAAS_ISSUER_URI:
  from_secret: keycloak_saas_issuer_uri
SAAS_ADMIN_CLIENT_SECRET:
  from_secret: saas_admin_client_secret
```

---

## Verification

1. Run `./scripts/setup-keycloak-production.sh` or complete manual steps above
2. Update Woodpecker secrets with the output values
3. Trigger a release: `git tag release-v<version> && git push origin release-v<version>`
4. Visit `https://forge.keeplearningos.com`
5. Click login → redirects to Keycloak (GitHub IDP) → authenticate → redirected back with JWT
6. Check backend logs: `docker logs auth-backend` — no JWT/OAuth2 errors
