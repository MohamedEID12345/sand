/** Loads the repository content (content/laws, content/templates, content/books) into the embedded store on start-up.
 * Only files whose fingerprint changed since the last sync are rewritten; templates edited from the admin console are kept
 * unless their source file changes. Run from a PC with `npm run setup`. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { db, get, now } from '../db';
import { writeLaw } from './libraryWrite';
import { searchKey } from './library';

export const CONTENT_DIR = path.resolve(process.env.CONTENT_DIR || path.join(__dirname, '..', '..', '..', 'content'));
const enc = (k: string) => k.replace(/[.#$\[\]/~]/g, '_');
const fp = (f: string) => { const st = fs.statSync(f); return crypto.createHash('sha1').update(`${st.size}|${st.mtimeMs}|${f}`).digest('hex').slice(0, 16); };
const jsonFiles = (dir: string) => (fs.existsSync(dir) ? fs.readdirSync(dir).filter((f) => f.endsWith('.json') && !f.startsWith('_') && f !== 'index.json') : []);

export async function syncContent() {
  const t0 = Date.now(), seen = ((await get<Record<string, string>>('sync')) || {}) as Record<string, string>;
  const upd: Record<string, string | null> = {};
  let laws = 0, templates = 0, books = 0, removed = 0;
  const lawsDir = path.join(CONTENT_DIR, 'laws');
  const index = fs.existsSync(path.join(lawsDir, 'index.json')) ? JSON.parse(fs.readFileSync(path.join(lawsDir, 'index.json'), 'utf8')) : null;
  if (index?.categories) await db.ref('library/categories').set(Object.fromEntries(index.categories.map((c: any) => [c.id, { name: c.name, sort: c.sort }])));

  for (const f of jsonFiles(lawsDir)) {
    const file = path.join(lawsDir, f), key = `law_${enc(f.replace(/\.json$/, ''))}`, h = fp(file);
    if (seen[key] === h) continue;
    const doc = JSON.parse(fs.readFileSync(file, 'utf8')), id = enc(doc.meta.id);
    if (doc.meta.published) { await writeLaw(doc.meta, doc.articles); laws++; }
    else if (await get(`library/items/${id}`)) {
      await db.ref().update({ [`library/items/${id}`]: null, [`library/articles/${id}`]: null, [`library/chunks/${id}`]: null }); removed++;
    }
    upd[key] = h;
  }
  const tplDir = path.join(CONTENT_DIR, 'templates');
  for (const f of jsonFiles(tplDir)) {
    const file = path.join(tplDir, f), key = `tpl_${enc(f.replace(/\.json$/, ''))}`, h = fp(file);
    if (seen[key] === h) continue;
    const t = JSON.parse(fs.readFileSync(file, 'utf8')), id = enc(t.id);
    await db.ref().update({ [`contractTemplates/${id}`]: { ...t, updatedAt: now() },
      [`library/chunks/form_${id}`]: { 0: { sourceType: 'FORM', templateId: t.id, text: `نموذج: ${t.name}\n${t.description || ''}\nالسند: ${(t.legalBasis || []).join('، ')}\n${t.body}` } } });
    upd[key] = h; templates++;
  }
  const bookDir = path.join(CONTENT_DIR, 'books');
  for (const f of jsonFiles(bookDir)) {
    const file = path.join(bookDir, f), key = `book_${enc(f.replace(/\.json$/, ''))}`, h = fp(file);
    if (seen[key] === h) continue;
    const { meta: m, pages } = JSON.parse(fs.readFileSync(file, 'utf8')), id = enc(m.id);
    if (m.published) {
      const chunks: Record<string, any> = {};
      for (const p of pages) for (let i = 0, j = 0; i < p.text.length; i += 1400, j++) chunks[`p${p.page}_${j}`] = { page: p.page, text: `${m.title} — ص ${p.page}\n${p.text.slice(Math.max(0, i - (i ? 150 : 0)), i + 1400)}` };
      await db.ref().update({
        [`library/items/${id}`]: { type: 'BOOK', subtype: m.subtype || null, title: m.title, subtitle: m.subtitle || null, author: m.author || null, year: m.year || null,
          categoryId: m.categoryId, pageCount: pages.length, license: m.license || null, textQuality: m.textQuality, verified: !!m.verified, notes: m.notes || null,
          searchKey: searchKey(`${m.title} ${m.subtitle || ''} ${m.author || ''}`), updatedAt: now() },
        [`library/pages/${id}`]: Object.fromEntries(pages.map((p: any) => [p.page, p.text])), [`library/chunks/${id}`]: chunks });
      books++;
    }
    upd[key] = h;
  }
  if (Object.keys(upd).length) {
    await db.ref('sync').update(upd);
    const items = (await get<Record<string, any>>('library/items')) || {};
    await db.ref('library/index/meta').set({ version: Date.now(), updatedAt: now(), lawCount: Object.values(items).filter((i: any) => i.type !== 'BOOK').length,
      articleCount: Object.values(items).reduce((a: number, i: any) => a + (i.articleCount || 0), 0) });
  }
  console.log(`[content] synced in ${Date.now() - t0}ms: ${laws} laws, ${templates} templates, ${books} books, ${removed} unpublished (from ${CONTENT_DIR})`);
  return { laws, templates, books, removed };
}
