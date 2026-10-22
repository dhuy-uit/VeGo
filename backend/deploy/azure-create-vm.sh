#!/usr/bin/env bash
# Tạo máy ảo Azure chạy backend VéGo. Chạy trong Azure Cloud Shell (Bash):
#   curl -fsSL https://raw.githubusercontent.com/dhuy-uit/VeGo/main/backend/deploy/azure-create-vm.sh | bash
# Có thể đổi: RG, VM_NAME, DNS_LABEL, LOCATIONS, VM_SIZE (đặt biến trước lệnh bash).
set -euo pipefail

RG="${RG:-vego-rg}"
VM_NAME="${VM_NAME:-vego-vm}"
VM_SIZE="${VM_SIZE:-Standard_B1s}"          # B1s: miễn phí 750 giờ/tháng với Azure for Students
LOCATIONS="${LOCATIONS:-southeastasia eastasia japaneast koreacentral centralindia australiaeast}"
REPO="${REPO:-https://github.com/dhuy-uit/VeGo}"
BRANCH="${BRANCH:-main}"
SUFFIX="$(tr -dc 'a-z0-9' < /dev/urandom | head -c 5 || true)"
DNS_LABEL="${DNS_LABEL:-vego-api-$SUFFIX}"

echo "==> Tài khoản Azure đang dùng:"
az account show --query "{subscription:name, user:user.name}" -o table

FIRST_LOC="${LOCATIONS%% *}"
echo "==> Tạo resource group $RG"
az group create -n "$RG" -l "$FIRST_LOC" -o none

created=""
for LOC in $LOCATIONS; do
  FQDN="$DNS_LABEL.$LOC.cloudapp.azure.com"
  INIT="$(mktemp)"
  cat > "$INIT" <<CLOUDINIT
#cloud-config
package_update: true
packages: [git, ca-certificates, curl, openssl]
runcmd:
  - fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile && echo '/swapfile none swap sw 0 0' >> /etc/fstab
  - curl -fsSL https://get.docker.com | sh
  - usermod -aG docker vego
  - git clone --depth 1 -b $BRANCH $REPO /opt/vego
  - chown -R vego:vego /opt/vego
  - DOMAIN=$FQDN bash /opt/vego/backend/deploy/setup-server.sh > /var/log/vego-setup.log 2>&1
CLOUDINIT

  echo "==> Thử tạo máy ảo $VM_SIZE ở $LOC ..."
  if az vm create -g "$RG" -n "$VM_NAME" -l "$LOC" \
       --image Ubuntu2404 --size "$VM_SIZE" \
       --admin-username vego --generate-ssh-keys \
       --public-ip-sku Standard --public-ip-address-dns-name "$DNS_LABEL" \
       --os-disk-size-gb 30 --storage-sku Standard_LRS \
       --custom-data "$INIT" -o none 2> /tmp/vego-vm-error.txt; then
    created="$LOC"
    break
  fi
  echo "   Không tạo được ở $LOC: $(tail -n 2 /tmp/vego-vm-error.txt | tr '\n' ' ')"
  # Dọn tài nguyên dở dang trước khi thử vùng khác
  az resource list -g "$RG" --query "[].id" -o tsv | xargs -r az resource delete --ids -o none 2>/dev/null || true
done

if [ -z "$created" ]; then
  echo "!! Không tạo được máy ảo ở vùng nào. Gửi nội dung lỗi ở trên cho Claude." >&2
  exit 1
fi

echo "==> Mở cổng 80 (HTTP) và 443 (HTTPS)"
az vm open-port -g "$RG" -n "$VM_NAME" --port 80,443 --priority 1010 -o none

FQDN="$DNS_LABEL.$created.cloudapp.azure.com"
IP="$(az vm show -d -g "$RG" -n "$VM_NAME" --query publicIps -o tsv)"
cat <<DONE

======================================================================
 ĐÃ TẠO MÁY ẢO. Server cần khoảng 10-15 phút để cài Docker và build API.
   Địa chỉ API : https://$FQDN
   IP          : $IP
   SSH         : ssh vego@$FQDN   (khóa SSH đã lưu trong Cloud Shell)
 Kiểm tra      : curl https://$FQDN/actuator/health
 Xem tiến trình: ssh vego@$FQDN 'sudo tail -f /var/log/vego-setup.log'
 Gửi dòng "Địa chỉ API" ở trên cho Claude.
======================================================================
DONE
