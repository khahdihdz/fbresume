#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

APP_NAME="FBResume"
KEYSTORE_DIR="$HOME/.android/keystores"
KEYSTORE="$KEYSTORE_DIR/fbresume-release.jks"
ALIAS="fbresume"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pause() {
  echo
  read -r -p "Nhấn Enter để tiếp tục..." _
}

require_keytool() {
  if ! command -v keytool >/dev/null 2>&1; then
    echo -e "$RED Không tìm thấy keytool.$NC"
    echo "Cài Java bằng: pkg install openjdk-17"
    exit 1
  fi
}

create_keystore() {
  require_keytool
  mkdir -p "$KEYSTORE_DIR"
  chmod 700 "$KEYSTORE_DIR"

  if [ -f "$KEYSTORE" ]; then
    echo -e "$YELLOW Keystore đã tồn tại:$NC $KEYSTORE"
    read -r -p "Tạo lại và ghi đè? (y/N): " answer
    [[ "$answer" =~ ^[Yy]$ ]] || return
    rm -f "$KEYSTORE"
  fi

  echo
  echo "=== TẠO KEYSTORE CHO $APP_NAME ==="
  echo "Alias: $ALIAS"
  echo "File : $KEYSTORE"
  echo

  keytool -genkeypair -v     -keystore "$KEYSTORE"     -alias "$ALIAS"     -keyalg RSA     -keysize 2048     -validity 10000     -dname "CN=$APP_NAME, OU=Android, O=khahdihdz, L=Vietnam, ST=Vietnam, C=VN"

  chmod 600 "$KEYSTORE"
  echo
  echo -e "$GREEN ✓ Tạo keystore thành công.$NC"
  echo "Hãy sao lưu file và mật khẩu ở nơi an toàn."
}

show_info() {
  require_keytool
  [ -f "$KEYSTORE" ] || { echo -e "$RED Chưa có keystore.$NC"; return; }
  keytool -list -v -keystore "$KEYSTORE"
}

export_base64() {
  [ -f "$KEYSTORE" ] || { echo -e "$RED Chưa có keystore.$NC"; return; }

  OUT="$HOME/fbresume-keystore.base64"
  base64 -w 0 "$KEYSTORE" > "$OUT" 2>/dev/null || base64 "$KEYSTORE" | tr -d '\n' > "$OUT"
  chmod 600 "$OUT"

  echo
  echo -e "$GREEN ✓ Đã tạo Base64:$NC"
  echo "$OUT"
  echo
  echo "GitHub Repository: khahdihdz/fbresume"
  echo
  echo "Secrets:"
  echo "FBRESUME_KEYSTORE_BASE64"
  echo "FBRESUME_KEYSTORE_PASSWORD"
  echo "FBRESUME_KEY_ALIAS = $ALIAS"
  echo "FBRESUME_KEY_PASSWORD"
  echo
  echo -e "$YELLOW Không commit file .jks hoặc file Base64 vào Git.$NC"
}

menu() {
  while true; do
    clear
    echo "=================================================="
    echo "       $APP_NAME - KEYSTORE MANAGER"
    echo "=================================================="
    echo
    echo "1. Tạo keystore"
    echo "2. Xem thông tin keystore"
    echo "3. Xuất Base64 cho GitHub Actions"
    echo "4. Hiển thị GitHub Secrets"
    echo "0. Thoát"
    echo
    read -r -p "Lựa chọn: " choice

    case "$choice" in
      1) create_keystore; pause ;;
      2) show_info; pause ;;
      3) export_base64; pause ;;
      4)
        echo
        echo "Repository: khahdihdz/fbresume"
        echo
        echo "FBRESUME_KEYSTORE_BASE64"
        echo "FBRESUME_KEYSTORE_PASSWORD"
        echo "FBRESUME_KEY_ALIAS = $ALIAS"
        echo "FBRESUME_KEY_PASSWORD"
        pause
        ;;
      0) exit 0 ;;
      *) echo "Lựa chọn không hợp lệ."; sleep 1 ;;
    esac
  done
}

menu
