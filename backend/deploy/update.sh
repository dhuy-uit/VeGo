#!/usr/bin/env bash
# Cập nhật server lên code mới nhất trên GitHub:  bash /opt/vego/backend/deploy/update.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
git pull --ff-only
cd backend
docker compose --profile https up -d --build
docker image prune -f > /dev/null
docker compose ps
