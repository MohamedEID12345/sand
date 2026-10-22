"""Converts Word contract/form templates (dotted blanks) into fill templates (CONTENT_IMPORT.md §1).
Every blank ("……", "....", "-----", "____", "[اسم …]", "../../....") becomes a field labelled from the words before it;
lines starting with أولًا/ثانيًا… tag the party ("— الطرف الأول"). Usage: python3 _docx_to_templates.py <dir> [out]"""
import json, os, re, sys, zipfile, glob
from lxml import etree

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
FALLBACK = '{http://schemas.openxmlformats.org/markup-compatibility/2006}Fallback'
BLANK = re.compile(r'\[[^\]\n]{2,60}\]|(?:[.…_ـ\-]{3,}[\s.…_ـ\-]*)+')
DATE = re.compile(r'[.…\-_ ]{1,14}/[.…\-_ ]{1,14}/[.…\-_ ]{0,14}(?:٢٠[٠-٩]{0,2}|20\d{0,2})?[.…\-_ ]{0,8}')
PARTY = {'أول': 'الأول', 'ثاني': 'الثاني', 'ثالث': 'الثالث', 'رابع': 'الرابع', 'خامس': 'الخامس'}
CATS = [
    (r'عمل فردي|تدريب|طلب|محضر|إخطار|اخلاء طرف|استقالة|أجاز|عهدة|تعيين|نقل|الراتب', 'HR', 'شئون العاملين', ['قانون العمل رقم 14 لسنة 2025']),
    (r'وصية|هبة|اشهار', 'GIFTS_WILLS', 'الهبة والوصية', ['القانون المدني — الهبة (م 486 وما بعدها)']),
    (r'قسمة|شيوع|اتحاد ملاك', 'CO_OWNERSHIP', 'القسمة والملكية الشائعة', ['القانون المدني — الملكية الشائعة والقسمة (م 825 وما بعدها)']),
    (r'إيجار|ايجار|انتفاع|العارية', 'LEASE', 'الإيجار والانتفاع', ['القانون المدني — الإيجار (م 558 وما بعدها)']),
    (r'مقاولة|تصميم هندسي', 'CONSTRUCTION', 'المقاولات', ['القانون المدني — المقاولة (م 646 وما بعدها)']),
    (r'توريد', 'SUPPLY', 'التوريد', ['القانون المدني', 'قانون التجارة رقم 17 لسنة 1999']),
    (r'شراكة|شركة', 'PARTNERSHIP', 'الشركات والشراكات', ['القانون المدني — الشركة (م 505 وما بعدها)']),
    (r'كفالة|ضمان', 'GUARANTEES', 'الكفالة والضمان', ['القانون المدني — الكفالة (م 772 وما بعدها)']),
    (r'وكالة|توزيع|تسويق|دعاية|علامة|براءة|نموذج صناعي|مؤلف|تطبيق|موقع|تكنولوجيا|برامج|التواصل', 'COMMERCIAL', 'العقود التجارية والتقنية', ['قانون التجارة رقم 17 لسنة 1999', 'القانون المدني']),
    (r'بيع|وعد', 'SALE', 'عقود البيع', ['القانون المدني — البيع (م 418 وما بعدها)']),
    (r'.', 'CIVIL_OTHER', 'عقود مدنية أخرى', ['القانون المدني']),
]


def paragraphs(path):
    root = etree.fromstring(zipfile.ZipFile(path).read('word/document.xml'))
    for fb in list(root.iter(FALLBACK)):
        fb.getparent().remove(fb)
    out = []
    for p in root.iter(W + 'p'):
        if any(a.tag == W + 'p' for a in p.iterancestors()):
            continue
        out.append(re.sub(r'[\u200b\u200c\u200f\u200e\ufeff]', '', ''.join(t.text or '' for t in p.iter(W + 't'))).rstrip())
    while out and not out[-1].strip():
        out.pop()
    return out


def words(s):
    return re.sub(r'\{\{\w+\}\}|[^\u0621-\u064a\s/]', ' ', s).split()


