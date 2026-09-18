#!/data/data/com.termux/files/usr/bin/bash
set -e

BASE="$HOME/fbresume"
DATA="$BASE/data"
DB="$DATA/videos.json"
mkdir -p "$DATA"

[ -f "$DB" ] || printf '%s\n' '[]' > "$DB"

case "${1:-menu}" in
  list)
    cat "$DB"
    ;;
  backup)
    mkdir -p "$HOME/storage/downloads"
    cp "$DB" "$HOME/storage/downloads/fbresume-videos.json"
    echo "Backup: $HOME/storage/downloads/fbresume-videos.json"
    ;;
  open)
    url="${2:-}"
    [ -n "$url" ] || { echo "Usage: fbresume.sh open URL"; exit 1; }
    if command -v termux-open-url >/dev/null 2>&1; then
      termux-open-url "$url"
    else
      echo "$url"
    fi
    ;;
  *)
    echo "FBResume Termux"
    echo "  list                 Xem dữ liệu"
    echo "  backup               Sao lưu"
    echo "  open URL             Mở URL Facebook"
    ;;
esac
