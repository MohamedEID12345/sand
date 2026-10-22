/** Writes a law (meta + articles) into the library (used by content sync and admin uploads). */
import { db, now } from '../db';
import { searchKey } from './library';

const ORD: Record<string, number> = { 'الاولي': 1, 'الثانيه': 2, 'الثالثه': 3, 'الرابعه': 4, 'الخامسه': 5, 'السادسه': 6, 'السابعه': 7, 'الثامنه': 8, 'التاسعه': 9, 'العاشره': 10 };
const DIG = (s: string) => s.replace(/[٠-٩]/g, (d) => String('٠١٢٣٤٥٦٧٨٩'.indexOf(d)));
const enc = (k: string) => k.replace(/[.#$\[\]/~]/g, '_');

export interface LawArticle { key: string; part: string; number: number | null; suffix: string | null; label: string; heading: string | null; text: string; page: number | null; amendmentNote?: string | null }

/** Plain-text law → articles ("مادة 5 -", "المادة (5):", "(المادة الأولى)", headings الباب/الفصل). */
export function segmentText(text: string): LawArticle[] {
  const arts: LawArticle[] = [], head: string[] = [];
  let cur: LawArticle | null = null;
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line) continue;
    const l = DIG(line);
    const h = l.match(/^(الكتاب|الباب|الفصل|الفرع)\s+(\S+)/);
    if (h && line.length < 90) { head.splice(['الكتاب', 'الباب', 'الفصل', 'الفرع'].indexOf(h[1])); head.push(line); continue; }
    const iss = searchKey(l).match(/^\(?\s*الماده\s+(ال[^\s)]+)\s*\)?\s*[:\-–]?\s*(.*)$/);
    if (iss && ORD[iss[1]] && line.length < 120) {
      const n = ORD[iss[1]];
      cur = { key: `i${n}`, part: 'issuance', number: n, suffix: null, label: `المادة ${line.match(/ماد[ةه]\s+(\S+)/)?.[1]?.replace(')', '') || n} (إصدار)`, heading: null, text: iss[2] || '', page: null };
      arts.push(cur); continue;
    }
    const m = l.match(/^[\s(]*(?:ال)?ماد[ةه]\s*[:\-–]?\s*[(\[]?\s*(\d{1,4})\s*[)\]]?\s*(مكرر[اًا]*(?:\s*\(?[أ-ي]\)?)?)?\s*[):\-–.]*\s*(.*)$/);
    if (m && !/^(من|فى|في|بالقانون|السابق|المشار)/.test(m[3])) {
      const n = Number(m[1]), suffix = m[2]?.trim() || null;
      let key = String(n) + (suffix ? '-' + searchKey(suffix).replace(/\s/g, '') : ''), k = 2;
      while (arts.some((a) => a.key === key)) key = `${key.split('~')[0]}~${k++}`;
      cur = { key, part: 'main', number: n, suffix, label: `مادة ${n}${suffix ? ' ' + suffix : ''}`, heading: head.join(' / ') || null, text: m[3] || '', page: null };
      arts.push(cur); continue;
    }
    if (cur) cur.text += (cur.text ? '\n' : '') + line;
  }
  if (arts.length && arts.every((a) => a.part === 'issuance')) for (const a of arts) { a.part = 'main'; a.key = a.key.slice(1); a.label = a.label.replace(' (إصدار)', ''); }
  return arts;
}

export async function writeLaw(meta: any, articles: LawArticle[]) {
  const id = enc(meta.id), t = now(), updates: Record<string, unknown> = {};
  updates[`library/items/${id}`] = {
    type: 'LAW', instrument: meta.instrument || 'LAW', number: meta.number || null, year: meta.year || null, title: meta.title, subtitle: meta.subtitle || meta.title,
    categoryId: meta.categoryId, status: meta.status || null, statusNote: meta.statusNote || null, amends: meta.amends || [], amendedBy: meta.amendedBy || [],
    gazette: meta.gazette || null, issueDate: meta.issueDate || null, textQuality: meta.textQuality || 'UPLOADED', verified: !!meta.verified,
    articleCount: articles.length, notes: meta.notes || null, sourceFile: meta.sourceFile || null,
    searchKey: searchKey(`${meta.title} ${meta.subtitle || ''} ${meta.number || ''} ${meta.year || ''}`), updatedAt: t,
  };
  const arts: Record<string, unknown> = {}, chunks: Record<string, unknown> = {};
  articles.forEach((a, i) => {
    arts[enc(a.key)] = { order: i, part: a.part, number: a.number, suffix: a.suffix, label: a.label, heading: a.heading, text: a.text, page: a.page, amendmentNote: a.amendmentNote || null };
    const head = `${a.label} — ${meta.title}`;
    for (let p = 0, j = 0; p < a.text.length || p === 0; p += 1400, j++) {
      chunks[`${enc(a.key)}_${j}`] = { articleKey: a.key, text: `${head}\n${a.text.slice(Math.max(0, p - (p ? 150 : 0)), p + 1400)}` };
      if (a.text.length <= 1400) break;
    }
  });
  await db.ref(`library/articles/${id}`).set(arts);
  await db.ref(`library/chunks/${id}`).set(chunks);
  await db.ref().update({ ...updates, 'library/index/meta/version': Date.now() });
  return { id, articles: articles.length };
}
