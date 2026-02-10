#!/usr/bin/env bash
# Keycloak Production Setup Script
# Idempotent — safe to re-run. Uses Keycloak Admin REST API.
# Requires: curl, jq
#
# Everything lives in the master realm:
#   kos-admin-api      (confidential, service account with admin role — backend admin API)
#   kos-admin-web      (public, PKCE — frontend login)
#   kos-admin-console  (confidential — backend OAuth2)
#   SUPER_ADMIN role
#   GitHub IDP         (assumed to already exist)
#
# Usage:
#   ./scripts/setup-keycloak-production.sh
#
# Environment variables (prompted if not set):
#   KEYCLOAK_URL          - Keycloak base URL (default: http://auth.keeplearningos.com)
#   KEYCLOAK_ADMIN_USER   - Admin username (default: admin)
#   KEYCLOAK_ADMIN_PASS   - Admin password (required)

set -euo pipefail

# ── Colors ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${CYAN}▸${NC} $*"; }
ok()    { echo -e "${GREEN}✓${NC} $*"; }
warn()  { echo -e "${YELLOW}⚠${NC} $*"; }
fail()  { echo -e "${RED}✗${NC} $*" >&2; exit 1; }

# ── Dependency check ──────────────────────────────────────────────────────────
command -v curl >/dev/null || fail "curl is required"
command -v jq   >/dev/null || fail "jq is required"

# ── Configuration ─────────────────────────────────────────────────────────────
KEYCLOAK_URL="${KEYCLOAK_URL:-http://auth.keeplearningos.com}"
KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"

if [[ -z "${KEYCLOAK_ADMIN_PASS:-}" ]]; then
  read -rsp "Keycloak admin password: " KEYCLOAK_ADMIN_PASS; echo
fi

REALM="master"
ADMIN_API_CLIENT_ID="kos-admin-api"
FRONTEND_CLIENT_ID="kos-admin-web"
BACKEND_CLIENT_ID="kos-admin-console"
ROLE_NAME="SUPER_ADMIN"
FRONTEND_URL="https://forge.keeplearningos.com"

# ── Helper: Keycloak API call ─────────────────────────────────────────────────
kc_api() {
  local method="$1" path="$2"; shift 2
  local url="${KEYCLOAK_URL}${path}"
  local response http_code body

  if ! response=$(curl -s --connect-timeout 10 --max-time 30 -w "\n%{http_code}" \
    -X "$method" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    "$@" "$url"); then
    fail "curl failed for ${method} ${path} — check network connectivity to ${KEYCLOAK_URL}"
  fi

  http_code=$(echo "$response" | tail -1)
  body=$(echo "$response" | sed '$d')

  echo "$http_code"
  echo "$body"
}

kc_get()  { kc_api GET  "$@"; }
kc_post() { local path="$1" data="$2"; kc_api POST "$path" -d "$data"; }
kc_put()  { local path="$1" data="$2"; kc_api PUT  "$path" -d "$data"; }

parse_response() {
  HTTP_CODE=$(echo "$1" | head -1)
  BODY=$(echo "$1" | tail -n +2)
}

# ── Step 0: Connectivity check + Authenticate ────────────────────────────────
info "Checking connectivity to ${KEYCLOAK_URL}..."

HTTP_STATUS=$(curl -s --connect-timeout 10 --max-time 15 -o /dev/null -w "%{http_code}" "${KEYCLOAK_URL}" 2>&1)
if [[ -z "$HTTP_STATUS" ]] || [[ "$HTTP_STATUS" == "000" ]]; then
  fail "Cannot reach ${KEYCLOAK_URL} — check DNS, firewall, or VPN"
fi
if [[ "$HTTP_STATUS" == "301" ]] || [[ "$HTTP_STATUS" == "302" ]]; then
  KEYCLOAK_URL="${KEYCLOAK_URL/http:/https:}"
  warn "HTTP redirects to HTTPS — using ${KEYCLOAK_URL}"
fi
ok "Keycloak is reachable"

info "Authenticating as ${KEYCLOAK_ADMIN_USER}..."

if ! TOKEN_RESPONSE=$(curl -s --connect-timeout 10 --max-time 30 -X POST \
  "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=${KEYCLOAK_ADMIN_USER}" \
  -d "password=${KEYCLOAK_ADMIN_PASS}"); then
  fail "curl failed — connection error to token endpoint"
fi

