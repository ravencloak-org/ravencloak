#!/bin/bash
set -e

echo "🌐 Setting up Cloudflare Tunnel for Auth Frontend"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}Step 1: Install cloudflared${NC}"
if ! command -v cloudflared &> /dev/null; then
    echo "Installing cloudflared..."
    # For Debian/Ubuntu
    wget -q https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
    sudo dpkg -i cloudflared-linux-amd64.deb
    rm cloudflared-linux-amd64.deb
    echo -e "${GREEN}✅ cloudflared installed${NC}"
else
    echo -e "${GREEN}✅ cloudflared already installed${NC}"
fi

echo -e "\n${BLUE}Step 2: Login to Cloudflare${NC}"
echo -e "${YELLOW}This will open a browser window for authentication${NC}"
cloudflared tunnel login

echo -e "\n${BLUE}Step 3: Create tunnel${NC}"
TUNNEL_NAME="auth-frontend-prod"
echo "Creating tunnel: $TUNNEL_NAME"
cloudflared tunnel create $TUNNEL_NAME

# Get tunnel ID
TUNNEL_ID=$(cloudflared tunnel list | grep $TUNNEL_NAME | awk '{print $1}')
echo -e "${GREEN}✅ Tunnel created: $TUNNEL_ID${NC}"

echo -e "\n${BLUE}Step 4: Create tunnel configuration${NC}"
mkdir -p ~/.cloudflared
cat > ~/.cloudflared/config.yml <<EOF
tunnel: $TUNNEL_ID
credentials-file: /root/.cloudflared/$TUNNEL_ID.json

ingress:
  # Auth Frontend - Admin Portal
  - hostname: insight-service.jobin.wtf
    service: http://localhost:8090
    originRequest:
      noTLSVerify: false
      connectTimeout: 30s

  # Auth Backend API
  - hostname: api.insight-service.jobin.wtf
    service: http://localhost:8091
    originRequest:
      noTLSVerify: false
      connectTimeout: 30s

  # Catch-all rule (required)
  - service: http_status:404
EOF

echo -e "${GREEN}✅ Configuration created at ~/.cloudflared/config.yml${NC}"

echo -e "\n${BLUE}Step 5: Create DNS records${NC}"
echo "Creating CNAME for insight-service.jobin.wtf..."
cloudflared tunnel route dns $TUNNEL_NAME insight-service.jobin.wtf

echo "Creating CNAME for api.insight-service.jobin.wtf..."
cloudflared tunnel route dns $TUNNEL_NAME api.insight-service.jobin.wtf

echo -e "\n${BLUE}Step 6: Install as systemd service${NC}"
sudo cloudflared service install
sudo systemctl start cloudflared
sudo systemctl enable cloudflared

echo -e "\n${BLUE}Step 7: Check tunnel status${NC}"
cloudflared tunnel list
sudo systemctl status cloudflared --no-pager

echo -e "\n${GREEN}✅ Cloudflare Tunnel setup complete!${NC}"
echo -e "\nTunnel Name: $TUNNEL_NAME"
echo -e "Tunnel ID: $TUNNEL_ID"
echo -e "\nYour services are now available at:"
echo -e "  Frontend: https://forge.keeplearningos.com"
echo -e "  Backend:  https://api.forge.keeplearningos.com"
echo -e "\n${YELLOW}Note: Make sure your Docker containers are running on ports 8090 and 8091${NC}"
