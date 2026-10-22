"""Decoders for PDFs whose Unicode text layer is unusable but glyph geometry is intact:
- page_text: Windows Arabic TrueType subsets (TTE…, Arial, Arabic Transparent, Simplified Arabic): glyph ids follow the
  original font order where the Arabic block is in Presentation-Forms-B order (glyph 897 = U+FE81).
- page_text_unicode: correct Unicode stored as positioned presentation forms in visual order."""
import re, unicodedata
import pymupdf as fitz

MIRROR = {'(': ')', ')': '(', '[': ']', ']': '[', '{': '}', '}': '{', '<': '>', '>': '<'}
DIGRUN = re.compile(r'[٠-٩0-9]+(?:[/.,٫٬:\-][٠-٩0-9]+)*')


def glyph_char(g):
    if 3 <= g <= 97:
        return chr(g + 29)
    if 761 <= g <= 770:
        return chr(0x0660 + g - 761)
    if 897 <= g <= 1020:
        return unicodedata.normalize('NFKC', chr(0xFE81 + g - 897))
    return {895: 'الله', 748: '،', 751: 'ء', 752: '', 177: '-', 755: '؛', 756: '؟', 1: ' ', -1: ''}.get(g, '' if 753 <= g <= 760 else None)


def _rows_to_text(rows, arabic_test):
    out = []
    for k in sorted(rows):
        items = sorted(rows[k], key=lambda t: t[0])
        txt = ''.join(s for _, s in items)
        if not arabic_test(txt):
            out.append(txt.strip())
            continue
        logical = ''.join(MIRROR.get(s, s) for _, s in reversed(items))
        logical = DIGRUN.sub(lambda m: m.group(0)[::-1], logical)
        out.append(re.sub(r'\s+', ' ', logical).strip())
    return '\n'.join(l for l in out if l)


def page_text(page, fontpat=('TTE', 'Arial', 'Arabic Transparent', 'Simplified Arabic'), unknown=None):
    rows = {}
    for sp in page.get_texttrace():
        tte = any(fp in sp['font'] for fp in fontpat)
        for ch in sp['chars']:
            if tte:
                s = glyph_char(ch[1])
                if s is None:
                    if unknown is not None:
                        unknown[ch[1]] = unknown.get(ch[1], 0) + 1
                    s = ''
            else:
                s = chr(ch[0]) if 0 < ch[0] < 0xFFFD else ''
            rows.setdefault(round(ch[2][1] / 2.0), []).append((ch[2][0], s))
    return _rows_to_text(rows, lambda t: bool(re.search('[\u0600-\u06ff]', t)))


def page_text_unicode(page):
    rows = {}
    for sp in page.get_texttrace():
        for ch in sp['chars']:
            if 0 < ch[0] < 0xFFFD:
                rows.setdefault(round(ch[2][1] / 2.0), []).append((ch[2][0], unicodedata.normalize('NFKC', chr(ch[0]))))
    return _rows_to_text(rows, lambda t: bool(re.search('[\u0600-\u06ff]', t)))


def decode(path, first=1, last=None, unknown=None):
    d = fitz.open(path)
    last = last or len(d)
    return [(i + 1, page_text(d[i], unknown=unknown)) for i in range(first - 1, last)]


def decode_unicode(path):
    d = fitz.open(path)
    return [(i + 1, page_text_unicode(d[i])) for i in range(len(d))]
