#!/bin/bash
set -e

# Assign SUPER_ADMIN realm role to a user in the local Keycloak instance.
# If the user doesn't exist, creates them with a temporary password (local dev only).
#
# Usage: ./scripts/assign-super-admin.sh <email> [password]
#   password  Optional. If provided and user doesn't exist, creates a local user.
#             Defaults to "Admin1234!" for local dev bootstrapping.

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8088}"
REALM="${KEYCLOAK_REALM:-master}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
EMAIL="${1}"
PASSWORD="${2:-Admin1234!}"

if [ -z "$EMAIL" ]; then
  echo "Usage: $0 <email> [password]"
  exit 1
fi

echo "Keycloak: $KEYCLOAK_URL (realm: $REALM)"

# Get admin token
TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli&username=$ADMIN_USER&password=$ADMIN_PASS&grant_type=password" \
  | python3 -c "
import sys, json
d = json.load(sys.stdin)
t = d.get('access_token')
if not t:
    raise SystemExit('Keycloak login failed: ' + d.get('error_description', 'unknown error'))
print(t)
")

# Look up user by email
USER_JSON=$(curl -s -H "Authorization: Bearer $TOKEN" \
  "$KEYCLOAK_URL/admin/realms/$REALM/users?email=$EMAIL&exact=true")

USER_ID=$(echo "$USER_JSON" | python3 -c "
import sys, json
users = json.load(sys.stdin)
print(users[0]['id'] if users else '')
")

# Create user if not found
if [ -z "$USER_ID" ]; then
  echo "User not found — creating local user: $EMAIL"

  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "$KEYCLOAK_URL/admin/realms/$REALM/users" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"email\": \"$EMAIL\",
      \"username\": \"$EMAIL\",
      \"enabled\": true,
      \"emailVerified\": true,
      \"credentials\": [{
        \"type\": \"password\",
        \"value\": \"$PASSWORD\",
        \"temporary\": false
      }]
    }")

  if [ "$STATUS" != "201" ]; then
    echo "Error: failed to create user (HTTP $STATUS)"
    exit 1
  fi

  # Re-fetch user ID
  USER_ID=$(curl -s -H "Authorization: Bearer $TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM/users?email=$EMAIL&exact=true" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

  echo "Created user — login with: $EMAIL / $PASSWORD"
fi

echo "User ID: $USER_ID"

# Get SUPER_ADMIN role definition
ROLE=$(curl -s -H "Authorization: Bearer $TOKEN" \
  "$KEYCLOAK_URL/admin/realms/$REALM/roles/SUPER_ADMIN")

ROLE_NAME=$(echo "$ROLE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('name',''))")
if [ -z "$ROLE_NAME" ]; then
  echo "Error: SUPER_ADMIN role not found in realm '$REALM'."
  exit 1
fi

# Assign role
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  "$KEYCLOAK_URL/admin/realms/$REALM/users/$USER_ID/role-mappings/realm" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "[$ROLE]")

if [ "$STATUS" = "204" ]; then
  echo "Done — SUPER_ADMIN assigned to $EMAIL"
else
  echo "Error: unexpected status $STATUS"
  exit 1
fi