if ! echo "$TOKEN_RESPONSE" | jq empty 2>/dev/null; then
  echo -e "${RED}Response is not JSON. Raw response (first 500 chars):${NC}" >&2
  echo "$TOKEN_RESPONSE" | head -c 500 >&2
  echo "" >&2
  fail "Token endpoint did not return JSON — check if ${KEYCLOAK_URL}/realms/master is valid"
fi

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token // empty')

# If auth failed, admin-cli may be confidential — retry with client secret
if [[ -z "$ACCESS_TOKEN" ]]; then
  ERROR_MSG=$(echo "$TOKEN_RESPONSE" | jq -r '.error_description // empty')
  if [[ "$ERROR_MSG" == *"Invalid client"* ]] || [[ "$ERROR_MSG" == *"client credentials"* ]]; then
    warn "admin-cli appears to be confidential — retrying with client secret"
    if [[ -z "${ADMIN_CLI_SECRET:-}" ]]; then
      read -rsp "admin-cli client secret: " ADMIN_CLI_SECRET; echo
    fi
    if ! TOKEN_RESPONSE=$(curl -s --connect-timeout 10 --max-time 30 -X POST \
      "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
      -d "grant_type=password&client_id=admin-cli&client_secret=${ADMIN_CLI_SECRET}&username=${KEYCLOAK_ADMIN_USER}&password=${KEYCLOAK_ADMIN_PASS}"); then
      fail "curl failed on retry"
    fi
    ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token // empty')
  fi
  if [[ -z "$ACCESS_TOKEN" ]]; then
    ERROR_DESC=$(echo "$TOKEN_RESPONSE" | jq -r '.error_description // .error // "unknown error"')
    fail "Authentication failed: ${ERROR_DESC}"
  fi
fi
ok "Authenticated"

# ── Step 1: Create kos-admin-api client (backend admin API) ──────────────────
info "Creating client: ${ADMIN_API_CLIENT_ID}..."

parse_response "$(kc_get "/admin/realms/${REALM}/clients?clientId=${ADMIN_API_CLIENT_ID}")"
if [[ "$HTTP_CODE" != "200" ]]; then
  fail "Failed to query clients (HTTP $HTTP_CODE): $BODY"
fi

EXISTING=$(echo "$BODY" | jq 'if type == "array" then length else 0 end')
if [[ "$EXISTING" -gt 0 ]]; then
  ADMIN_API_UUID=$(echo "$BODY" | jq -r '.[0].id')
  warn "Client '${ADMIN_API_CLIENT_ID}' already exists — retrieving secret"
else
  parse_response "$(kc_post "/admin/realms/${REALM}/clients" "{
    \"clientId\": \"${ADMIN_API_CLIENT_ID}\",
    \"name\": \"KOS Admin API\",
    \"description\": \"Backend service account for Keycloak Admin REST API\",
    \"enabled\": true,
    \"publicClient\": false,
    \"clientAuthenticatorType\": \"client-secret\",
    \"serviceAccountsEnabled\": true,
    \"standardFlowEnabled\": false,
    \"directAccessGrantsEnabled\": false
  }")"
  [[ "$HTTP_CODE" == "201" ]] || fail "Failed to create ${ADMIN_API_CLIENT_ID} (HTTP $HTTP_CODE): $BODY"

  parse_response "$(kc_get "/admin/realms/${REALM}/clients?clientId=${ADMIN_API_CLIENT_ID}")"
  ADMIN_API_UUID=$(echo "$BODY" | jq -r '.[0].id')
  ok "Client '${ADMIN_API_CLIENT_ID}' created"
fi

parse_response "$(kc_get "/admin/realms/${REALM}/clients/${ADMIN_API_UUID}/client-secret")"
ADMIN_API_SECRET=$(echo "$BODY" | jq -r '.value // empty')
[[ -n "$ADMIN_API_SECRET" ]] || fail "Could not retrieve ${ADMIN_API_CLIENT_ID} secret"

# Grant admin role to service account
info "Granting admin role to ${ADMIN_API_CLIENT_ID} service account..."

parse_response "$(kc_get "/admin/realms/${REALM}/clients/${ADMIN_API_UUID}/service-account-user")"
[[ "$HTTP_CODE" == "200" ]] || fail "Failed to get service account user (HTTP $HTTP_CODE): $BODY"
SA_USER_ID=$(echo "$BODY" | jq -r '.id // empty')
[[ -n "$SA_USER_ID" ]] || fail "Could not find service account user"

