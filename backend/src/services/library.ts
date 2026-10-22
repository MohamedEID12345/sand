import zlib from 'node:zlib';
import { get, bucket } from '../db';
import { embedQuery } from './gemini';

/** Arabic search normalizer — also used when writing library items */
export function searchKey(s = '') {
  return s.replace(/[\u064B-\u0652\u0670\u0640]/g, '').replace(/[٠-٩]/g, (d) => String('٠١٢٣٤٥٦٧٨٩'.indexOf(d)))
    .replace(/[إأآٱ]/g, 'ا').replace(/ى/g, 'ي').replace(/ة/g, 'ه').replace(/ؤ/g, 'و').replace(/ئ/g, 'ي').replace(/\s+/g, ' ').trim();
}
const STOP = new Set(['في', 'من', 'علي', 'الي', 'عن', 'ما', 'هل', 'او', 'و', 'ان', 'التي', 'الذي', 'هو', 'هي', 'مع', 'كل', 'ذلك', 'هذا', 'هذه', 'لا']);
const core = (t: string) => { const c = t.replace(/^(وال|بال|كال|فال|لل|ال)/, ''); return c.length >= 3 ? c : t; };
const has = (sk: string, t: string) => sk.includes(t) || sk.includes(core(t));
export const tokens = (q: string) => searchKey(q).split(/[^\u0621-\u064a0-9]+/).filter((t) => t.length > 1 && !STOP.has(t));
const STATUS_AR: Record<string, string> = { IN_FORCE: 'سارٍ', AMENDED: 'معدّل', REPEALED: 'ملغى' };
const PER_PAGE = 30;

interface Art { lawId: string; key: string; order: number; part?: string; label: string; heading?: string; text: string; number?: number; page?: number; amendmentNote?: string; sk: string }
interface State { version: number; items: Record<string, any>; cats: Record<string, any>; arts: Art[]; byLaw: Record<string, Art[]>;
  vecs: { itemId: string; key?: string; page?: number; text: string; v: Float32Array }[]; chunkTotal: number }
let st: State = { version: -1, items: {}, cats: {}, arts: [], byLaw: {}, vecs: [], chunkTotal: 0 };
let loading: Promise<void> | null = null;

/** The whole library (≈30 MB) is read from the database once per version and cached as one gzip file in Storage,
 * so a restart downloads one file instead of thousands of rows. */
async function fetchLibrary(version: number) {
  const key = `cache/library-v${version}.json.gz`, f = bucket().file(key);
  if (version) {
    try { const [buf] = await f.download(); return JSON.parse(zlib.gunzipSync(buf).toString('utf8')); }
    catch { /* not cached yet for this version → build it below */ }
  }
  const [items, cats, articles, chunks] = await Promise.all([get<any>('library/items'), get<any>('library/categories'), get<any>('library/articles'), get<any>('library/chunks')]);
  const data = { items: items || {}, cats: cats || {}, articles: articles || {}, chunks: chunks || {} };
  if (version) f.save(zlib.gzipSync(Buffer.from(JSON.stringify(data))), { contentType: 'application/gzip' }).catch((e) => console.error('[library] cache write failed', e?.message));
  return data;
}
async function load() {
  const meta = await get<any>('library/index/meta');
  const version = meta?.version || 0;
  if (version === st.version) return;
  const { items, cats, articles, chunks } = await fetchLibrary(version);
  const arts: Art[] = [], byLaw: Record<string, Art[]> = {};
  for (const [lawId, rows] of Object.entries<any>(articles || {})) {
    const law = items?.[lawId] || {};
    const list = Object.entries<any>(rows || {}).filter(([, a]) => a && typeof a === 'object').map(([key, a]) => ({ lawId, key, ...a, sk: searchKey(`${a.label} ${law.title || ''} ${a.text}`) })).sort((a, b) => a.order - b.order);
    byLaw[lawId] = list; arts.push(...list);
  }
  const vecs: State['vecs'] = [];
  let chunkTotal = 0;
  for (const [itemId, rows] of Object.entries<any>(chunks || {})) for (const c of Object.values<any>(rows || {})) {
    if (c) chunkTotal++;
    if (!c?.embedding) continue;
    const buf = Buffer.from(c.embedding, 'base64');
    vecs.push({ itemId, key: c.articleKey, page: c.page, text: c.text, v: new Float32Array(buf.buffer, buf.byteOffset, buf.length / 4) });
  }
  st = { version, items: items || {}, cats: cats || {}, arts, byLaw, vecs, chunkTotal };
  console.log(`[library] v${version}: ${Object.keys(st.items).length} items, ${arts.length} articles, ${vecs.length}/${chunkTotal} vectors${semanticOn() ? '' : ' (semantic search off until ≥90% are embedded — keyword search in use)'}`);
}
let watching = false;
export async function ready() {
  if (!watching) {  // only processes that actually use the library (the API) poll for admin edits — not setup/embed scripts
    watching = true;
    setInterval(() => { ready().catch((e) => console.error('[library] reload', e)); }, 60_000).unref();
  }
  if (!loading) loading = load().finally(() => { loading = null; });
  await loading;
}
export function forceReload() { st.version = -1; return ready(); }

