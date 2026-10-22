"""Legislation ingestion: PDF/OCR/transcription -> normalized pages -> articles -> JSON for the library.
Usage: python3 ingest.py [doc-id ...]   (writes ../legal-guide/content/laws/)"""
import json, os, re, subprocess, sys, unicodedata
from collections import Counter
sys.path.insert(0, os.path.dirname(__file__))
from norm import normalize, search_key, AR_DIGITS
from registry import DOCS, CATEGORIES

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = os.environ.get('LAWS_OUT', os.path.abspath(os.path.join(ROOT, '..', '..', '..', 'content', 'laws')))
ORD = {'الاولي': 1, 'الثانيه': 2, 'الثالثه': 3, 'الرابعه': 4, 'الخامسه': 5, 'السادسه': 6, 'السابعه': 7, 'الثامنه': 8,
       'التاسعه': 9, 'العاشره': 10, 'الحاديه عشره': 11, 'الثانيه عشره': 12, 'الثالثه عشره': 13, 'الرابعه عشره': 14,
       'الخامسه عشره': 15, 'السادسه عشره': 16, 'السابعه عشره': 17, 'الثامنه عشره': 18, 'التاسعه عشره': 19, 'العشرون': 20}
CTRL = re.compile('[\x00-\x08\x0b-\x1f\x7f]')
HEADER_JUNK = re.compile(r'^(?:\d+\s*)?(?:ال)?جريد[ةه] الرسمي[ةه]|^الوقائع المصري[ةه]|^[\d\s\-–—.]+$|^-{3,}$|^=== ')
ART = re.compile(r'^\(?\s*(?:ال)?ماد[ةه]\s*([:\-–]?)\s*[\(\[]?\s*(\d{1,4})\s*[\)\]]?\s*(مكرر(?:ا|اً|ًا)?(?:\s*\(?\s*[أ-ي]\s*\)?)?)?\s*\)?\s*(.*)$')
ISS = re.compile(r'^\(?\s*(?:ال)?ماد[ةه]\s+(ال[^\s)]+(?:\s+عشر[ةه])?)\s*\)?\s*(?:\(?\s*(?:إ|ا)صدار\s*\)?)?\s*[:\-–]?\s*(.*)$')
CLOSING = re.compile(r'^(يبصم هذا|صدر برئاس|صدر برياس|صدر بقصر|\(?\s*الموافق)')
COMP = re.compile(r'^[\s)(]*(?:ال)?ماد[ةه]\s*(?:رقم)?[\s)(]*(?:(\d{1,2})\s*-\s*[\s)(]*)?:?\s*(\d{1,4})\s*(مكرر[اًا]*(?:\s*\(?[أ-ي]\)?)?)?\s*(إصدار)?\s*[:)(\s]*(.*)$')
HEAD = re.compile(r'^\(?\s*(الكتاب|الباب|الفصل|الفرع|القسم)\s+(ال\S+|\d+)(?:\s+(عشر|مكرر))?\s*\)?\s*[:\-–]?\s*(.*)$')


OCR_HDR = re.compile(r'^[\s():؟?.\-–_ل\d٠-٩]*(?:ال)?ماد[ةه]\s*(?:ل\s*)?[\s():؟?.\-–_\d٠-٩]*(مكرر\S*)?[\s():؟?.\-–_\d٠-٩]*$')
OCR_LEAD = re.compile(r'^[\s١1)(:\u200e\u200f]+(?=(?:ال)?ماد)')


