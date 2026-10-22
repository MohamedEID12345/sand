"""Arabic text normalization for Egyptian legal PDFs (text layer or OCR)."""
import re, unicodedata

BIDI = dict.fromkeys(map(ord, '\u200e\u200f\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069\ufeff'), None)
# Spacing tashkeel presentation forms -> combining marks (NFKC would insert a space before them)
TASHKEEL_PF = {0xFE70: '\u064b', 0xFE71: '\u064b', 0xFE72: '\u064c', 0xFE74: '\u064d', 0xFE76: '\u064e', 0xFE77: '\u064e',
               0xFE78: '\u064f', 0xFE79: '\u064f', 0xFE7A: '\u0650', 0xFE7B: '\u0650', 0xFE7C: '\u0651', 0xFE7D: '\u0651',
               0xFE7E: '\u0652', 0xFE7F: '\u0652'}
LETTER_FIX = {'\u06cc': '\u064a', '\u06a9': '\u0643', '\u0640': None, '\u00a0': ' ',
              **{chr(0x06f0 + i): chr(0x0660 + i) for i in range(10)}}
AR_DIGITS = str.maketrans('٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹', '01234567890123456789')
TASHKEEL = re.compile('[\u064b-\u0652\u0670]')


MIRROR = str.maketrans('()[]{}', ')(][}{')


def fix_bidi_runs(line: str) -> str:
    """pdftotext emits RTL paragraphs with LTR runs (U+202A..U+202C) in visual order and mirrored
    brackets. Neutral runs (digits/punctuation) are reversed token-wise; brackets are swapped."""
    line = re.sub('[\ue000-\uf8ff]', '', line)  # private-use glyphs (detached diacritics)
    if '\u202b' not in line:
        return line
    out = []
    pos = 0
    for m in re.finditer('\u202a([^\u202a\u202c]*)\u202c', line):
        out.append(line[pos:m.start()].translate(MIRROR))
        run = m.group(1)
        if not run.strip():
            pos = m.end()
            continue
        if re.search('[A-Za-z]', run):
            out.append(' ' + run.strip() + ' ')
        else:
            toks = re.findall(r'[()\[\]{}]|[^\s()\[\]{}]+', run)
            out.append(' ' + ' '.join(t.translate(MIRROR) for t in reversed(toks)) + ' ')
        pos = m.end()
    out.append(line[pos:].translate(MIRROR))
    return ''.join(out)


def normalize(text: str, from_pdf_layer: bool = False) -> str:
    if from_pdf_layer:
        text = '\n'.join(fix_bidi_runs(l) for l in text.split('\n'))
    text = re.sub('[\ue000-\uf8ff]', '', text).translate(BIDI)
    text = ''.join(TASHKEEL_PF.get(ord(c), c) for c in text)
    text = unicodedata.normalize('NFKC', text)
    text = text.translate(str.maketrans(LETTER_FIX))
    # a lone prefix letter split from its word by a dropped diacritic glyph: "ي  عفى" -> "يعفى"
    text = re.sub(r'(?<![\u0621-\u064a])([يتنأ])[ \t]{2,}(?=[\u0621-\u064a])', r'\1', text)
    text = re.sub(r'[ \t]+', ' ', text)
    text = re.sub(r'([(\[]) ', r'\1', text)
    text = re.sub(r' ([)\]])', r'\1', text)
    text = re.sub(r' ([،؛.:!؟])', r'\1', text)
    return '\n'.join(l.strip() for l in text.split('\n'))


def search_key(text: str) -> str:
    """Aggressive normalization for indexing/search: no tashkeel, unified alef/yaa/taa marbuta, ASCII digits."""
    t = TASHKEEL.sub('', text).translate(AR_DIGITS)
    t = re.sub('[إأآٱ]', 'ا', t)
    t = t.replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
    return re.sub(r'\s+', ' ', t).strip()