const lawRef = (id: string) => { const l = st.items[id] || {}; return l.number && l.year && l.instrument !== 'CONSTITUTION' ? `${l.title} رقم ${l.number} لسنة ${l.year}` : l.title; };
const lawItem = (id: string) => { const l = st.items[id]; return { id, type: l.type || 'LAW', title: l.title, subtitle: l.subtitle, snippet: l.statusNote || null,
  categoryName: st.cats[l.categoryId]?.name || null, effectiveDate: l.issueDate || null, status: l.status || null }; };
function articleItem(a: Art, q?: string[]) {
  let snippet = a.text.slice(0, 220);
  if (q?.length) { const i = searchKey(a.text).indexOf(q[0]); if (i > 80) snippet = '…' + a.text.slice(i - 60, i + 160); }
  const law = st.items[a.lawId] || {};
  return { id: `${a.lawId}:${a.key}`, type: 'ARTICLE', title: `${a.label} — ${law.title}`, subtitle: a.heading || law.subtitle, snippet,
    categoryName: st.cats[law.categoryId]?.name || null, effectiveDate: law.issueDate || null, status: law.status || null };
}
/** Vectors are used only when (almost) the whole library is embedded; a partial index would bias results toward the embedded laws. */
const semanticOn = () => st.vecs.length > 0 && st.vecs.length >= st.chunkTotal * 0.9;
export const stats = () => ({ version: st.version, items: Object.keys(st.items).length, articles: st.arts.length, vectors: st.vecs.length, chunks: st.chunkTotal, semantic: semanticOn() });

export async function categories() {
  await ready();
  const counts: Record<string, number> = {};
  for (const it of Object.values<any>(st.items)) counts[it.categoryId] = (counts[it.categoryId] || 0) + 1;
  return Object.entries<any>(st.cats).sort((a, b) => (a[1].sort || 0) - (b[1].sort || 0)).map(([id, c]) => ({ id, name: c.name, count: counts[id] || 0 }));
}

/** "مادة 147 مدني" / "م 74 عقوبات" → direct article hits */
function directHits(q: string): Art[] {
  const k = searchKey(q), m = k.match(/(?:الماده|ماده|م)\s*\(?(\d{1,4})\)?/);
  if (!m) return [];
  const n = Number(m[1]), rest = tokens(k.replace(m[0], ''));
  const laws = Object.keys(st.byLaw).filter((id) => {
    const l = st.items[id] || {}, lk = searchKey(`${l.title} ${l.subtitle} ${l.number} ${l.year}`);
    return !rest.length || rest.every((t) => lk.includes(t) || lk.includes('ال' + t));
  });
  return laws.flatMap((id) => st.byLaw[id].filter((a) => a.number === n && a.part === 'main')).slice(0, 10);
}

