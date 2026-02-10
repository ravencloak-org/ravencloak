# Keycloak Production Setup Guide

Production Keycloak: `https://keycloak.keeplearningos.com`

## Architecture

```
Frontend (kos-admin-web)  ──PKCE auth code──►  Keycloak (saas-admin realm)
        │                                            │
        │ Bearer JWT                                 │
        ▼                                            │
Backend (Spring Boot)  ──client_credentials──►  Keycloak (master realm / admin-cli)
```

- **`saas-admin` realm** — user-facing auth (frontend login, SUPER_ADMIN role)
- **`master` realm** — admin API access (backend manages all realms via `admin-cli`)

---

## Step 1: Configure `admin-cli` in Master Realm

The backend's `KeycloakAdminClient` uses client credentials on `admin-cli` to call the Keycloak Admin REST API.

1. Login to Keycloak Admin Console → **master** realm
2. Go to **Clients** → `admin-cli`
3. **Settings** tab:
   - Client authentication: **ON** (makes it confidential)
   - Service accounts roles: **ON**
   - Save
4. **Credentials** tab → copy the **Client secret**
5. **Service accounts roles** tab → verify it has `admin` realm role (should be default)

> **Save this**: `KEYCLOAK_ADMIN_CLIENT_SECRET=<secret>`

---

## Step 2: Create `saas-admin` Realm

1. Click realm dropdown (top-left) → **Create realm**
2. Realm name: `saas-admin`
3. Enabled: **ON**
4. Save

Optional: Under **Realm settings** → **Login** tab, enable:
- User registration (if you want self-signup)
- Email as username
- Login with email

---

## Step 3: Create `kos-admin-web` Client (Frontend — Public)

Used by the Vue frontend (`keycloak-js`) for PKCE login.

1. In `saas-admin` realm → **Clients** → **Create client**
2. **General**:
   - Client ID: `kos-admin-web`
   - Name: `KOS Admin Web`
3. **Capability config**:
   - Client authentication: **OFF** (public client)
   - Standard flow: **ON** (Authorization Code)
   - Direct access grants: **OFF**
4. **Access settings**:
   - Root URL: `https://auth.keeplearningos.com`
   - Valid redirect URIs: `https://auth.keeplearningos.com/*`
   - Valid post logout redirect URIs: `https://auth.keeplearningos.com/*`
   - Web origins: `https://auth.keeplearningos.com`
5. Save

---

## Step 4: Create `kos-admin-console` Client (Backend — Confidential)

Used by Spring Security OAuth2 registration (`saas-admin` in `application.yml`).

1. In `saas-admin` realm → **Clients** → **Create client**
2. **General**:
   - Client ID: `kos-admin-console`
   - Name: `KOS Admin Console`
3. **Capability config**:
   - Client authentication: **ON** (confidential)
   - Standard flow: **ON** (authorization_code)
   - Service accounts roles: **ON**
4. **Access settings**:
   - Valid redirect URIs: `https://auth.keeplearningos.com/*`
   - Web origins: `https://auth.keeplearningos.com`
5. Save
6. **Credentials** tab → copy the **Client secret**

> **Save this**: `SAAS_ADMIN_CLIENT_SECRET=<secret>`

---

## Step 5: Create `SUPER_ADMIN` Role

Required by `SuperAdminAuthorizationManager` (backend) and router guards (frontend).

1. In `saas-admin` realm → **Realm roles** → **Create role**
2. Role name: `SUPER_ADMIN`
3. Save

---

## Step 6: Create Admin User

1. In `saas-admin` realm → **Users** → **Add user**
2. Fill in: email, first name, last name
3. Email verified: **ON**
4. Save
5. **Credentials** tab → **Set password** → toggle "Temporary" OFF
6. **Role mapping** tab → **Assign role** → select `SUPER_ADMIN`

---

## Step 7: Update Woodpecker CI Secrets

### New secrets to add

```bash
# admin-cli secret from master realm (Step 1)
woodpecker-cli secret add --repository dsjkeeplearning/kos-auth-backend \
  --name keycloak_admin_client_secret --value "<admin-cli secret>"

# Keycloak base URL for admin API
woodpecker-cli secret add --repository dsjkeeplearning/kos-auth-backend \
  --name keycloak_base_url --value "https://keycloak.keeplearningos.com"
```

### Existing secrets to verify/update

```bash
# kos-admin-console secret from saas-admin realm (Step 4)
woodpecker-cli secret update --repository dsjkeeplearning/kos-auth-backend \
  --name saas_admin_client_secret --value "<kos-admin-console secret>"

# These should already be set, verify values:
# keycloak_issuer_prefix = https://keycloak.keeplearningos.com/realms/
# keycloak_saas_issuer_uri = https://keycloak.keeplearningos.com/realms/saas-admin
# vite_keycloak_url = https://keycloak.keeplearningos.com
# vite_keycloak_realm = saas-admin
# vite_keycloak_client_id = kos-admin-web
```

---

## Step 8: Fix `deploy.yml` (REQUIRED)

**`deploy.yml` is missing two env vars** that `KeycloakAdminClient` needs. The backend currently falls back to `http://localhost:8088` which won't work in production.

Add to the `docker run` command in the deploy step:

```
-e KEYCLOAK_BASE_URL="$KEYCLOAK_BASE_URL"
-e KEYCLOAK_ADMIN_CLIENT_SECRET="$KEYCLOAK_ADMIN_CLIENT_SECRET"
```

And add to the `environment:` block:

```yaml
KEYCLOAK_BASE_URL:
  from_secret: keycloak_base_url
KEYCLOAK_ADMIN_CLIENT_SECRET:
  from_secret: keycloak_admin_client_secret
```

---

## Environment Variable Reference

### Backend (`application.yml` mappings)

| Env Var | Config Property | Value | Purpose |
|---------|----------------|-------|---------|
| `KEYCLOAK_BASE_URL` | `keycloak.admin.base-url` | `https://keycloak.keeplearningos.com` | Admin REST API base URL |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | `keycloak.admin.client-secret` | `<admin-cli secret>` | master realm client credentials |
| `KEYCLOAK_ISSUER_PREFIX` | (used by `JwtIssuerReactiveAuthenticationManagerResolver`) | `https://keycloak.keeplearningos.com/realms/` | Multi-tenant JWT validation |
| `KEYCLOAK_SAAS_ISSUER_URI` | `spring.security.oauth2.client.provider.keycloak.issuer-uri` | `https://keycloak.keeplearningos.com/realms/saas-admin` | OAuth2 provider discovery |
| `SAAS_ADMIN_CLIENT_SECRET` | `spring.security.oauth2.client.registration.saas-admin.client-secret` | `<kos-admin-console secret>` | OAuth2 client credentials |

### Frontend (Vite build args — baked into Docker image)

| Env Var | Value | Purpose |
|---------|-------|---------|
| `VITE_KEYCLOAK_URL` | `https://keycloak.keeplearningos.com` | keycloak-js server URL |
| `VITE_KEYCLOAK_REALM` | `saas-admin` | keycloak-js realm |
| `VITE_KEYCLOAK_CLIENT_ID` | `kos-admin-web` | keycloak-js public client |

---

## Verification

1. Complete Steps 1–8 above
2. Trigger a release: `git tag release-v<version> && git push --tags`
3. After deploy, visit `https://auth.keeplearningos.com`
4. Click login → redirects to Keycloak → authenticate → redirected back with JWT
5. Check backend logs: `docker logs auth-backend` — no JWT/OAuth2 errors
