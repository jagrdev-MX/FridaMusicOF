#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SITE_DIR="$ROOT_DIR/site"
INDEX="$SITE_DIR/index.html"
CSS="$SITE_DIR/styles.css"

required_files=(
  "$INDEX"
  "$CSS"
  "$SITE_DIR/assets/fridamusic-logo.svg"
  "$SITE_DIR/assets/fridamusic-logo-round.svg"
  "$SITE_DIR/README.md"
  "$SITE_DIR/assets/developers.json"
  "$ROOT_DIR/start-server.bat"
)

for f in "${required_files[@]}"; do
  [[ -f "$f" ]] || { echo "Missing file: $f"; exit 1; }
done

grep -q 'https://github.com/jagrdev-MX/FridaMusicOF' "$INDEX"
grep -q 'https://www.instagram.com/fridalabs_mx/' "$INDEX"
grep -q 'https://www.instagram.com/jagr.dev/' "$INDEX"
grep -q 'mailto:fridalabs.soporte@gmail.com' "$INDEX"
grep -q 'assets/fridamusic-logo.svg' "$INDEX"
grep -q 'assets/fridamusic-logo-round.svg' "$INDEX"
grep -q 'assets/developers.json' "$INDEX"
grep -q 'https://github.com/juliocps25' "$SITE_DIR/assets/developers.json"

echo "Site validation OK"