parse_response "$(kc_get "/admin/realms/${REALM}/roles/admin")"
[[ "$HTTP_CODE" == "200" ]] || fail "Failed to get admin role (HTTP $HTTP_CODE): $BODY"
ADMIN_ROLE_JSON="$BODY"

parse_response "$(kc_api POST "/admin/realms/${REALM}/users/${SA_USER_ID}/role-mappings/realm" -d "[${ADMIN_ROLE_JSON}]")"
if [[ "$HTTP_CODE" == "204" ]]; then
  ok "Admin role assigned to service account"
elif [[ "$HTTP_CODE" == "409" ]]; then
  warn "Admin role already assigned — skipping"
else
  fail "Failed to assign admin role (HTTP $HTTP_CODE): $BODY"
fi

# ── Step 2: Create kos-admin-web client (frontend, public PKCE) ──────────────
info "Creating client: ${FRONTEND_CLIENT_ID}..."

parse_response "$(kc_get "/admin/realms/${REALM}/clients?clientId=${FRONTEND_CLIENT_ID}")"
EXISTING=$(echo "$BODY" | jq 'if type == "array" then length else 0 end')
if [[ "$EXISTING" -gt 0 ]]; then
  warn "Client '${FRONTEND_CLIENT_ID}' already exists — skipping"
else
  parse_response "$(kc_post "/admin/realms/${REALM}/clients" "{
    \"clientId\": \"${FRONTEND_CLIENT_ID}\",
    \"name\": \"KOS Admin Web\",
    \"enabled\": true,
    \"publicClient\": true,
    \"standardFlowEnabled\": true,
    \"directAccessGrantsEnabled\": false,
    \"rootUrl\": \"${FRONTEND_URL}\",
    \"baseUrl\": \"${FRONTEND_URL}\",
    \"redirectUris\": [\"${FRONTEND_URL}/*\"],
    \"webOrigins\": [\"${FRONTEND_URL}\"],
    \"attributes\": {
      \"pkce.code.challenge.method\": \"S256\"
    }
  }")"
  [[ "$HTTP_CODE" == "201" ]] || fail "Failed to create ${FRONTEND_CLIENT_ID} (HTTP $HTTP_CODE): $BODY"
  ok "Client '${FRONTEND_CLIENT_ID}' created (public, PKCE)"
fi

# ── Step 3: Create kos-admin-console client (backend OAuth2, confidential) ───
info "Creating client: ${BACKEND_CLIENT_ID}..."

parse_response "$(kc_get "/admin/realms/${REALM}/clients?clientId=${BACKEND_CLIENT_ID}")"
EXISTING=$(echo "$BODY" | jq 'if type == "array" then length else 0 end')
if [[ "$EXISTING" -gt 0 ]]; then
  BACKEND_UUID=$(echo "$BODY" | jq -r '.[0].id')
  warn "Client '${BACKEND_CLIENT_ID}' already exists — retrieving secret"
else
  parse_response "$(kc_post "/admin/realms/${REALM}/clients" "{
    \"clientId\": \"${BACKEND_CLIENT_ID}\",
    \"name\": \"KOS Admin Console\",
    \"enabled\": true,
    \"publicClient\": false,
    \"clientAuthenticatorType\": \"client-secret\",
    \"standardFlowEnabled\": true,
    \"serviceAccountsEnabled\": true,
    \"directAccessGrantsEnabled\": false,
    \"rootUrl\": \"${FRONTEND_URL}\",
    \"baseUrl\": \"${FRONTEND_URL}\",
    \"redirectUris\": [\"${FRONTEND_URL}/*\"],
    \"webOrigins\": [\"${FRONTEND_URL}\"]
  }")"
  [[ "$HTTP_CODE" == "201" ]] || fail "Failed to create ${BACKEND_CLIENT_ID} (HTTP $HTTP_CODE): $BODY"

  parse_response "$(kc_get "/admin/realms/${REALM}/clients?clientId=${BACKEND_CLIENT_ID}")"
  BACKEND_UUID=$(echo "$BODY" | jq -r '.[0].id')
  ok "Client '${BACKEND_CLIENT_ID}' created (confidential + service accounts)"
fi

parse_response "$(kc_get "/admin/realms/${REALM}/clients/${BACKEND_UUID}/client-secret")"
BACKEND_CLIENT_SECRET=$(echo "$BODY" | jq -r '.value // empty')
[[ -n "$BACKEND_CLIENT_SECRET" ]] || fail "Could not retrieve ${BACKEND_CLIENT_ID} secret"

