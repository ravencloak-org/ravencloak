#!/bin/bash
set -e

# Woodpecker CI - Add Build Cache Secrets
# Usage: ./scripts/add-woodpecker-secrets.sh <path-to-env-file>

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

# Load values from env file
echo "Loading values from $ENV_FILE..."
export $(grep -v '^#' "$ENV_FILE" | grep -v '^$' | xargs)

# Verify required S3 cache variables
required_vars=("S3_BUILD_CACHE_BUCKET" "S3_BUILD_CACHE_REGION" "S3_BUILD_CACHE_ACCESS_KEY_ID" "S3_BUILD_CACHE_SECRET_KEY" "S3_BUILD_CACHE_ENDPOINT")
for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        echo "Error: $var is not set. Please set it in .env or as environment variable."
        exit 1
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

    echo "Adding secret: $name"
    woodpecker-cli repo secret add \
        --repository "$REPO" \
        --name "$name" \
        --value "$value" \
        --event push \
        --event pull_request \
        --event tag \
        --event manual \
        --event deployment \
        --event release \
        2>/dev/null || woodpecker-cli repo secret update \
        --repository "$REPO" \
        --name "$name" \
        --value "$value" \
        --event push \
        --event pull_request \
        --event tag \
        --event manual \
        --event deployment \
        --event release
}

# Add all S3 build cache secrets
add_secret "s3_build_cache_bucket" "$S3_BUILD_CACHE_BUCKET"
add_secret "s3_build_cache_region" "$S3_BUILD_CACHE_REGION"
add_secret "s3_build_cache_access_key_id" "$S3_BUILD_CACHE_ACCESS_KEY_ID"
add_secret "s3_build_cache_secret_key" "$S3_BUILD_CACHE_SECRET_KEY"
add_secret "s3_build_cache_endpoint" "$S3_BUILD_CACHE_ENDPOINT"

echo ""
echo "Done! Verifying secrets..."
woodpecker-cli repo secret ls --repository "$REPO"