def page_texts(doc):
    """Returns [(page_no, normalized_text)]."""
    m = doc['method']
    if m == 'manual':
        raw = open(os.path.join(ROOT, 'manual', doc['id'] + '.txt'), encoding='utf-8').read()
        parts = re.split(r'(?m)^=== صفحة (\d+)\s*$', raw)
        return [(int(parts[i]), normalize(parts[i + 1])) for i in range(1, len(parts), 2)]
    if doc.get('side_numbers'):  # two-column layout: "(١١)" printed beside the "مادة" block (above, below or inside it)
        base = page_texts(dict(doc, side_numbers=False))
        lines = [(p, l) for p, t in base for l in t.split('\n')]
        num = re.compile(r'^\(\s*([0-9٠-٩]{1,3}(?:\s*مكرر\S*)?)\s*\)\s*(.*)$')
        starts = [i for i, (_, l) in enumerate(lines) if re.match(r'^مادة\s', l)]
        used = set()
        for k, i in enumerate(starts):
            end = starts[k + 1] if k + 1 < len(starts) else len(lines)
            cand = [j for j in range(i, end) if num.match(lines[j][1].strip()) and j not in used]
            if not cand and i > 0 and num.match(lines[i - 1][1].strip()) and i - 1 not in used:
                cand = [i - 1]
            if cand:
                j = cand[0]
                used.add(j)
                p, l = lines[i]
                lines[i] = (p, 'مادة ' + num.match(lines[j][1].strip()).group(1) + ' - ' + l[5:])
        out = {}
        for j, (p, l) in enumerate(lines):
            if j in used:
                l = num.match(l.strip()).group(2)
            if l.strip():
                out.setdefault(p, []).append(l)
        return [(p, '\n'.join(ls)) for p, ls in sorted(out.items())]
    if m in ('decode_t3', 'decode_tte', 'visual'):
        import decode_ahram, decode_tte
        if m == 'visual':
            return [(p, normalize(t)) for p, t in decode_tte.decode_unicode(doc['file'])]
        dec = decode_ahram if m == 'decode_t3' else decode_tte
        return [(p, normalize(t)) for p, t in dec.decode(doc['file'])]
    if m == 'ocr':
        d = os.path.join(ROOT, 'ocr', doc['id'])
        files = sorted(f for f in os.listdir(d) if f.endswith('.txt')) if os.path.isdir(d) else []
        return [(int(f[2:5]), ocr_cleanup(normalize(open(os.path.join(d, f), encoding='utf-8').read()))) for f in files]
    n = int(re.search(r'Pages:\s+(\d+)', subprocess.run(['pdfinfo', doc['file']], capture_output=True, text=True).stdout).group(1))
    out = []
    for p in range(1, n + 1):
        raw = subprocess.run(['pdftotext', '-f', str(p), '-l', str(p), doc['file'], '-'], capture_output=True, text=True).stdout
        lines = [l for l in raw.split('\n') if not CTRL.search(l)]  # lines in a broken embedded font
        t = normalize('\n'.join(lines), from_pdf_layer=True).replace('\ufffd', '')
        if doc.get('watermark'):  # "صورة إلكترونية لا يعتد بها عند التداول" scattered as short lines
            wm = 'صورةإلكترونيةلايعتدبهاعندالتداول'
            t = '\n'.join(l for l in t.split('\n') if not (l.strip() and len(l.strip()) <= 14 and l.replace(' ', '') in wm))
            t = re.sub(r'صورة إ(?:لكترونية)?\s?|(?<!\S)(?:تروني|لك|ة|يعتد بها|عند التداول)(?!\S)\s?', '', t)
        if doc.get('mirror'):
            t = t.translate(str.maketrans('()', ')('))
        if doc.get('fix_lig'):  # detached diacritics in these fonts break words: drop tashkeel
            t = re.sub(r'مكر\s*ً?\s*را', 'مكررًا', t)
            t = re.sub('[\u064b-\u0652]', '', t)
            t = '\n'.join(l for l in t.split('\n') if l.strip())
            t = re.sub(r'[ \t]+', ' ', t)
        out.append((p, t))
    return out


def ocr_cleanup(t):
    t = t.replace('|', '').replace('“', '"').replace('”', '"')
    t = re.sub(r'[ء-ي]\s*$', lambda m: m.group(0), t)
    return t


# ---------- OCR word repair against a clean legal vocabulary ----------
CONFUSE = [('ش', 'ث'), ('ث', 'ش'), ('ه', 'ة'), ('ة', 'ه'), ('ي', 'ى'), ('ى', 'ي'), ('ر', 'ز'), ('ز', 'ر'), ('د', 'ذ'), ('ذ', 'د'),
           ('ح', 'ج'), ('ج', 'ح'), ('خ', 'ح'), ('ف', 'ق'), ('ق', 'ف'), ('ب', 'ت'), ('ت', 'ب'), ('ن', 'ت'), ('ت', 'ن'), ('ي', 'ب'),
           ('ء', 'ئ'), ('أ', 'ا'), ('ا', 'أ'), ('إ', 'ا'), ('ل', 'ك')]


def fix_ocr_words(text, vocab):
    fixed = Counter()

    def repl(m):
        w = m.group(0)
        if len(w) < 4 or vocab.get(w, 0) >= 2:
            return w
        if len(w) >= 7:  # OCR often drops the space between words: "الوزاراتالمعنية"
            best_split, bscore = None, 0
            for i in range(3, len(w) - 2):
                a, b = w[:i], w[i:]
                sc = min(vocab.get(a, 0), vocab.get(b, 0))
                if sc > bscore and sc >= 3:
                    best_split, bscore = a + ' ' + b, sc
            if best_split:
                fixed[w + '→' + best_split] += 1
                return best_split
        best, bs = w, vocab.get(w, 0)
        for i, ch in enumerate(w):
            for a, b in CONFUSE:
                if ch == a:
                    c = w[:i] + b + w[i + 1:]
                    if vocab.get(c, 0) > max(bs, 4):
                        best, bs = c, vocab[c]
        if best != w:
            fixed[w + '→' + best] += 1
        return best
    return re.sub(r'[\u0621-\u064a]+', repl, text), fixed


