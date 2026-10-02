#!/bin/bash
# Kendi kontrolüm için: birkaç sayfayı yan yana tek görselde birleştirir.
# kullanım: contact.sh çıktı.png genişlik sayfa_no1 sayfa_no2 ...   (no = 00, 07 ...)
out=$1; w=$2; shift 2
P="$(cd "$(dirname "$0")/.." && pwd)/png"
files=()
for n in "$@"; do f=$(ls "$P"/${n}_*.png 2>/dev/null | head -1); [ -n "$f" ] && files+=("$f"); done
convert "${files[@]}" -background '#EEF2F7' -gravity north +append -resize ${w}x "$out"
identify "$out" | cut -d' ' -f1-3