export async function search(q: string, type?: string, categoryId?: string, cursor?: string) {
  await ready();
  const qt = tokens(q || ''), out: any[] = [];
  const okLaw = (id: string) => !categoryId || st.items[id]?.categoryId === categoryId;
  if (!q?.trim()) {
    for (const id of Object.keys(st.items).filter(okLaw).sort((a, b) => (st.items[b].year || 0) - (st.items[a].year || 0)))
      if (!type || type === (st.items[id].type || 'LAW')) out.push(lawItem(id));
  } else {
    if (!type || type === 'ARTICLE') for (const a of directHits(q)) if (okLaw(a.lawId)) out.push(articleItem(a, qt));
    if (!type || type === 'LAW' || type === 'BOOK') for (const [id, l] of Object.entries<any>(st.items)) {
      if (!okLaw(id) || (type && (l.type || 'LAW') !== type)) continue;
      const sk = l.searchKey || searchKey(`${l.title} ${l.subtitle}`);
      if (qt.length && qt.every((t) => sk.includes(t))) out.push(lawItem(id));
    }
    if (!type || type === 'ARTICLE') {
      const seen = new Set(out.map((o) => o.id)), phrase = searchKey(q);
      const scored = st.arts.filter((a) => okLaw(a.lawId)).map((a) => {
        let s = 0;
        for (const t of qt) if (has(a.sk, t)) s += 1;
        if (phrase.length > 6 && a.sk.includes(phrase)) s += 3;
        return { a, s };
      }).filter((x) => x.s >= Math.max(1, Math.ceil(qt.length * 0.6))).sort((x, y) => y.s - x.s || x.a.order - y.a.order);
      for (const { a } of scored.slice(0, 200)) { const it = articleItem(a, qt); if (!seen.has(it.id)) out.push(it); }
    }
  }
  const start = Number(cursor || 0);
  return { items: out.slice(start, start + 20), nextCursor: start + 20 < out.length ? String(start + 20) : null, total: out.length };
}

const kv = (rows: [string, any][]) => rows.filter(([, v]) => v).map(([label, value]) => ({ label, value: String(value) }));
export async function source(type: string, id: string, page = 1) {
  await ready();
  if (type === 'ARTICLE') {
    const [lawId, key] = id.split(':'), list = st.byLaw[lawId] || [], i = list.findIndex((a) => a.key === key);
    if (i < 0) return null;
    const a = list[i], law = st.items[lawId] || {};
    const related: any[] = [list[i - 1], list[i + 1]].filter(Boolean).map((x) => articleItem(x as Art));
    for (const rid of [...(law.amends || []), ...(law.amendedBy || [])]) if (st.items[rid]) related.push(lawItem(rid));
    return { type, id, title: `${a.label} — ${law.title}`, subtitle: law.subtitle, body: a.text + (a.amendmentNote ? `\n\n> ${a.amendmentNote}` : ''),
      meta: kv([['القانون', lawRef(lawId)], ['الموضع', a.heading], ['حاشية', a.amendmentNote], ['الصفحة في المصدر', a.page], ['ملاحظات المصدر', law.notes]]),
      sections: [], related, verified: !!law.verified, status: law.status || null, url: null, page: null, pageCount: null };
  }
  const law = st.items[id];
  if (!law) return null;
  if (law.type === 'BOOK') {
    const text = await get<string>(`library/pages/${id}/${page}`);
    return { type: 'BOOK', id, title: law.title, subtitle: law.subtitle, body: text || '', meta: kv([['المؤلف', law.author], ['السنة', law.year]]),
      sections: [], related: [], verified: !!law.verified, status: null, url: null, page, pageCount: law.pageCount || 1 };
  }
  const list = st.byLaw[id] || [], pageCount = Math.max(1, Math.ceil(list.length / PER_PAGE)), p = Math.min(Math.max(1, page), pageCount);
  const slice = list.slice((p - 1) * PER_PAGE, p * PER_PAGE);
  let body = '', last = '';
  for (const a of slice) {
    if (a.heading && a.heading !== last) { body += `\n*${a.heading}*\n`; last = a.heading; }
    body += `\n#### ${a.label}\n${a.text}\n${a.amendmentNote ? `> ${a.amendmentNote}\n` : ''}`;
  }
  return { type: 'LAW', id, title: law.title, subtitle: law.subtitle, body: body.trim(),
    meta: kv([['رقم القانون', law.number && law.year && law.instrument !== 'CONSTITUTION' ? `${law.number} لسنة ${law.year}` : null], ['الجريدة الرسمية', law.gazette],
      ['الحالة', law.status ? `${STATUS_AR[law.status]}${law.statusNote ? ' — ' + law.statusNote : ''}` : law.statusNote], ['عدد المواد', list.length], ['ملاحظات المصدر', law.notes]]),
    sections: slice.map((a) => ({ id: `${id}:${a.key}`, type: 'ARTICLE', title: a.label, subtitle: null, snippet: null, categoryName: null, effectiveDate: null, status: null })),
    related: [...(law.amends || []), ...(law.amendedBy || [])].filter((r: string) => st.items[r]).map(lawItem),
    verified: !!law.verified, status: law.status || null, url: null, page: p, pageCount };
}