# ---------- lam-ligature repair (fonts that extract "لا/لإ/لج…" reversed as "ال/إل/جل…") ----------
LIG = 'اأإآجحخمى'


def fix_lig(text, vocab):
    def repl(m):
        w = m.group(0)
        pos = [i for i in range(len(w) - 1) if w[i] in LIG and w[i + 1] == 'ل']
        if not pos or len(pos) > 4:
            return w
        best, score = w, vocab.get(w, 0) * 1.5
        for mask in range(1, 1 << len(pos)):
            c = list(w)
            ok = True
            for b, i in enumerate(pos):
                if mask >> b & 1:
                    if i + 1 < len(c) and c[i] in LIG:
                        c[i], c[i + 1] = c[i + 1], c[i]
                    else:
                        ok = False
            c = ''.join(c)
            sc = vocab.get(c, 0)
            if ok and sc > score and sc >= 2:
                best, score = c, sc
        return best
    t = re.sub(r'(?<![\u0621-\u064a])([وفبك]?)ا([اأإآمجحخ])ل(?=[\u0621-\u064a])', r'\1ال\2', text)  # "املبحث" → "المبحث"
    t = re.sub(r'[\u0621-\u064a]+', repl, t)
    return re.sub(r'(?<![\u0621-\u064a])ال(?![\u0621-\u064a])', 'لا', t)  # standalone "ال" is always "لا"


def fix_transpose(text, vocab, min_freq=3):
    """General repair for fonts whose two-letter ligatures extract reversed ("يف"→"في", "بني"→"بين", "الترشيعات"→"التشريعات")."""
    cache = {}

    def best_for(w):
        if w in cache:
            return cache[w]
        base = vocab.get(w, 0)
        best, bs = w, base
        if base < 2:
            cands = set()
            for i in range(len(w) - 1):
                c1 = w[:i] + w[i + 1] + w[i] + w[i + 2:]
                cands.add(c1)
                for j in range(i + 2, len(c1) - 1):
                    cands.add(c1[:j] + c1[j + 1] + c1[j] + c1[j + 2:])
            for c in cands:
                sc = vocab.get(c, 0)
                if sc > bs and sc >= min_freq:
                    best, bs = c, sc
        cache[w] = best
        return best
    t = re.sub(r'[\u0621-\u064a]+', lambda m: best_for(m.group(0)), text)
    return re.sub(r'(?<![\u0621-\u064a])ال(?![\u0621-\u064a])', 'لا', t)


def _vocab_fix(text, vocab, cands):
    cache = {}

    def best(w):
        if w not in cache:
            out, bs = w, vocab.get(w, 0)
            if bs < 2:
                for c in cands(w):
                    if vocab.get(c, 0) > max(bs, 2):
                        out, bs = c, vocab[c]
            cache[w] = out
        return cache[w]
    return re.sub(r'[\u0621-\u064a]+', lambda m: best(m.group(0)), text)


def fix_dropped_alef(text, vocab):
    """Fonts that drop the alef of the lam-alef ligature: "الول"→"الأول", "ول"→"ولا"."""
    return _vocab_fix(text, vocab, lambda w: [w[:i + 1] + a + w[i + 1:] for i, ch in enumerate(w) if ch == 'ل' for a in 'اأإآ'])


def fix_kashida_b(text, vocab):
    """Fonts whose kashida glyph extracts as "ب": "سبنة"→"سنة", "عبذر"→"عذر"."""
    def cands(w):
        idx = [i for i in range(1, len(w)) if w[i] == 'ب'][:4]
        return [''.join(ch for i, ch in enumerate(w) if not (i in idx and m >> idx.index(i) & 1)) for m in range(1, 1 << len(idx))]
    return _vocab_fix(text, vocab, cands)


# ---------- kaf repair for fonts that map initial/medial kaf to alef-madda ----------
def build_vocab(texts):
    c = Counter()
    for t in texts:
        c.update(re.findall(r'[\u0621-\u064a]+', t))
    return c


LEGIT_MADDA = re.compile(r'^(?:و|ف|ب|ل|ك)?(?:ال|لل)?آ(?:خر|ثار|تي|تية|تيين|تيتين|تيان|لات|لة|لية|جال|داب|مر|مرة|باء|ن$|ل$)'
                         r'|شآت|مآل|مآخذ')


