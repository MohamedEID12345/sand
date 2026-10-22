"""Books / research papers -> content/books/<id>.json {meta, pages[{page, text}]} for the library reader + RAG.
Usage: python3 books.py"""
import json, os, re, subprocess, sys, glob, pickle
from collections import Counter
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ingest
from norm import normalize

OUT = os.environ.get('BOOKS_OUT', os.path.join(os.path.dirname(ingest.OUT), 'books'))
BOOKS = [
    dict(id='paper-ijdjl-7-2-465', file=os.path.join(os.path.dirname(os.path.abspath(__file__)), 'src', 'IJDJL_Volume_7_Issue_2_Pages_465-489.pdf'), fix_lig=True,
         type='BOOK', subtype='RESEARCH_PAPER', title='اتجاهات الإصلاح التشريعي في العلاقات الإيجارية لعام 2025',
         subtitle='أحمد شرف الدين — كلية الحقوق، جامعة عين شمس — المجلة الدولية للفقه والقضاء والتشريع، المجلد 7، العدد 2 (2026)، ص 465–489',
         author='أحمد شرف الدين', year=2026, categoryId='civil', doi='10.21608/IJDJL.2026.473657.1306',
         license='TO_CONFIRM', published=False,
         notes='بحث منشور في دورية علمية: يُنشر في المكتبة بعد تأكيد حق الاستخدام (وصول مفتوح/إذن). خط الملف يعكس بعض حروف الربط وأُصلح آليًا.'),
]


def vocab():
    v = Counter()
    for f in glob.glob(os.path.join(ingest.OUT, '*.json')):
        if f.endswith('index.json'):
            continue
        d = json.load(open(f))
        if d['meta']['textQuality'] != 'OCR':
            v.update(ingest.build_vocab([a['text'] for a in d['articles']]))
    return v


def main():
    os.makedirs(OUT, exist_ok=True)
    v = vocab()
    for b in BOOKS:
        n = int(re.search(r'Pages:\s+(\d+)', subprocess.run(['pdfinfo', b['file']], capture_output=True, text=True).stdout).group(1))
        pages = []
        for p in range(1, n + 1):
            raw = subprocess.run(['pdftotext', '-f', str(p), '-l', str(p), b['file'], '-'], capture_output=True, text=True).stdout
            t = normalize(raw, from_pdf_layer=True).replace('\ufffd', '')
            if b.get('fix_lig'):
                t = ingest.fix_transpose(ingest.fix_lig(t, v), v)
            t = '\n'.join(l for l in t.split('\n') if l.strip())
            pages.append({'page': p, 'text': t})
        meta = {k: b[k] for k in b if k not in ('file', 'fix_lig')}
        meta.update(pageCount=n, sourceFile=os.path.basename(b['file']), textQuality='TEXT_LAYER', verified=False)
        json.dump({'meta': meta, 'pages': pages}, open(f"{OUT}/{b['id']}.json", 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
        print(b['id'], n, 'pages', sum(len(p['text']) for p in pages), 'chars')


if __name__ == '__main__':
    main()
