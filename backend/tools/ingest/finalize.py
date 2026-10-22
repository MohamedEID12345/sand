"""Re-applies publication rules on content/laws/*.json and rebuilds index.json + REVIEW.md without re-extracting."""
import json, os, glob, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ingest
from registry import DOCS, CATEGORIES

OUT = ingest.OUT
order = {d['id']: i for i, d in enumerate(DOCS)}
reg = {d['id']: d for d in DOCS}
index = []
for f in sorted(glob.glob(os.path.join(OUT, '*.json'))):
    if f.endswith('index.json'):
        continue
    doc = json.load(open(f, encoding='utf-8'))
    m = doc['meta']
    if m['id'] not in reg:  # stale file from an id that no longer exists
        os.remove(f); continue
    gaps = m.get('missingArticleNumbers') or []
    nums = [a['number'] for a in doc['articles'] if a['part'] == 'main' and not a['suffix']]
    top = max(nums) if nums else 0
    if m['id'] in {'law-66-1973', 'law-103-1961'} or (m.get('published') and top and len(gaps) > max(10, 0.25 * top) and m['textQuality'] != 'TRANSCRIBED'):
        if m.get('published'):
            m['published'] = False
        else:
            pass
        m['notes'] = ((m.get('notes') or '') + ' — ترقيم المواد غير مكتمل/مختلط باللائحة: مسودة حتى المراجعة').strip(' —')
    json.dump(doc, open(f, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    index.append(m)
have = {m['id'] for m in index}
for d in DOCS:
    if d['id'] not in have:
        index.append({'id': d['id'], 'title': d['title'], 'subtitle': d['subtitle'], 'number': d['number'], 'year': d['year'], 'categoryId': d['category'],
                      'published': False, 'textQuality': 'PENDING', 'articleCount': 0, 'missingArticleNumbers': [], 'notes': d.get('note_text'),
                      'status': d.get('status'), 'statusNote': d.get('status_note'), 'suggestedStatus': d.get('suggested'), 'suggestedStatusNote': d.get('suggested_note')})
index.sort(key=lambda m: order.get(m['id'], 999))
json.dump({'categories': CATEGORIES, 'laws': index}, open(os.path.join(OUT, 'index.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
ingest.write_review(index)
pub = [m for m in index if m.get('published')]
print('docs', len(index), 'published', len(pub), 'articles(published)', sum(m.get('articleCount', 0) for m in pub))
print('drafts:', [m['id'] for m in index if not m.get('published')])
