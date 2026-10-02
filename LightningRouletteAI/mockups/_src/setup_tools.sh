#!/bin/bash
# Görsel üretim araçlarını kurar: Chromium (npm, indirme gerektirmez) + yazı tipleri + fonttools.
# Ortam sıfırlanırsa tekrar çalıştır.  Kullanım: bash setup_tools.sh
set -e
T=${LRA_TOOLS:-/home/user/render-tools}
mkdir -p "$T" && cd "$T"
[ -f package.json ] || npm init -y >/dev/null
npm install --silent --no-audit --no-fund puppeteer-core @sparticuz/chromium \
  @fontsource/roboto @fontsource/roboto-mono @fontsource/noto-color-emoji \
  @fontsource/noto-sans-symbols @fontsource/noto-sans-symbols-2 2>&1 | tail -3
if [ ! -x venv/bin/python ]; then python3 -m venv venv && venv/bin/pip install -q fonttools brotli; fi
echo "araçlar hazır: $T"
