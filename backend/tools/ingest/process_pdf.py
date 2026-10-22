"""Processes one law PDF uploaded from the admin console (called by the API server, no external services):
  python3 process_pdf.py <in.pdf> <meta.json> <out.json>
Picks the best extraction (text layer / glyph decoders / OCR), segments the articles and writes {meta, preamble, closing, articles}."""
import json, os, re, subprocess, sys, pickle
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pymupdf as fitz
import ingest, norm, decode_tte, decode_ahram

ROOT = os.path.dirname(os.path.abspath(__file__))
VP = os.path.join(ROOT, 'vocab.pkl')
VOCAB = pickle.load(open(VP, 'rb')) if os.path.exists(VP) else None


def rate(t):
    ws = re.findall(r'[\u0621-\u064a]+', t)
    return 0 if len(ws) < 30 else 100 * sum(1 for w in ws if (VOCAB.get(w, 0) > 0 if VOCAB else len(w) > 1)) / len(ws)


def best_method(path):
    d = fitz.open(path)
    n = len(d)
    pages = sorted({min(n, x) for x in (2, max(3, n // 3), max(4, 2 * n // 3))})
    fonts = {x[3] for p in pages for x in d[p - 1].get_fonts(full=True)}
    cands = {'text': norm.normalize('\n'.join(subprocess.run(['pdftotext', '-f', str(p), '-l', str(p), path, '-'], capture_output=True, text=True).stdout for p in pages), True),
             'decode_tte': '\n'.join(decode_tte.page_text(d[p - 1]) for p in pages),
             'visual': '\n'.join(decode_tte.page_text_unicode(d[p - 1]) for p in pages)}
    if any(re.match(r'^T\d+$', f) for f in fonts):
        cands['decode_t3'] = '\n'.join(decode_ahram.page_text(d[p - 1]) for p in pages)
    scores = {k: rate(norm.normalize(v)) for k, v in cands.items()}
    m = max(scores, key=scores.get)
    return (m if scores[m] >= 55 else 'ocr'), scores


def main(pdf, meta_file, out_file):
    meta = json.load(open(meta_file, encoding='utf-8'))
    qid = meta['id']
    method, scores = best_method(pdf)
    if method == 'ocr':
        subprocess.run([os.path.join(ROOT, 'ocr_queue.sh'), f'{qid}:{pdf}:250'], check=False)
    d = dict(id=qid, file=pdf, method=method, number=meta.get('number') or 0, year=meta.get('year') or 0, title=meta['title'],
             subtitle=meta.get('subtitle') or meta['title'], category=meta['categoryId'], consolidated=True)
    pages = ingest.page_texts(d)
    if method == 'text' and VOCAB:
        pages = [(p, ingest.fix_dropped_alef(ingest.fix_lig(t, VOCAB), VOCAB)) for p, t in pages]
    parts, arts = ingest.segment(d, pages)
    nums = [a['number'] for a in arts if a['part'] == 'main' and not a['suffix']]
    gaps = sorted(set(range(1, max(nums) + 1)) - set(nums)) if nums else []
    quality = {'ocr': 'OCR', 'text': 'TEXT_LAYER', 'visual': 'TEXT_LAYER'}.get(method, 'DECODED')
    publishable = bool(arts) and quality != 'OCR' and len(gaps) <= max(10, 0.25 * (max(nums) if nums else 0))
    m = {**meta, 'type': 'LAW', 'textQuality': quality, 'verified': quality != 'OCR' and bool(meta.get('verified')), 'pageCount': len(pages),
         'articleCount': len(arts), 'missingArticleNumbers': gaps[:200], 'published': publishable}
    json.dump({'meta': m, **parts, 'articles': arts, 'report': {'method': method, 'scores': {k: round(v, 1) for k, v in scores.items()}, 'gaps': len(gaps)}},
              open(out_file, 'w', encoding='utf-8'), ensure_ascii=False)
    print(json.dumps({'method': method, 'articles': len(arts), 'gaps': len(gaps), 'published': publishable}))


if __name__ == '__main__':
    main(*sys.argv[1:4])
