#!/bin/bash
set -e

echo "🚀 Deploying Auth Frontend to Production"

# Configuration
IMAGE_TAG="ghcr.io/dsjkeeplearning/kos-auth-backend-frontend:1.0.13"
CONTAINER_NAME="auth-frontend"
PORT="8090"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}Step 1: Authenticate with GitHub Container Registry${NC}"
echo "Your GitHub token:"
read -s GITHUB_TOKEN
echo ""
echo $GITHUB_TOKEN | docker login ghcr.io -u $GITHUB_USER --password-stdin

echo -e "\n${BLUE}Step 2: Pull latest image${NC}"
docker pull $IMAGE_TAG

echo -e "\n${BLUE}Step 3: Stop and remove old container (if exists)${NC}"
docker stop $CONTAINER_NAME 2>/dev/null || true
docker rm $CONTAINER_NAME 2>/dev/null || true

echo -e "\n${BLUE}Step 4: Start new container${NC}"
docker run -d \
  --name $CONTAINER_NAME \
  --restart unless-stopped \
  -p $PORT:80 \
  $IMAGE_TAG

echo -e "\n${BLUE}Step 5: Verify container is running${NC}"
docker ps | grep $CONTAINER_NAME

echo -e "\n${BLUE}Step 6: Check logs${NC}"
docker logs $CONTAINER_NAME --tail 20

echo -e "\n${GREEN}✅ Deployment complete!${NC}"
echo -e "Container: $CONTAINER_NAME"
echo -e "Port: $PORT"
echo -e "Image: $IMAGE_TAG"
echo -e "\nAccess locally at: http://localhost:$PORT"
