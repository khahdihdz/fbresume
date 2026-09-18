#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

BASE="$HOME/fbresume"
DATA="$BASE/data"
DB="$DATA/videos.json"

mkdir -p "$DATA"
[ -f "$DB" ] || printf '%s\n' '[]' > "$DB"

ok()    { printf '\033[1;32m[OK]\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33m[!]\033[0m %s\n' "$*"; }

doctor() {
  echo "FBResume — kiểm tra môi trường"
  echo

  local failed=0

  if command -v jq >/dev/null 2>&1; then
    ok "jq: đã cài"
  else
    warn "jq: thiếu"
    echo "  Cài: pkg install jq"
    failed=1
  fi

  if command -v git >/dev/null 2>&1; then
    ok "git: đã cài"
  else
    warn "git: thiếu"
    echo "  Cài: pkg install git"
    failed=1
  fi

  if command -v termux-open-url >/dev/null 2>&1; then
    ok "termux-open-url: đã có"
  else
    warn "termux-open-url: chưa có"
    echo "  Không bắt buộc. Nếu cần mở URL Facebook trực tiếp từ Termux,"
    echo "  hãy cài ứng dụng Termux:API và gói: pkg install termux-api"
  fi

  if [ -d "$HOME/storage/downloads" ]; then
    ok "Bộ nhớ Termux: đã cấp quyền"
  else
    warn "Bộ nhớ Termux: chưa được cấp quyền"
    echo "  Chạy: termux-setup-storage"
    failed=1
  fi

  if [ -f "$DB" ] && jq empty "$DB" >/dev/null 2>&1; then
    ok "videos.json: hợp lệ"
  else
    warn "videos.json: thiếu hoặc JSON không hợp lệ"
    echo "  Có thể sửa lại bằng: printf '%s\\n' '[]' > "$DB""
    failed=1
  fi

  echo
  if [ "$failed" -eq 0 ]; then
    ok "Môi trường FBResume đã sẵn sàng."
    return 0
  fi

  warn "Một số thành phần đang thiếu. Làm theo hướng dẫn phía trên rồi chạy lại: fbresume doctor"
  return 1
}

case "${1:-menu}" in
  doctor)
    doctor
    ;;
  list)
    command -v jq >/dev/null 2>&1 || {
      echo "Thiếu jq. Chạy: pkg install jq"
      exit 1
    }
    jq . "$DB"
    ;;
  backup)
    if [ ! -d "$HOME/storage/downloads" ]; then
      echo "Chưa có quyền bộ nhớ."
      echo "Chạy: termux-setup-storage"
      exit 1
    fi
    mkdir -p "$HOME/storage/downloads"
    cp "$DB" "$HOME/storage/downloads/fbresume-videos.json"
    echo "Backup: $HOME/storage/downloads/fbresume-videos.json"
    ;;
  open)
    url="${2:-}"
    [ -n "$url" ] || { echo "Usage: fbresume open URL"; exit 1; }
    if command -v termux-open-url >/dev/null 2>&1; then
      termux-open-url "$url"
    else
      warn "termux-open-url chưa có; URL:"
      echo "$url"
      echo
      echo "Tùy chọn: cài Termux:API + chạy: pkg install termux-api"
    fi
    ;;
  *)
    echo "FBResume Termux"
    echo "  doctor               Kiểm tra và gợi ý cài đặt thành phần thiếu"
    echo "  list                 Xem dữ liệu"
    echo "  backup               Sao lưu"
    echo "  open URL             Mở URL Facebook"
    ;;
esac
