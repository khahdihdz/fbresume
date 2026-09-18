#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

BASE="$HOME/fbresume"
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"

info()  { printf '\033[1;36m[FBResume]\033[0m %s\n' "$*"; }
ok()    { printf '\033[1;32m[OK]\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33m[!]\033[0m %s\n' "$*"; }
error() { printf '\033[1;31m[ERROR]\033[0m %s\n' "$*" >&2; }

if [ ! -d "/data/data/com.termux/files/usr" ] || ! command -v pkg >/dev/null 2>&1; then
  error "Script này cần chạy trong Termux."
  exit 1
fi

if [ ! -d "$BASE" ]; then
  error "Không tìm thấy $BASE."
  echo "Hãy clone repo vào ~/fbresume trước, ví dụ:"
  echo "  git clone <URL-repo> ~/fbresume"
  exit 1
fi

info "Kiểm tra và cài các gói Termux bắt buộc..."

pkg update -y >/dev/null

required_packages=(git jq)
missing=()

for package in "${required_packages[@]}"; do
  if ! command -v "$package" >/dev/null 2>&1; then
    missing+=("$package")
  fi
done

if [ "${#missing[@]}" -gt 0 ]; then
  info "Thiếu: ${missing[*]} — đang cài tự động..."
  pkg install -y "${missing[@]}"
else
  ok "git và jq đã sẵn sàng."
fi

mkdir -p "$BASE/data"
[ -f "$BASE/data/videos.json" ] || printf '%s\n' '[]' > "$BASE/data/videos.json"

if [ -f "$BASE/termux/fbresume.sh" ]; then
  install -m 755 "$BASE/termux/fbresume.sh" "$PREFIX/bin/fbresume" 2>/dev/null || {
    cp "$BASE/termux/fbresume.sh" "$PREFIX/bin/fbresume"
    chmod 755 "$PREFIX/bin/fbresume"
  }
else
  error "Không tìm thấy termux/fbresume.sh."
  exit 1
fi

echo
ok "FBResume Termux đã cài."
echo
echo "Kiểm tra môi trường:"
echo "  fbresume doctor"
echo
echo "Lệnh:"
echo "  fbresume                 Mở menu"
echo "  fbresume list            Xem dữ liệu"
echo "  fbresume backup          Sao lưu"
echo "  fbresume open URL        Mở URL Facebook"
echo
echo "Tùy chọn:"
if command -v termux-open-url >/dev/null 2>&1; then
  ok "termux-open-url: đã có"
else
  warn "termux-open-url: chưa có/không khả dụng."
  echo "  Có thể cài Termux:API nếu muốn mở URL bằng lệnh Termux."
fi

if [ -d "$HOME/storage/downloads" ]; then
  ok "Quyền truy cập bộ nhớ: đã sẵn sàng"
else
  warn "Chưa có ~/storage/downloads."
  echo "  Khi cần sao lưu, chạy: termux-setup-storage"
fi
