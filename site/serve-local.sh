#!/usr/bin/env bash
set -euo pipefail

PORT="${1:-8080}"
HOST="${HOST:-127.0.0.1}"

# Ayuda a evitar que herramientas/sistema intenten enrutar localhost por proxy.
export NO_PROXY="localhost,127.0.0.1,::1"
export no_proxy="localhost,127.0.0.1,::1"

printf 'FridaMusic site en: http://%s:%s/\n' "$HOST" "$PORT"
python3 -m http.server "$PORT" --bind "$HOST" --directory site
