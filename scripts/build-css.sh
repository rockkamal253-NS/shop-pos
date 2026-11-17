#!/usr/bin/env bash
# Rebuilds src/main/resources/static/css/app.css from input.css with the Tailwind
# standalone CLI. One-time download (~40 MB) into .tailwind/ — the app itself is
# completely CDN-free. Usage: ./scripts/build-css.sh
set -euo pipefail
cd "$(dirname "$0")/.."

BIN="${TAILWIND_BIN:-$PWD/.tailwind/tailwindcss}"
mkdir -p .tailwind

if [ ! -x "$BIN" ]; then
    echo "Downloading Tailwind standalone CLI v3.4.17..."
    curl -sL -o "$BIN" https://github.com/tailwindlabs/tailwindcss/releases/download/v3.4.17/tailwindcss-linux-x64
    chmod +x "$BIN"
fi

"$BIN" -c tailwind.config.js -i src/main/resources/static/css/input.css -o src/main/resources/static/css/app.css --minify
echo "Wrote src/main/resources/static/css/app.css"