# ── Step 4: Create SUPER_ADMIN role ──────────────────────────────────────────
info "Creating realm role: ${ROLE_NAME}..."

parse_response "$(kc_get "/admin/realms/${REALM}/roles/${ROLE_NAME}")"
if [[ "$HTTP_CODE" == "200" ]]; then
  warn "Role '${ROLE_NAME}' already exists — skipping"
else
  parse_response "$(kc_post "/admin/realms/${REALM}/roles" "{
    \"name\": \"${ROLE_NAME}\",
    \"description\": \"Super administrator with full system access\"
  }")"
  [[ "$HTTP_CODE" == "201" ]] || fail "Failed to create role (HTTP $HTTP_CODE): $BODY"
  ok "Role '${ROLE_NAME}' created"
fi

# ── Step 5: Verify GitHub IDP exists ─────────────────────────────────────────
info "Checking GitHub identity provider..."

parse_response "$(kc_get "/admin/realms/${REALM}/identity-provider/instances/github")"
if [[ "$HTTP_CODE" == "200" ]]; then
  ok "GitHub IDP exists"
else
  warn "GitHub IDP not found in master realm — you'll need to configure it manually"
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN} Setup Complete!  (all in master realm)${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}Next step:${NC} Assign SUPER_ADMIN role to your user:"
echo -e "  ${KEYCLOAK_URL}/admin/master/console/#/master/users"
echo ""
echo -e "${CYAN}── Woodpecker CI Secrets ──${NC}"
echo ""
echo -e "  ${CYAN}keycloak_base_url${NC}              = ${KEYCLOAK_URL}"
echo -e "  ${CYAN}keycloak_admin_client_id${NC}       = ${ADMIN_API_CLIENT_ID}"
echo -e "  ${CYAN}keycloak_admin_client_secret${NC}   = ${ADMIN_API_SECRET}"
echo -e "  ${CYAN}saas_admin_client_secret${NC}       = ${BACKEND_CLIENT_SECRET}"
echo -e "  ${CYAN}keycloak_issuer_prefix${NC}         = ${KEYCLOAK_URL}/realms/"
echo -e "  ${CYAN}keycloak_saas_issuer_uri${NC}       = ${KEYCLOAK_URL}/realms/${REALM}"
echo -e "  ${CYAN}vite_keycloak_url${NC}              = ${KEYCLOAK_URL}"
echo -e "  ${CYAN}vite_keycloak_realm${NC}            = ${REALM}"
echo -e "  ${CYAN}vite_keycloak_client_id${NC}        = ${FRONTEND_CLIENT_ID}"
echo ""
echo "To update Woodpecker secrets:"
echo ""
cat <<CMDS
  woodpecker-cli secret update --repository dsjkeeplearning/kos-auth-backend \\
    --name keycloak_base_url --value "${KEYCLOAK_URL}"

  woodpecker-cli secret update --repository dsjkeeplearning/kos-auth-backend \\
    --name keycloak_admin_client_id --value "${ADMIN_API_CLIENT_ID}"

  woodpecker-cli secret update --repository dsjkeeplearning/kos-auth-backend \\
    --name keycloak_admin_client_secret --value "${ADMIN_API_SECRET}"

  woodpecker-cli secret update --repository dsjkeeplearning/kos-auth-backend \\
    --name saas_admin_client_secret --value "${BACKEND_CLIENT_SECRET}"

  woodpecker-cli secret update --repository dsjkeeplearning/kos-auth-backend \\
    --name keycloak_issuer_prefix --value "${KEYCLOAK_URL}/realms/"

  woodpecker-cli secret update --repository dsjkeeplearning/kos-auth-backend \\
    --name keycloak_saas_issuer_uri --value "${KEYCLOAK_URL}/realms/${REALM}"

  woodpecker-cli secret update --repository dsjkeeplearning/kos-auth-backend \\
    --name vite_keycloak_url --value "${KEYCLOAK_URL}"

  woodpecker-cli secret update --repository dsjkeeplearning/kos-auth-backend \\
    --name vite_keycloak_realm --value "${REALM}"

  woodpecker-cli secret update --repository dsjkeeplearning/kos-auth-backend \\
    --name vite_keycloak_client_id --value "${FRONTEND_CLIENT_ID}"
CMDS
