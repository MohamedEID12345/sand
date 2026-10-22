/** Word (.docx) → fill template: every dotted/underscored blank or [label] becomes a field labelled from the words before it;
 * lines starting with أولًا/ثانيًا… tag the party. Same rules as content/templates/_docx_to_templates.py. */
import JSZip from 'jszip';

const BLANK = /\[[^\]\n]{2,60}\]|(?:[.…_ـ\-]{3,}[\s.…_ـ\-]*)+/g;
const DATE = /[.…\-_ ]{1,14}\/[.…\-_ ]{1,14}\/[.…\-_ ]{0,14}(?:٢٠[٠-٩]{0,2}|20\d{0,2})?[.…\-_ ]{0,8}/g;
const PARTY: Record<string, string> = { 'أول': 'الأول', 'ثاني': 'الثاني', 'ثالث': 'الثالث', 'رابع': 'الرابع', 'خامس': 'الخامس' };
const decode = (s: string) => s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');

export async function docxParagraphs(buf: Buffer) {
  const zip = await JSZip.loadAsync(buf);
  const xml = await zip.file('word/document.xml')?.async('string');
  if (!xml) throw new Error('ملف Word غير صالح');
  const body = xml.replace(/<mc:Fallback>[\s\S]*?<\/mc:Fallback>/g, '');
  const paras = [...body.matchAll(/<w:p[ >][\s\S]*?<\/w:p>/g)].map((m) =>
    decode([...m[0].matchAll(/<w:t(?: [^>]*)?>([^<]*)<\/w:t>/g)].map((t) => t[1]).join('')).replace(/[\u200b\u200c\u200e\u200f\ufeff]/g, '').trimEnd());
  while (paras.length && !paras[paras.length - 1].trim()) paras.pop();
  return paras;
}
const words = (s: string) => s.replace(/\{\{\w+\}\}|[^\u0621-\u064a\s/]/g, ' ').split(/\s+/).filter(Boolean);
function ftype(lab: string, seg: string) {
  if (/(رقم قومي|الرقم القومي|بطاقة رقم)\s*$/.test(seg.trim())) return 'national_id';
  if (lab.includes('البريد الإلكتروني')) return 'email';
  if (/تليفون|هاتف|موبايل|محمول/.test(lab)) return 'phone';
  if (/مبلغ|قدره|ثمن|أجرة|الأجرة/.test(lab) && !lab.includes('جنيه')) return 'money';
  return 'text';
}
export async function docxToTemplate(buf: Buffer, fallbackName: string) {
  const paras = await docxParagraphs(buf);
  const first = paras.findIndex((p) => p.trim());
  const name = (first >= 0 ? paras[first].trim() : fallbackName).slice(0, 150);
  const fields: any[] = [], seen: Record<string, number> = {}, lines: string[] = [];
  let n = 0;
  for (const line of paras.slice(first + 1)) {
    const l2 = line.includes('/') ? line.replace(DATE, (m0: string, off: number) => {
      if (!/[.…\-_]/.test(m0)) return m0;
      n++;
      const lab = words(line.slice(Math.max(0, off - 30), off)).slice(-4).join(' ');
      fields.push({ key: `date${n}`, label: lab ? `تاريخ — ${lab}` : 'التاريخ', type: 'date', required: false, defaultValue: '.../.../......' });
      return ` {{date${n}}} `;
    }) : line;
    const ord = l2.match(/^\s*(أول|ثاني|ثالث|رابع|خامس)/);
    let out = '', pos = 0;
    for (const m of l2.matchAll(BLANK)) {
      if (m[0].includes('{{')) continue;
      n++;
      const raw = m[0], seg = l2.slice(pos, m.index);
      let lab = raw.startsWith('[') ? raw.replace(/[\[\]]/g, '').trim()
        : (words(seg).slice(-4).join(' ') || words(l2.slice(m.index! + raw.length, m.index! + raw.length + 40)).slice(0, 3).join(' ')).replace(/^\/|\/$/g, '').trim() || `بيان ${n}`;
      if (ord) lab = `${lab.replace(/^(أول|ثاني|ثالث|رابع|خامس)\s*ا?\s*/, '').trim() || 'الاسم'} — الطرف ${PARTY[ord[1]]}`;
      seen[lab] = (seen[lab] || 0) + 1;
      const alone = !l2.replace(BLANK, '').replace(/[\s:\-–.،]/g, '');
      fields.push({ key: `f${n}`, label: seen[lab] > 1 ? `${lab} (${seen[lab]})` : lab, type: alone && raw.length > 25 ? 'textarea' : ftype(lab, seg), required: false, defaultValue: '............' });
      out += seg + (seg && !seg.endsWith(' ') ? ' ' : '') + `{{f${n}}} `;
      pos = m.index! + raw.length;
    }
    lines.push((out + l2.slice(pos)).replace(/[ \t]{2,}/g, ' ').trimEnd());
  }
  return { name, fields, body: lines.join('\n').replace(/\n{3,}/g, '\n\n').trim() };
}