def fix_kaf(text, vocab):
    def repl(m):
        w = m.group(0)
        if 'آ' not in w:
            return w
        idx = [i for i, ch in enumerate(w) if ch == 'آ']
        best, score = w, vocab.get(w, 0) * 1.5
        for mask in range(1, 1 << len(idx)):
            cand = list(w)
            for b, i in enumerate(idx):
                if mask >> b & 1:
                    cand[i] = 'ك'
            cand = ''.join(cand)
            s = vocab.get(cand, 0)
            if s > score:
                best, score = cand, s
        if score == 0 and not LEGIT_MADDA.search(w):  # unseen word: alef-madda only in known stems
            best = w.replace('آ', 'ك')
        return best
    return re.sub(r'[\u0621-\u064a]+', repl, text)


# ---------- article segmentation ----------
def ar_int(s):
    return int(s.translate(AR_DIGITS))


def segment(doc, pages):
    articles, headings, preamble = [], {}, []
    cur = None
    expected = 1
    issuance_mode = False
    pending_heading = None
    closing, interlude = [], []
    after_closing = closing_signed = False
    for pno, text in pages:
        for line in text.split('\n'):
            line = re.sub(r'^-{2,}\s*', '', line.strip())
            if not line or HEADER_JUNK.search(line):
                continue
            key_line = search_key(line)
            h = HEAD.match(line)
            if h and len(line) < 90:
                level = h.group(1)
                headings = {k: v for k, v in headings.items() if order(k) < order(level)}
                headings[level] = (level + ' ' + h.group(2) + (' ' + h.group(3) if h.group(3) else '')).strip(' ()')
                rest = h.group(4).strip(' ()-–')
                if rest:
                    headings[level] += ' — ' + rest
                    pending_heading = None
                else:
                    pending_heading = level
                continue
            if doc['method'] == 'ocr' and not doc.get('amending'):
                line = OCR_LEAD.sub('', line)
                key_line = search_key(line).replace('الماده', 'الماده ').replace('  ', ' ')
                if OCR_HDR.match(line) and len(line) <= 24:
                    digits = re.findall(r'\d+', line.translate(AR_DIGITS))
                    parsed = int(digits[0]) if digits else None
                    suffix = 'مكرر' if 'مكرر' in line else None
                    if suffix:
                        n = expected - 1
                    elif parsed is not None and expected <= parsed <= expected + 2:
                        n = parsed
                    else:
                        n = expected
                    if issuance_mode and n == 1:
                        issuance_mode = False
                    cur = new_article(articles, 'main', n, suffix, 'مادة ' + str(n) + (' مكرر' if suffix else ''), headings, pno, '')
                    if parsed != n and not suffix:
                        cur['numberInferred'] = True
                    expected = n + 1
                    after_closing = closing_signed = False
                    pending_heading = None
                    continue
            m_iss = ISS.match(key_line)
            if m_iss and ORD.get(m_iss.group(1).strip()) and len(line) < 120:
                n = ORD[m_iss.group(1).strip()]
                cur = new_article(articles, 'issuance', n, None, 'المادة ' + line_ordinal(line) + ' (إصدار)', headings, pno, m_iss.group(2))
                issuance_mode = True
                pending_heading = None
                after_closing = closing_signed = False
                continue
            if len(line) < 30:  # a stray glyph from the neighbouring line glued to a heading: "لمادة (٥)"
                line = re.sub(r'^[\u0621-\u064a](?=ماد[ةه]\s*\()', '', line)
            if doc.get('consolidated'):  # compiler style "(المادة 107:) 1- …" / "(المادة 2 إصدار:)"
                lt = line.translate(AR_DIGITS)
                mc = COMP.match(lt)
                raqm = bool(re.match(r'^[\s)(]*ماد[ةه]\s*رقم', lt))
                if mc and (':' in lt[:30] or mc.group(4) or raqm) and len(lt) > 8:
                    n = int(mc.group(2))
                    suffix = (mc.group(3) or '').strip() or None
                    tail = mc.group(5) or ''
                    ordinal = raqm and re.match(r'^[\s—\-]*الماد[ةه]\s+ال(?!\d)', tail)
                    if raqm:
                        tail = re.sub(r'^[\s—\-]*الماد[ةه]\s*\S+(?:\s*مكرر\S*)?', '', tail)
                    if mc.group(4) or ordinal:
                        cur = new_article(articles, 'issuance', n, None, f'المادة {n} (إصدار)', headings, pno, '')
                        issuance_mode = True
                    else:
                        cur = new_article(articles, 'main', n, suffix, 'مادة ' + str(n) + (' ' + suffix if suffix else ''), headings, pno, '')
                    body = re.sub(r'^[\s:)(]+', '', tail)
                    if mc.group(1):
                        body = f'{mc.group(1)}- {body}'
                    if body:
                        cur['lines'].append(body)
                    expected = n + 1
                    after_closing = closing_signed = False
                    pending_heading = None
                    continue
            m = None if doc.get('amending') else ART.match(line.translate(AR_DIGITS))
            if m and accept_header(m, line, expected, cur):
                n = int(m.group(2))
                suffix = (m.group(3) or '').strip() or None
                if issuance_mode and n == 1:
                    issuance_mode = False
                label = 'مادة ' + str(n) + (' ' + suffix if suffix else '')
                body = (m.group(4) or '').strip()
                note = AMEND_NOTE.match(body)
                if note:
                    body = body[note.end():]
                body = re.sub(r'^[\-–:.)\s]+', '', body)
                cur = new_article(articles, 'main', n, suffix, label, headings, pno, body)
                if note:
                    cur['amendmentNote'] = note.group(1).strip()
                after_closing = closing_signed = False
                expected = n + 1 if not suffix else n + 1
                pending_heading = None
                continue
            if pending_heading and len(line) < 90 and not line.startswith('('):
                headings[pending_heading] += ' — ' + line.strip(' ()')
                pending_heading = None
                continue
            pending_heading = None
            if CLOSING.match(line):
                closing.append(line)
                cur = None
                after_closing = True
                continue
            if after_closing and cur is None and len(line) < 40 and not closing_signed:
                closing.append(line)  # signer
                closing_signed = True
                continue
            if cur is None:
                (preamble if not closing else interlude).append(line)
            else:
                cur['lines'].append(line)
    for a in articles:
        a['text'] = join_lines(a.pop('lines'))
    restarts = [i for i, a in enumerate(articles) if i and a['part'] == 'main' and a['number'] == 1 and not a['suffix']]
    if restarts and 0 < restarts[-1] <= 20 and all(a['part'] == 'main' for a in articles[:restarts[-1]]):
        for a in articles[:restarts[-1]]:
            a['part'], a['key'], a['label'] = 'issuance', 'i' + str(a['number']), a['label'] + ' (إصدار)'
        rest = articles[restarts[-1]:]
        for a in rest:
            if '~' in a['key']:
                base = a['key'].split('~')[0]
                if not any(b['key'] == base for b in rest if b is not a):
                    a['key'] = base
    # a second numbering restart deep into the document = the attached executive regulation
    later = [i for i, a in enumerate(articles) if i > 20 and a['part'] == 'main' and a['number'] == 1 and not a['suffix']]
    if doc.get('consolidated') and later:
        for a in articles[later[0]:]:
            if a['part'] == 'main':
                a['part'], a['key'] = 'regulation', 'r' + a['key'].split('~')[0] + ('~' + a['key'].split('~')[1] if '~' in a['key'] else '')
                a['label'] += ' (اللائحة التنفيذية)'
        seen = {}
        for a in articles:
            k = a['key'].split('~')[0]
            seen[k] = seen.get(k, 0) + 1
            a['key'] = k if seen[k] == 1 else f'{k}~{seen[k]}'
    if articles and all(a['part'] == 'issuance' for a in articles):
        for a in articles:  # amending laws have no attached law: their articles are the law itself
            a['part'] = 'main'
            a['key'] = a['key'][1:]
            a['label'] = a['label'].replace(' (إصدار)', '')
    return {'preamble': '\n'.join(preamble), 'closing': '\n'.join(closing), 'interlude': '\n'.join(interlude)}, articles