export interface Retrieved { n: number; score: number; sourceType: 'ARTICLE' | 'BOOK' | 'FORM'; sourceId: string; title: string; law: string; article?: string; page?: string; text: string; status?: string; verified: boolean }
/** Hybrid retrieval: direct article references + keyword scoring + vector similarity (when embeddings exist). */
export async function retrieve(query: string, k = 8): Promise<Retrieved[]> {
  await ready();
  const qt = tokens(query), scores = new Map<string, number>();
  const bump = (id: string, s: number) => scores.set(id, (scores.get(id) || 0) + s);
  for (const a of directHits(query)) bump(`${a.lawId}:${a.key}`, 10);
  for (const a of st.arts) {
    let s = 0;
    for (const t of qt) if (has(a.sk, t)) s += 1;
    if (s >= Math.max(1, Math.ceil(qt.length * 0.5))) bump(`${a.lawId}:${a.key}`, (s / Math.max(1, qt.length)) * 3 + (st.items[a.lawId]?.status === 'REPEALED' ? -1 : 0));
  }
  if (semanticOn()) {
    const qv = await embedQuery(query).catch(() => null);
    if (qv) for (const { c, s } of st.vecs.map((c) => ({ c, s: cos(qv, c.v) })).sort((a, b) => b.s - a.s).slice(0, k * 2))
      bump(c.key ? `${c.itemId}:${c.key}` : `BOOK:${c.itemId}:${c.page}`, s * 4);
  }
  const out: Retrieved[] = [];
  for (const [id, score] of [...scores.entries()].sort((a, b) => b[1] - a[1]).slice(0, k)) {
    if (id.startsWith('BOOK:')) {
      const [, bookId, pg] = id.split(':'), b = st.items[bookId] || {}, v = st.vecs.find((x) => x.itemId === bookId && String(x.page) === pg);
      out.push({ n: out.length + 1, score, sourceType: 'BOOK', sourceId: `${bookId}:${pg}`, title: b.title, law: b.title, page: pg, text: v?.text || '', verified: !!b.verified });
      continue;
    }
    const [lawId, key] = id.split(':'), a = (st.byLaw[lawId] || []).find((x) => x.key === key);
    if (!a) continue;
    const law = st.items[lawId] || {};
    out.push({ n: out.length + 1, score, sourceType: 'ARTICLE', sourceId: id, title: `${a.label} — ${law.title}`, law: lawRef(lawId), article: a.label,
      text: a.text.slice(0, 2500), status: law.status ? STATUS_AR[law.status] : undefined, verified: !!law.verified });
  }
  return out;
}
function cos(a: Float32Array, b: Float32Array) {
  let d = 0, na = 0, nb = 0;
  for (let i = 0; i < Math.min(a.length, b.length); i++) { d += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
  return d / (Math.sqrt(na * nb) || 1);
}