def ftype(lab, seg):
    if re.search(r'(رقم قومي|الرقم القومي|بطاقة رقم)\s*$', seg.strip()):
        return 'national_id'
    if 'البريد الإلكتروني' in lab:
        return 'email'
    if re.search(r'تليفون|هاتف|موبايل|محمول', lab):
        return 'phone'
    if re.search(r'مبلغ|قدره|ثمن|أجرة|الأجرة', lab) and 'جنيه' not in lab:
        return 'money'
    return 'text'


def convert(path):
    paras = paragraphs(path)
    first = next(i for i, p in enumerate(paras) if p.strip())
    title, fields, lines, n, seen = paras[first].strip(), [], [], 0, {}
    for line in paras[first + 1:]:
        def date_sub(m):
            nonlocal n
            if not re.search(r'[.…\-_]', m.group(0)):
                return m.group(0)
            n += 1
            lab = ' '.join(words(line[max(0, m.start() - 30):m.start()])[-4:])
            fields.append(dict(key=f'date{n}', label=('تاريخ — ' + lab) if lab else 'التاريخ', type='date', required=False, defaultValue='.../.../......'))
            return ' {{date%d}} ' % n
        l2 = DATE.sub(date_sub, line) if '/' in line else line
        ordm = re.match(r'\s*(أول|ثاني|ثالث|رابع|خامس)', l2)
        out, pos = [], 0
        for m in BLANK.finditer(l2):
            n += 1
            raw, seg = m.group(0), l2[pos:m.start()]
            if raw.startswith('['):
                lab = raw.strip('[] ')
            else:
                w = words(seg) or words(l2[m.end():m.end() + 40])[:3]
                lab = ' '.join(w[-4:]).strip(' /') or f'بيان {n}'
            if ordm:
                lab = re.sub(r'^(أول|ثاني|ثالث|رابع|خامس)\s*ا?\s*', '', lab).strip() or 'الاسم'
                lab = f'{lab} — الطرف {PARTY[ordm.group(1)]}'
            seen[lab] = seen.get(lab, 0) + 1
            label = lab if seen[lab] == 1 else f'{lab} ({seen[lab]})'
            alone = not re.sub(BLANK, '', l2).strip(' :-–.،')
            fields.append(dict(key=f'f{n}', label=label, type='textarea' if alone and len(raw) > 25 else ftype(lab, seg), required=False, defaultValue='............'))
            out.append(seg + ('' if seg.endswith(' ') or not seg else ' ') + '{{f%d}} ' % n)
            pos = m.end()
        out.append(l2[pos:])
        lines.append(re.sub(r'[ \t]{2,}', ' ', ''.join(out)).rstrip())
    body = re.sub(r'\n{3,}', '\n\n', '\n'.join(lines)).strip()
    cat = next(c for c in CATS if re.search(c[0], title))
    return dict(name=title, category=cat[1], categoryLabel=cat[2], legalBasis=cat[3], fields=fields, body=body)


def main(src, out):
    made = []
    for i, f in enumerate(sorted(glob.glob(os.path.join(src, '**', '*.docx'), recursive=True))):
        t = convert(f)
        tid = 'ctr-' + os.path.basename(f)[:8]
        t = dict(id=tid, **t, description=f"نموذج {t['name']} قابل للملء — {len(t['fields'])} خانة.", credits=1, active=True, sort=100 + i, version=1,
                 autoFields=True, source='نماذج المستخدم (Word) — حُوّلت آليًا: كل فراغ منقّط أصبح خانة',
                 draftingInstructions='املأ الخانات بقيم المستخدم حرفيًا وأبقِ البنود كما هي. الخانات الفارغة تبقى نقاطًا. راجع اتساق الأسماء والمبالغ والتواريخ، '
                                      'ونبّه لأي بند يخالف نصًا آمرًا في المصادر المسترجعة دون اختلاق أرقام مواد.')
        json.dump(t, open(os.path.join(out, tid + '.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
        made.append(t['category'])
    return made


if __name__ == '__main__':
    from collections import Counter
    m = main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else os.path.dirname(os.path.abspath(__file__)))
    print(len(m), 'templates', dict(Counter(m)))