def pdf_pages(f):
    m = re.search(r'Pages:\s+(\d+)', subprocess.run(['pdfinfo', f], capture_output=True, text=True).stdout)
    return int(m.group(1)) if m else None


def order(level):
    return ['الكتاب', 'الباب', 'الفصل', 'الفرع', 'القسم'].index(level)


def line_ordinal(line):
    m = re.search(r'ماد[ةه]\s+(ال[^\s)]+(?:\s+عشر[ةه])?)', line)
    return m.group(1) if m else ''


AMEND_NOTE = re.compile(r'^\(?\s*([^()]{0,90}?(?:معدل|مستبدل|مضاف|أضيف|اضيف|ملغ|ألغي|الغي|حذف|أعيد)[^()]*(?:\([^()]*\)[^()]*)*)\)\s*')


def accept_header(m, line, expected, cur):
    n = int(m.group(2))
    rest = (m.group(4) or '').strip()
    if m.group(1):
        return cur is None or n == 1 or expected - 1 <= n <= expected + 150
    strong = rest == '' or re.match(r'^[\-–:.]', rest) or re.match(r'^\(\s*[\d٠-٩]', rest) \
        or bool(m.group(3)) or bool(AMEND_NOTE.match(rest))
    if re.match(r'^(من|فى|في|بالقانون|السابق|المشار)', rest):
        return False
    if not strong:
        return bool(re.match(r'^\(?\s*ماد', line)) and n == expected  # weak: bare "مادة N text"
    if cur is None or n == 1:
        return True
    return expected - 1 <= n <= expected + 150


