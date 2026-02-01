#!/bin/bash
set -e

# Woodpecker CI - Add All Secrets (S3 Cache + App Config)
# Usage: ./scripts/add-woodpecker-secrets.sh <path-to-env-file>
#
# Secrets added:
#   S3 Build Cache: s3_build_cache_bucket, s3_build_cache_region, etc.
#   App Config: keycloak_issuer_prefix, db_host, db_password, etc.

ENV_FILE="${1:-.env}"

# Configuration
WOODPECKER_SERVER="https://drone.keeplearningos.com"
REPO="${WOODPECKER_REPO:-dsjkeeplearning/kos-auth-backend}"

# Check if env file exists
if [ ! -f "$ENV_FILE" ]; then
    echo "Error: Env file not found: $ENV_FILE"
    echo "Usage: $0 <path-to-env-file>"
    exit 1
fi

# Check if woodpecker-cli is connected
if ! woodpecker-cli context ls &>/dev/null; then
    echo "Woodpecker CLI not configured. Running setup..."
    woodpecker-cli setup --server "$WOODPECKER_SERVER"
fi

echo "Using Woodpecker context:"
woodpecker-cli context ls
echo ""

# Load values from env file using a more robust method
echo "Loading values from $ENV_FILE..."
while IFS='=' read -r key value; do
    # Skip comments and empty lines
    [[ "$key" =~ ^#.*$ ]] && continue
    [[ -z "$key" ]] && continue
    # Remove leading/trailing whitespace from key
    key=$(echo "$key" | xargs)
    # Remove surrounding quotes from value if present
    value="${value%\"}"
    value="${value#\"}"
    value="${value%\'}"
    value="${value#\'}"
    # Export S3 cache and app variables
    if [[ "$key" == S3_BUILD_CACHE_* ]] || [[ "$key" == KEYCLOAK_* ]] || [[ "$key" == SAAS_* ]] || [[ "$key" == DB_* ]] || [[ "$key" == SPRING_* ]]; then
        export "$key=$value"
        echo "  Loaded: $key"
    fi
done < "$ENV_FILE"

# Verify required variables
echo ""
echo "Verifying loaded values..."

# S3 cache variables
s3_vars=("S3_BUILD_CACHE_BUCKET" "S3_BUILD_CACHE_REGION" "S3_BUILD_CACHE_ACCESS_KEY_ID" "S3_BUILD_CACHE_SECRET_KEY" "S3_BUILD_CACHE_ENDPOINT")
# App variables
app_vars=("KEYCLOAK_ISSUER_PREFIX" "KEYCLOAK_SAAS_ISSUER_URI" "SAAS_ADMIN_CLIENT_SECRET" "DB_HOST" "DB_PORT" "DB_NAME" "DB_USERNAME" "SPRING_PROFILES_ACTIVE")
# DB_PASSWORD can be empty

all_vars=("${s3_vars[@]}" "${app_vars[@]}")
for var in "${all_vars[@]}"; do
    val="${!var}"
    if [ -z "$val" ]; then
        echo "Warning: $var is not set (may be optional)"
    elif [[ "$var" == *"SECRET"* ]] || [[ "$var" == *"PASSWORD"* ]] || [[ "$var" == *"ACCESS_KEY"* ]]; then
        echo "  $var: ${val:0:4}****** (length: ${#val})"
    else
        echo "  $var: $val"
    fi
done

echo "Adding secrets to Woodpecker CI..."
echo "Server: $WOODPECKER_SERVER"
echo "Repository: $REPO"
echo ""

# Function to trim whitespace
trim() {
    local var="$*"
    var="${var#"${var%%[![:space:]]*}"}"
    var="${var%"${var##*[![:space:]]}"}"
    echo -n "$var"
}

# Function to add a secret
add_secret() {
    local name=$1
    local value=$(trim "$2")

    # Skip empty values
    if [ -z "$value" ]; then
        echo "Skipping secret: $name (empty value)"
        return 0
    fi

    echo "Adding secret: $name"
    if ! woodpecker-cli repo secret add \
        --repository "$REPO" \
        --name "$name" \
        --value "$value" \
        --event push \
        --event pull_request \
        --event tag \
        --event manual \
        --event deployment \
        --event release \
        2>/dev/null; then
        woodpecker-cli repo secret update \
            --repository "$REPO" \
            --name "$name" \
            --value "$value" \
            --event push \
            --event pull_request \
            --event tag \
            --event manual \
            --event deployment \
            --event release
    fi
}

# Add all S3 build cache secrets
echo "Adding S3 build cache secrets..."
add_secret "s3_build_cache_bucket" "$S3_BUILD_CACHE_BUCKET"
add_secret "s3_build_cache_region" "$S3_BUILD_CACHE_REGION"
add_secret "s3_build_cache_access_key_id" "$S3_BUILD_CACHE_ACCESS_KEY_ID"
add_secret "s3_build_cache_secret_key" "$S3_BUILD_CACHE_SECRET_KEY"
add_secret "s3_build_cache_endpoint" "$S3_BUILD_CACHE_ENDPOINT"

# Add app configuration secrets
echo ""
echo "Adding app configuration secrets..."
add_secret "keycloak_issuer_prefix" "$KEYCLOAK_ISSUER_PREFIX"
add_secret "keycloak_saas_issuer_uri" "$KEYCLOAK_SAAS_ISSUER_URI"
add_secret "saas_admin_client_secret" "$SAAS_ADMIN_CLIENT_SECRET"
add_secret "db_host" "$DB_HOST"
add_secret "db_port" "$DB_PORT"
add_secret "db_name" "$DB_NAME"
add_secret "db_username" "$DB_USERNAME"
add_secret "db_password" "${DB_PASSWORD:-}"
add_secret "spring_profiles_active" "${SPRING_PROFILES_ACTIVE:-prod}"

echo ""
echo "Done! Verifying secrets..."
woodpecker-cli repo secret ls --repository "$REPO"
