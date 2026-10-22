#!/usr/bin/env bash
# Cài đặt / khởi động backend VéGo trên server (Ubuntu đã có Docker).
#   DOMAIN=ten-mien bash setup-server.sh
# Tạo .env với mật khẩu CSDL và JWT_SECRET ngẫu nhiên nếu chưa có, rồi chạy Docker Compose.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  : "${DOMAIN:?Cần đặt DOMAIN, vd DOMAIN=vego-api.southeastasia.cloudapp.azure.com}"
  umask 077
  cat > .env <<ENV
DOMAIN=$DOMAIN
PUBLIC_BASE_URL=https://$DOMAIN
DB_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
EXPOSE_RESET_TOKEN=false
SEED_DEMO_USER=true
ENV
  chown --reference=. .env 2>/dev/null || true
  echo "Đã tạo .env"
fi

docker compose --profile https up -d --build
docker image prune -f > /dev/null
echo "Xong. Kiểm tra: curl -s https://$(grep '^DOMAIN=' .env | cut -d= -f2)/actuator/health"