def new_article(articles, part, n, suffix, label, headings, pno, first):
    key = ('i' if part == 'issuance' else '') + str(n) + ('-' + search_key(suffix).replace(' ', '') if suffix else '')
    base, k = key, 2
    while any(a['key'] == key for a in articles):
        key = f'{base}~{k}'
        k += 1
    a = {'key': key, 'part': part, 'number': n, 'suffix': suffix, 'label': label,
         'heading': ' / '.join(headings.values()) or None, 'page': pno, 'lines': [first] if first else []}
    articles.append(a)
    return a


def join_lines(lines):
    """Join wrapped lines; keep paragraph breaks before numbered items / new sentences."""
    out = ''
    for l in lines:
        if not out:
            out = l
        elif re.match(r'^(\(?[\dأ-ي٠-٩]{1,2}\)|[\d٠-٩]{1,2}\s*[-–]|[-–]\s*[\d٠-٩]|أولا|ثانيا|ثالثا|رابعا)', l) or out.endswith(('.', ':', '؛')):
            out += '\n' + l
        else:
            out += ' ' + l
    return out.strip()


def main(ids):
    os.makedirs(OUT, exist_ok=True)
    texts = {}
    vocab_cache = os.path.join(ROOT, 'vocab.pkl')
    for d in ([] if os.path.exists(vocab_cache) else DOCS):
        if d['method'] == 'text' and not any(d.get(k) for k in ('fix_kaf', 'fix_lig', 'fix_alef', 'fix_kashida', 'watermark')):
            texts[d['id']] = '\n'.join(t for _, t in page_texts(d))
        elif os.path.exists(os.path.join(ROOT, 'manual', d['id'] + '.txt')):
            texts[d['id']] = '\n'.join(t for _, t in page_texts(dict(d, method='manual')))
    import pickle
    if os.path.exists(vocab_cache):
        vocab = pickle.load(open(vocab_cache, 'rb'))
    else:
        vocab = build_vocab(texts.values())
    civil = [d for d in DOCS if d.get('fix_kaf')]
    if not os.path.exists(vocab_cache):
        for d in civil:  # repaired civil code enlarges the vocabulary used for OCR repair
            vocab.update(build_vocab([fix_kaf('\n'.join(t for _, t in page_texts(d)), vocab)]))
        pickle.dump(vocab, open(vocab_cache, 'wb'))
    index = []
    for d in DOCS:
        if ids and d['id'] not in ids:
            if os.path.exists(f"{OUT}/{d['id']}.json"):
                index.append(json.load(open(f"{OUT}/{d['id']}.json"))['meta'])
            continue
        if d['method'] == 'ocr' and os.path.exists(os.path.join(ROOT, 'manual', d['id'] + '.txt')):
            d = dict(d, method='manual')
        pages = page_texts(d)
        if not pages:
            print('skip (no pages yet)', d['id'])
            index.append({'id': d['id'], 'title': d['title'], 'subtitle': d['subtitle'], 'number': d['number'], 'year': d['year'],
                          'categoryId': d['category'], 'published': False, 'textQuality': 'PENDING', 'articleCount': 0,
                          'suggestedStatus': d.get('suggested'), 'suggestedStatusNote': d.get('suggested_note'),
                          'status': d.get('status'), 'statusNote': d.get('status_note'), 'sourceFile': os.path.basename(d['file']),
                          'missingArticleNumbers': [], 'notes': d.get('note_text')})
            continue
        repairs = Counter()
        if d.get('fix_kaf'):
            pages = [(p, fix_kaf(t, vocab)) for p, t in pages]
        if d.get('fix_lig'):
            pages = [(p, fix_lig(t, vocab)) for p, t in pages]
        if d.get('fix_kashida'):
            pages = [(p, fix_kashida_b(t, vocab)) for p, t in pages]
        if d.get('fix_alef'):
            pages = [(p, fix_dropped_alef(t, vocab)) for p, t in pages]
        if d['method'] == 'ocr':
            fixed_pages = []
            for p, t in pages:
                t2, f = fix_ocr_words(t, vocab)
                repairs.update(f)
                fixed_pages.append((p, t2))
            pages = fixed_pages
        if d.get('consolidated') and d['method'] in ('decode_tte', 'text') and not d.get('fixed_method'):
            alt = dict(d, method='text' if d['method'] == 'decode_tte' else 'decode_tte', fix_lig=True, fix_alef=True)
            pa = page_texts(alt)
            pa = [(p, fix_dropped_alef(fix_lig(t, vocab), vocab)) for p, t in pa] if alt['method'] == 'text' else pa
            def score(arts):
                nums = [a['number'] for a in arts if a['part'] == 'main' and not a['suffix']]
                return len(set(nums)) - 2 * (len(set(range(1, max(nums) + 1)) - set(nums)) if nums else 0)
            try:
                _, a1 = segment(d, pages)
                _, a2 = segment(alt, pa)
                if score(a2) > score(a1):
                    d, pages = alt, pa
            except Exception as e:
                print('alt failed', d['id'], e)
        if d.get('single'):
            parts = {'preamble': '', 'closing': '', 'interlude': ''}
            arts = [{'key': '1', 'part': 'main', 'number': 1, 'suffix': None, 'label': 'نص ' + ('الاستدراك' if d.get('instrument') == 'CORRECTION' else 'الوثيقة'),
                     'heading': None, 'page': 1, 'text': join_lines([l for _, t in pages for l in t.split('\n') if l.strip()])}]
        else:
            parts, arts = segment(d, pages)
        quality = {'manual': 'TRANSCRIBED', 'text': 'TEXT_LAYER', 'ocr': 'OCR', 'decode_t3': 'DECODED', 'decode_tte': 'DECODED', 'visual': 'TEXT_LAYER'}[d['method']]
        main_nums = [a['number'] for a in arts if a['part'] == 'main' and not a['suffix']]
        gaps = sorted(set(range(1, max(main_nums) + 1)) - set(main_nums)) if main_nums else []
        meta = {
            'id': d['id'], 'type': 'LAW', 'instrument': d.get('instrument', 'LAW'), 'number': d['number'], 'year': d['year'],
            'title': d['title'], 'subtitle': d['subtitle'], 'categoryId': d['category'],
            'status': d.get('status'), 'statusNote': d.get('status_note'),
            'suggestedStatus': d.get('suggested'), 'suggestedStatusNote': d.get('suggested_note'),
            'amends': d.get('amends', []), 'amendedBy': d.get('amended_by', []),
            'gazette': d.get('gazette'), 'issueDate': d.get('issue_date'), 'sourceFile': os.path.basename(d['file']),
            'textQuality': quality, 'verified': quality != 'OCR' and not d.get('fix_lig'), 'pageCount': len(pages),
            'articleCount': len(arts), 'missingArticleNumbers': gaps[:200], 'notes': d.get('note_text'),
            'sourcePages': pdf_pages(d['file']), 'published': quality != 'OCR' and not d.get('draft'), 'ocrRepairs': sum(repairs.values()),
        }
        doc = {'meta': meta, **parts, 'articles': arts}
        json.dump(doc, open(f"{OUT}/{d['id']}.json", 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
        index.append(meta)
        print(f"{d['id']:18} {quality:11} pages={len(pages):3} articles={len(arts):4} gaps={len(gaps)} {gaps[:12]}")
    order = {d['id']: i for i, d in enumerate(DOCS)}
    index.sort(key=lambda m: order.get(m['id'], 999))
    json.dump({'categories': CATEGORIES, 'laws': index}, open(f'{OUT}/index.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    write_review(index)


STATUS_AR = {'IN_FORCE': 'سارٍ', 'AMENDED': 'معدّل', 'REPEALED': 'ملغى', None: '—'}
QUALITY_AR = {'DECODED': 'فك ترميز خط الملف (نص الناشر الأصلي)', 'TEXT_LAYER': 'نص رقمي من الملف', 'TRANSCRIBED': 'تفريغ يدوي من صورة الجريدة', 'OCR': 'OCR آلي (مسودة)',
              'PENDING': 'لم يُعالج'}


def write_review(index):
    L = ['# مراجعة الدفعة الأولى من التشريعات', '',
         'هذا الملف يُولَّد آليًا من `backend/tools/ingest/ingest.py`. الحالة (سارٍ/معدّل/ملغى) لا تُعرض في التطبيق إلا إذا كانت مؤكدة؛',
         'الحالات المقترحة تنتظر تأكيد الأدمن من لوحة التحكم قبل اعتمادها.', '',
         '| المعرّف | التشريع | جودة النص | المواد | منشور | الحالة | ملاحظات |', '|---|---|---|---|---|---|---|']
    for m in index:
        st = STATUS_AR.get(m.get('status'))
        if not m.get('status') and m.get('suggestedStatus'):
            st = 'مقترح: ' + STATUS_AR[m['suggestedStatus']]
        notes = []
        if m.get('statusNote'):
            notes.append(m['statusNote'])
        if m.get('suggestedStatusNote'):
            notes.append(m['suggestedStatusNote'])
        if m.get('missingArticleNumbers'):
            g = m['missingArticleNumbers']
            notes.append('أرقام غير موجودة في الملف: ' + compress(g))
        if m.get('notes'):
            notes.append(m['notes'])
        if m.get('supersededBy'):
            notes.append(f"نسخة قديمة/أصلية — النص المعتمد: {m['supersededBy']}")
        if m.get('textQuality') == 'OCR' and m.get('sourcePages') and m.get('pageCount', 0) < m['sourcePages']:
            notes.append(f"OCR جزئي: {m['pageCount']} من {m['sourcePages']} صفحة")
        L.append(f"| `{m['id']}` | {m['title']} ({m['number']}/{m['year']}) | {QUALITY_AR[m['textQuality']]} | {m.get('articleCount', 0)} | "
                 f"{'نعم' if m.get('published') else 'لا'} | {st} | {'<br>'.join(notes) or '—'} |")
    L += ['', '## ما يحتاج تدخلًا', '',
          '- **OCR آلي (مسودة، غير منشور):** الأرقام داخل النص وأرقام المواد في نسخ الجريدة المصوّرة تُقرأ آليًا بأخطاء؛ لا تُنشر هذه النصوص ولا يستشهد بها الـ AI '
          'حتى تُستبدل بنسخة نصية (Word/PDF نصي) أو تُراجع يدويًا. الأفضل رفع نسخة نصية من: قانون التجارة 17/1999، قانون الاستثمار 72/2017 ولائحته 2310/2017، '
          'التأمين الاجتماعي 79/1975، 102/2012، 103/2012.',
          '- **لم يُعالج:** 11/1991 و111/1981 مسح ضوئي ضعيف — مطلوب نسخة أوضح أو نسخة نصية (وعنوان القانون 111/1981 للتأكد من محتواه).',
          '- **القانون المدني 131/1948:** الملف المرفوع لا يتضمن المواد 54–80 و389–417 (مواد الإثبات الملغاة بقانون الإثبات) و1022. '
          'تم إصلاح خطأ خط الملف الذي كان يحوّل حرف (ك) إلى (آ) آليًا مع مراجعة الكلمات الشرعية (آخر، آثار، منشآت...).',
          '- **147/1984:** النص المرفوع هو النص الأصلي لسنة 1984؛ البند (19) الذي عدّله القانون 148/2026 أضيف بتعديلات لاحقة غير مرفوعة. '
          'كلمة واحدة غير واضحة في المادة الثانية مُعلَّمة بين قوسين. الجداول حُوّلت لنص مع ذكر وحدة العملة لكل بند.',
          '- **الدفعة الثانية (ضرائب):** 79/2016 قانون مؤقت جُدّد آخرها ضمن الملفات حتى 31/12/2022 (153/2022)؛ نص المادة 8 من قانون الدخل '
          'الذي وضعه 175/2023 حل محله نص 7/2024 (الشرائح والإعفاء الشخصي 20 ألف جنيه) ثم قد تعدّله قوانين لاحقة (30/2023 سابق، 151/2026 لاحق) — '
          'يُراجع أحدث نص قبل الإجابة. جداول الشرائح أعيد عرضها نصًا حسب فئة الدخل دون تغيير القيم. 199/2020 خط معكوس الحروف أُصلح آليًا ويُعرض غير موثّق. '
          'قرار وزير المالية 172/2015 (17 صفحة مصورة) مسودة OCR.',
          '- **نماذج الصحف والإعلانات:** 5 نماذج قابلة للملء في `content/templates/` (إعلان بالطلبات العارضة، إعادة الدعوى للرول، إعلان الجهات الخاصة م13، '
          'إشكال في تنفيذ حكم مدني، استئناف حكم م221). النصوص القانونية المقتبسة داخلها من النموذج الأصلي؛ يفضل إضافة قانون المرافعات 13/1968 للمكتبة.',
          '- **البحث المنشور (IJDJL 7/2):** مُعالَج في `content/books/` لكنه غير منشور حتى تأكيد حق الاستخدام.',
          '- **95/1995:** ملغى بالقانون 176/2018، ومادته 30 كانت قد أُلغيت بالمادة الرابعة من مواد إصدار القانون 8/1997 (ثابت من نص 8/1997 المرفوع). '
          'يبقى مفيدًا للعقود المبرمة في ظله.', '']
    open(f'{OUT}/REVIEW.md', 'w', encoding='utf-8').write('\n'.join(L))


def compress(nums):
    out, i = [], 0
    while i < len(nums):
        j = i
        while j + 1 < len(nums) and nums[j + 1] == nums[j] + 1:
            j += 1
        out.append(str(nums[i]) if i == j else f'{nums[i]}–{nums[j]}')
        i = j + 1
    return '، '.join(out)


if __name__ == '__main__':
    main(sys.argv[1:])
