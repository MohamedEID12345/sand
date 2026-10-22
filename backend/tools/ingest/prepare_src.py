"""Copies a folder of law PDFs into ./src/consolidated/ named by the first 8 hex chars of their MD5 — the names registry.py uses
for the consolidated batch. Usage: python3 prepare_src.py <folder-with-pdfs>"""
import hashlib, os, shutil, sys
src = sys.argv[1]
dst = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'src', 'consolidated')
os.makedirs(dst, exist_ok=True)
for root, _, files in os.walk(src):
    for f in files:
        if f.lower().endswith('.pdf'):
            p = os.path.join(root, f)
            h = hashlib.md5(open(p, 'rb').read()).hexdigest()[:8]
            shutil.copy(p, os.path.join(dst, h + '.pdf'))
            print(h, f)
