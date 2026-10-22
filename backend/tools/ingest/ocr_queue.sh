#!/bin/bash
# Resumable page-by-page Arabic OCR (tesseract LSTM, tessdata_best ara). Usage: ocr_queue.sh id:file:dpi ...
OUT="$(cd "$(dirname "$0")" && pwd)/ocr"
for spec in "$@"; do
  id="${spec%%:*}"; rest="${spec#*:}"; file="${rest%%:*}"; dpi="${rest##*:}"
  n=$(pdfinfo "$file" 2>/dev/null | awk '/^Pages/{print $2}')
  mkdir -p "$OUT/$id"
  for ((p=1; p<=n; p++)); do
    t=$(printf "%s/%s/p-%03d.txt" "$OUT" "$id" "$p")
    [ -s "$t" ] && continue
    pdftoppm -r "$dpi" -gray -f $p -l $p -png "$file" "$OUT/.page" >/dev/null 2>&1
    img=$(ls "$OUT/.page"-*.png 2>/dev/null | head -1)
    tesseract "$img" "${t%.txt}" -l ara --oem 1 --psm 6 -c preserve_interword_spaces=1 >/dev/null 2>&1
    [ -s "$t" ] || echo " " > "$t"
    rm -f "$OUT/.page"-*.png
    echo "$(date +%T) $id $p/$n"
  done
  echo "$(date +%T) DONE $id"
done
