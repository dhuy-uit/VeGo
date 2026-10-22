#!/usr/bin/env bash
# Tạo máy ảo Azure chạy backend VéGo. Chạy trong Azure Cloud Shell (Bash):
#   curl -fsSL https://raw.githubusercontent.com/dhuy-uit/VeGo/main/backend/deploy/azure-create-vm.sh | bash
# Có thể đổi: RG, VM_NAME, DNS_LABEL, LOCATIONS, SIZES (đặt biến trước lệnh bash).
set -uo pipefail

RG="${RG:-vego-rg}"
VM_NAME="${VM_NAME:-vego-vm}"
# B1s: miễn phí 750 giờ/tháng với Azure for Students. Các cỡ sau chỉ dùng khi vùng không có B1s.
SIZES="${SIZES:-Standard_B1s Standard_B2ats_v2 Standard_B1ms}"
# Ưu tiên vùng gần Việt Nam
PREFERRED="eastasia malaysiawest indonesiacentral southeastasia japaneast koreacentral centralindia australiaeast"
REPO="${REPO:-https://github.com/dhuy-uit/VeGo}"
BRANCH="${BRANCH:-main}"
SUFFIX="$(tr -dc 'a-z0-9' < /dev/urandom | head -c 5 || true)"
DNS_LABEL="${DNS_LABEL:-vego-api-$SUFFIX}"
LOG="$HOME/vego-vm-debug.log"

echo "==> Tài khoản Azure đang dùng:"
az account show --query "{subscription:name, user:user.name}" -o table

# Azure for Students chỉ cho tạo tài nguyên ở một số vùng (policy "Allowed resource deployment regions")
if [ -z "${LOCATIONS:-}" ]; then
  ALLOWED="$(az policy assignment list \
      --query "[].parameters.listOfAllowedLocations.value[]" -o tsv 2>/dev/null | tr '[:upper:]' '[:lower:]' | sort -u)"
  if [ -n "$ALLOWED" ]; then
    LOCATIONS=""
    for l in $PREFERRED; do echo "$ALLOWED" | grep -qx "$l" && LOCATIONS="$LOCATIONS $l"; done
    for l in $ALLOWED; do case " $LOCATIONS " in *" $l "*) ;; *) LOCATIONS="$LOCATIONS $l";; esac; done
    echo "==> Vùng được phép:$LOCATIONS"
  else
    LOCATIONS="$PREFERRED"
  fi
fi

fresh_group() {
  # Xóa nhóm cũ (nếu có) rồi tạo lại ở vùng $1 để không còn tài nguyên dở dang
  if az group show -n "$RG" -o none 2>/dev/null; then
    echo "   Dọn resource group $RG cũ..."
    az group delete -n "$RG" --yes -o none 2>/dev/null || az group wait --deleted -n "$RG" 2>/dev/null || true
  fi
  az group create -n "$RG" -l "$1" -o none
}

real_error() {
  # az CLI đôi khi che lỗi thật ("content already consumed") -> lấy từ log debug
  grep -oE '\((RequestDisallowedByAzure|SkuNotAvailable|QuotaExceeded|OperationNotAllowed|InvalidParameter|[A-Za-z]+)\) [^.]*' "$LOG" \
    | grep -v '^(DEBUG)' | sort -u | head -n 3
}

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

  for SIZE in $SIZES; do
    echo "==> Thử tạo máy ảo $SIZE ở $LOC ..."
    fresh_group "$LOC"
    if az vm create -g "$RG" -n "$VM_NAME" -l "$LOC" \
         --image Ubuntu2404 --size "$SIZE" \
         --admin-username vego --generate-ssh-keys \
         --public-ip-sku Standard --public-ip-address-dns-name "$DNS_LABEL" \
         --os-disk-size-gb 30 --storage-sku Standard_LRS \
         --custom-data "$INIT" -o none --debug 2> "$LOG"; then
      created="$LOC"
      break 2
    fi
    echo "   Không được: $(real_error | tr '\n' ' ')"
    # Bị chặn theo vùng thì thử cỡ khác cũng vô ích -> sang vùng kế
    grep -q "RequestDisallowedByAzure" "$LOG" && break
  done
done

if [ -z "$created" ]; then
  echo "!! Không tạo được máy ảo. Gửi các dòng 'Không được' ở trên cho Claude." >&2
  exit 1
fi

echo "==> Mở cổng 80 (HTTP) và 443 (HTTPS)"
az vm open-port -g "$RG" -n "$VM_NAME" --port 80,443 --priority 1010 -o none

FQDN="$DNS_LABEL.$created.cloudapp.azure.com"
IP="$(az vm show -d -g "$RG" -n "$VM_NAME" --query publicIps -o tsv)"
SIZE_USED="$(az vm show -g "$RG" -n "$VM_NAME" --query hardwareProfile.vmSize -o tsv)"
cat <<DONE

======================================================================
 ĐÃ TẠO MÁY ẢO ($SIZE_USED ở $created).
 Server cần khoảng 10-15 phút để cài Docker và build API.
   Địa chỉ API : https://$FQDN
   IP          : $IP
 Kiểm tra      : curl https://$FQDN/actuator/health
 Gửi dòng "Địa chỉ API" ở trên cho Claude.
======================================================================
DONE
