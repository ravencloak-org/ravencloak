#!/bin/bash
set -e

# Assign SUPER_ADMIN realm role to a user in the local Keycloak instance.
# Usage: ./scripts/assign-super-admin.sh <email>

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8088}"
REALM="${KEYCLOAK_REALM:-master}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
EMAIL="${1}"

if [ -z "$EMAIL" ]; then
  echo "Usage: $0 <email>"
  exit 1
fi

echo "Keycloak: $KEYCLOAK_URL (realm: $REALM)"
echo "Assigning SUPER_ADMIN to: $EMAIL"

# Get admin token
TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli&username=$ADMIN_USER&password=$ADMIN_PASS&grant_type=password" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('access_token') or (_ for _ in ()).throw(Exception(d.get('error_description','Login failed'))))")

# Look up user by email
USER_JSON=$(curl -s -H "Authorization: Bearer $TOKEN" \
  "$KEYCLOAK_URL/admin/realms/$REALM/users?email=$EMAIL&exact=true")

USER_ID=$(echo "$USER_JSON" | python3 -c "
import sys, json
users = json.load(sys.stdin)
if not users:
    raise SystemExit(f'No user found with email: $EMAIL')
print(users[0]['id'])
")

echo "User ID: $USER_ID"

# Get SUPER_ADMIN role definition
ROLE=$(curl -s -H "Authorization: Bearer $TOKEN" \
  "$KEYCLOAK_URL/admin/realms/$REALM/roles/SUPER_ADMIN")

ROLE_NAME=$(echo "$ROLE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('name',''))")
if [ -z "$ROLE_NAME" ]; then
  echo "Error: SUPER_ADMIN role not found in realm '$REALM'. Create it first."
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
