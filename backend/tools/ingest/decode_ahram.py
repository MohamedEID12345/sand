"""Decoder for gazette PDFs set with visual-order Arabic Type3 fonts (T2/T3/T4/T5) whose text layer extracts as
Mac-Roman mojibake ("≤∞±∑ WM…"). Glyph codes were identified visually from the font and calibrated against the
library vocabulary. Lines are rebuilt from glyph positions, reversed to logical order, digit runs restored."""
import re
import pymupdf as fitz

MAP = {
    1: 'ب', 2: 'ت', 3: 'ث', 4: 'ج', 5: 'ح', 6: 'خ', 7: 'س', 8: 'ش', 11: 'ط', 12: 'ظ', 14: 'ع', 15: 'غ', 16: 'ف', 17: 'ق',
    18: 'ك', 19: 'ل', 20: 'م', 21: 'ن', 22: 'ه', 23: 'ي', 24: 'ئ',
    25: 'ب', 26: 'ت', 27: 'ث', 28: 'ج', 29: 'ح', 30: 'خ', 31: 'س', 47: 'نم', 48: 'بم', 49: 'يم', 50: 'مم', 57: 'تم',
    65: 'ش', 66: 'ص', 67: 'ض', 68: 'ط', 69: 'ظ', 70: 'ع', 71: 'غ', 72: 'ف', 73: 'ق', 74: 'ك', 75: 'ل', 76: 'م', 77: 'ن',
    78: 'ه', 79: 'ي', 122: 'ئ', 123: 'ض', 124: 'ي', 125: 'ي', 127: 'ص',
    34: 'تج', 36: 'نج', 37: 'تح', 38: 'تج', 39: 'لج', 40: 'لح', 41: 'لخ', 42: 'لم', 45: 'تم', 46: 'يم', 53: 'ين',
    58: 'لمج', 59: 'لمح', 60: 'لمخ', 95: 'لأ', 153: 'لا', 154: 'لا', 156: 'لأ', 157: 'لإ', 158: 'لإ', 159: 'لآ',
    80: 'آ', 81: 'أ', 82: 'ؤ', 83: 'إ', 84: 'ئ', 85: 'ا', 86: 'ب', 87: 'ة', 88: 'ت', 89: 'ث', 90: 'ج', 96: 'ح', 97: 'خ',
    98: 'د', 99: 'ذ', 100: 'ر', 101: 'ز', 102: 'س', 103: 'ش', 104: 'ص', 105: 'ض', 106: 'ط', 107: 'ظ', 108: 'ع', 109: 'غ',
    110: 'ف', 111: 'ق', 112: 'ك', 113: 'ل', 114: 'م', 115: 'ن', 116: 'ه', 117: 'و', 118: 'ى',
    193: 'ء', 194: 'آ', 195: 'أ', 196: 'ؤ', 197: 'إ', 198: 'ئ', 199: 'ا', 200: 'ب', 201: 'ة', 202: 'ت', 203: 'ث', 204: 'ج',
    205: 'ح', 206: 'خ', 207: 'د', 208: 'ذ', 209: 'ر', 210: 'ز', 211: 'س', 212: 'ش', 213: 'ص', 214: 'ض', 215: 'ط', 216: 'ظ',
    217: 'ع', 218: 'غ', 225: 'ف', 226: 'ق', 227: 'ك', 228: 'ل', 229: 'م', 230: 'ن', 231: 'ه', 232: 'و', 233: 'ى', 234: 'ي',
    176: '٠', 177: '١', 178: '٢', 179: '٣', 180: '٤', 181: '٥', 182: '٦', 183: '٧', 184: '٨', 185: '٩',
    32: ' ', 91: '،', 172: '،', 173: '-', 174: '.', 186: ':', 187: '؛', 162: '"', 165: '٪', 168: '(', 169: ')',
    63: '', 224: '', 235: '', 237: '', 238: '', 239: '', 241: '', 62: '',
}
ARABIC_FONTS = re.compile(r'^T\d+$')
DIGRUN = re.compile(r'[٠-٩0-9]+(?:[/.,٫٬:\-][٠-٩0-9]+)*')


def page_text(page, unknown=None):
    rows = {}
    for sp in page.get_texttrace():
        arabic = bool(ARABIC_FONTS.match(sp['font']))
        for ch in sp['chars']:
            code, (x, y) = ch[1], ch[2]
            if arabic:
                s = MAP.get(code)
                if s is None:
                    if unknown is not None:
                        unknown[code] = unknown.get(code, 0) + 1
                    s = ''
            else:
                s = chr(ch[0]) if 0 < ch[0] < 0xFFFD else ''
            rows.setdefault(round(y / 2.0), []).append((x, s, arabic, code))
    lines = []
    for key in sorted(rows):
        items = []
        for it in sorted(rows[key], key=lambda t: t[0]):  # fake-bold draws each glyph several times
            if items and it[3] == items[-1][3] and it[0] - items[-1][0] < 0.8:
                continue
            items.append(it)
        if not any(a for _, _, a, _ in items):
            lines.append(''.join(s for _, s, _, _ in items))
            continue
        logical = ''.join(s for _, s, _, _ in reversed(items))
        logical = DIGRUN.sub(lambda m: m.group(0)[::-1], logical)
        lines.append(re.sub(r'\s+', ' ', logical).strip())
    return '\n'.join(l for l in lines if l)


def decode(path, first=1, last=None, unknown=None):
    d = fitz.open(path)
    last = last or len(d)
    return [(i + 1, page_text(d[i], unknown)) for i in range(first - 1, last)]
