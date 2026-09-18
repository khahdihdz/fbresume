#!/data/data/com.termux/files/usr/bin/bash
set -e

pkg update -y
pkg install -y git jq

mkdir -p "$HOME/fbresume/data"
[ -f "$HOME/fbresume/data/videos.json" ] || printf '%s\n' '[]' > "$HOME/fbresume/data/videos.json"

install -m 755 "$HOME/fbresume/termux/fbresume.sh" "$PREFIX/bin/fbresume" 2>/dev/null || {
  cp "$HOME/fbresume/termux/fbresume.sh" "$PREFIX/bin/fbresume"
  chmod 755 "$PREFIX/bin/fbresume"
}

echo
echo "FBResume Termux đã cài."
echo "Chạy: fbresume"
