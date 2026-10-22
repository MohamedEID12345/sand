import { Router, Request } from 'express';
import { db, get, listPage, newId, now } from '../db';
import { bad, forbidden, notFound, ok, required, str, wrap } from '../http';
import { requireAdmin } from '../services/auth';
import { aiConfig, appConfig, invalidateConfig, DEFAULT_OPERATIONS } from '../services/config';
import { addCredits, getWallet } from '../services/wallet';
import { invalidateTemplates } from '../services/templates';
import * as lib from '../services/library';
import { applyStatus, orderView } from './payments';
import { TICKET_STATUS } from './misc';
import { userView } from '../services/users';
import { uploadSingle } from '../upload';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import zlib from 'node:zlib';
import { bucket } from '../files';
import { SUPABASE_URL, STORAGE_BUCKET } from '../supabase';
import { exportTree } from '../db';
import { docxParagraphs, docxToTemplate } from '../services/docxTemplate';
import { segmentText, writeLaw } from '../services/libraryWrite';
import { validateTemplate } from './officeTemplates';
import { providersStatus, testProviders } from '../services/gemini';
import { syncContent } from '../services/contentSync';
import { buildEmbeddings, embedStatus } from '../services/embeddings';

const upload = { single: (field: string) => uploadSingle(field, 60 * 1024 * 1024) };
const INGEST = path.resolve(__dirname, '..', '..', 'tools', 'ingest');
/** Runs the Python pipeline on a queued PDF (needs python3 + the tools/ingest requirements — i.e. your PC: `npm run process-queue`). */
export async function processPdf(id: string, key: string, meta: any) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ingest-')), metaFile = path.join(dir, 'meta.json'), out = path.join(dir, 'out.json');
  fs.writeFileSync(metaFile, JSON.stringify(meta));
  const pdf = path.join(dir, 'source.pdf');
  fs.writeFileSync(pdf, (await bucket().file(key).download())[0]);
  await db.ref(`ingestQueue/${id}`).update({ status: 'PROCESSING', startedAt: now() });
  const res = await new Promise<{ code: number; log: string }>((resolve) => {
    const p = spawn(process.env.PYTHON || 'python3', [path.join(INGEST, 'process_pdf.py'), pdf, metaFile, out], { cwd: INGEST });
    let log = '';
    p.stdout.on('data', (d) => (log += d)); p.stderr.on('data', (d) => (log += d));
    p.on('close', (code) => resolve({ code: code ?? 1, log: log.slice(-2000) }));
    p.on('error', (e) => resolve({ code: 1, log: String(e) }));
  });
  if (res.code !== 0 || !fs.existsSync(out)) {
    await db.ref(`ingestQueue/${id}`).update({ status: 'FAILED', error: res.log.slice(-500), finishedAt: now() });
    return;
  }
  const doc = JSON.parse(fs.readFileSync(out, 'utf8'));
  if (doc.meta.published) await writeLaw(doc.meta, doc.articles);
  else await db.ref(`ingestDrafts/${id}`).set(doc);
  await db.ref(`ingestQueue/${id}`).update({ status: doc.meta.published ? 'DONE' : 'NEEDS_REVIEW', articles: doc.articles.length, method: doc.report.method,
    gaps: doc.report.gaps, finishedAt: now() });
  fs.rmSync(dir, { recursive: true, force: true });
}

/** Platform administration API (mounted at /admin, behind requireAuth). Roles: SUPER, CONTENT, SUPPORT, FINANCE. */
const r = Router();
const audit = (req: Request, action: string, target: string, data?: unknown) =>
  db.ref(`adminAudit/${newId()}`).set({ uid: req.user!.uid, role: (req as any).adminRole, action, target, data: data ?? null, at: now() });
const SUPER = requireAdmin('SUPER'), CONTENT = requireAdmin('CONTENT'), SUPPORT = requireAdmin('SUPPORT', 'FINANCE'), FINANCE = requireAdmin('FINANCE');

r.get('/me', requireAdmin(), wrap(async (req) => ({ uid: req.user!.uid, role: (req as any).adminRole, user: await userView(req.user!.uid) })));
r.get('/overview', requireAdmin(), wrap(async () => {
  const [users, orders, queue, usage, revenue] = await Promise.all([get<any>('users'), get<any>('orders'), get<any>('supportQueue'), get<any>('stats/aiUsage'), get<any>(`stats/revenue/${now().slice(0, 7)}`)]);
  const u = Object.values<any>(users || {}), o = Object.values<any>(orders || {});
  const days = Object.entries<any>(usage || {}).sort((a, b) => b[0].localeCompare(a[0])).slice(0, 14).map(([day, ops]) => ({ day, total: Object.values<number>(ops).reduce((a, b) => a + b, 0), ops }));
  return { users: u.length, newUsers7d: u.filter((x) => x.createdAt >= new Date(Date.now() - 7 * 864e5).toISOString()).length,
    suspended: u.filter((x) => x.status === 'SUSPENDED').length, ordersPaid: o.filter((x) => x.status === 'PAID').length,
    ordersPendingReview: o.filter((x) => x.status === 'UNDER_REVIEW').length, revenueThisMonth: Object.values<number>(revenue || {}).reduce((a, b) => a + b, 0),
    openTickets: Object.values<any>(queue || {}).filter((t) => t.status === 'OPEN').length, aiUsage: days, library: lib.stats() };
}));

// ---- configuration
const CONFIGS: Record<string, string> = { app: 'config/app', ai: 'config/ai', payments: 'config/payments', caseMeta: 'config/caseMeta', deadlineRules: 'config/deadlineRules' };
r.get('/config/:name', requireAdmin(), wrap(async (req) => {
  const path = CONFIGS[req.params.name];
  if (!path) throw notFound('الإعداد');
  if (req.params.name === 'app') return { stored: await get(path), effective: await appConfig() };
  if (req.params.name === 'ai') return { stored: await get(path), effective: await aiConfig(), defaults: DEFAULT_OPERATIONS };
  return { stored: await get(path) };
}));
r.put('/config/:name', SUPER, wrap(async (req) => {
  const path = CONFIGS[req.params.name];
  if (!path) throw notFound('الإعداد');
  if (typeof req.body !== 'object' || Array.isArray(req.body)) throw bad('يجب إرسال كائن JSON');
  await db.ref(path).set(req.body);
  invalidateConfig();
  await audit(req, 'config.update', req.params.name);
  return ok('تم الحفظ');
}));

// ---- catalog: plans, credit packs, promo codes
const KINDS = new Set(['plans', 'creditPacks', 'promoCodes']);
r.get('/catalog/:kind', requireAdmin(), wrap(async (req) => {
  if (!KINDS.has(req.params.kind)) throw notFound('القائمة');
  return { items: Object.entries<any>((await get(`catalog/${req.params.kind}`)) || {}).map(([id, v]) => ({ id, ...v })) };
}));
r.put('/catalog/:kind/:id', FINANCE, wrap(async (req) => {
  if (!KINDS.has(req.params.kind)) throw notFound('القائمة');
  const b = req.body || {};
  if (req.params.kind !== 'promoCodes') required(b, ['name', 'price']);
  const id = req.params.kind === 'promoCodes' ? req.params.id.toUpperCase() : req.params.id;
  await db.ref(`catalog/${req.params.kind}/${id}`).set({ ...b, updatedAt: now() });
  await audit(req, 'catalog.put', `${req.params.kind}/${id}`, b);
  return ok('تم الحفظ');
}));
r.delete('/catalog/:kind/:id', FINANCE, wrap(async (req) => {
  if (!KINDS.has(req.params.kind)) throw notFound('القائمة');
  await db.ref(`catalog/${req.params.kind}/${req.params.id}/active`).set(false);
  await audit(req, 'catalog.disable', `${req.params.kind}/${req.params.id}`);
  return ok('تم الإيقاف');
}));

// ---- users
r.get('/users', SUPPORT, wrap(async (req) => {
  const q = String(req.query.q || '').trim();
  const p = await listPage<any>('users', { cursor: req.query.cursor as string, limit: 50, filter: (u) => !q || `${u.name} ${u.email} ${u.phone}`.includes(q) });
  return { ...p, items: await Promise.all(p.items.map(async (u: any) => ({ id: u.id, name: u.name, email: u.email, phone: u.phone, status: u.status || 'ACTIVE',
    governorate: u.governorate, createdAt: u.createdAt, lastLoginAt: u.lastLoginAt || null, remainingCredits: (await getWallet(u.id)).remainingCredits }))) };
}));
r.get('/users/:id', SUPPORT, wrap(async (req) => {
  const user = await userView(req.params.id);
  if (!user) throw notFound('المستخدم');
  const orders = Object.entries<any>((await get('orders')) || {}).filter(([, o]) => o.uid === req.params.id).map(([id, o]) => orderView(id, o));
  return { user, wallet: await getWallet(req.params.id), subscription: await get(`subscriptions/${req.params.id}`), orders,
    transactions: (await listPage(`creditTx/${req.params.id}`, { limit: 30 })).items };
}));
r.post('/users/:id/status', SUPPORT, wrap(async (req) => {
  if (!['ACTIVE', 'SUSPENDED'].includes(req.body?.status)) throw bad('حالة غير صالحة');
  await db.ref(`users/${req.params.id}/status`).set(req.body.status);
  if (req.body.status === 'SUSPENDED') {  // revoke sessions
    const toks = (await get<Record<string, any>>('refreshTokens')) || {}, upd: Record<string, null> = {};
    for (const [k, t] of Object.entries(toks)) if (t.uid === req.params.id) upd[`refreshTokens/${k}`] = null;
    if (Object.keys(upd).length) await db.ref().update(upd);
  }
  await audit(req, 'user.status', req.params.id, req.body.status);
  return ok();
}));
r.post('/users/:id/credits', FINANCE, wrap(async (req) => {
  const amount = Math.trunc(Number(req.body?.amount));
  if (!amount || Math.abs(amount) > 100000) throw bad('قيمة غير صالحة', { amount: 'رقم صحيح' });
  required(req.body, ['reason']);
  await addCredits(req.params.id, amount, 'bonusCredits', 'ADMIN_ADJUST', str(req.body.reason, 200)!);
  await audit(req, 'user.credits', req.params.id, { amount, reason: req.body.reason });
  return getWallet(req.params.id);
}));
r.post('/users/:id/subscription', FINANCE, wrap(async (req) => {
  required(req.body, ['planId', 'days']);
  const plan = await get<any>(`catalog/plans/${req.body.planId}`);
  if (!plan) throw notFound('الباقة');
  const end = new Date(Date.now() + Number(req.body.days) * 864e5).toISOString();
  await db.ref(`subscriptions/${req.params.id}`).set({ planId: req.body.planId, planName: plan.name, status: 'ACTIVE', startAt: now(), endAt: end, limits: plan.limits || null, grantedBy: req.user!.uid });
  await audit(req, 'user.subscription', req.params.id, req.body);
  return ok();
}));

// ---- orders
r.get('/orders', FINANCE, wrap(async (req) => {
  const status = req.query.status as string;
  const p = await listPage<any>('orders', { cursor: req.query.cursor as string, limit: 50, filter: (o) => !status || o.status === status });
  return { ...p, items: p.items.map((o: any) => ({ ...orderView(o.id, o), uid: o.uid, bankProofNote: o.bankProofNote || null })) };
}));
r.post('/orders/:id/approve', FINANCE, wrap(async (req) => {
  const o = await get<any>(`orders/${req.params.id}`);
  if (!o) throw notFound('الطلب');
  if (o.provider !== 'BANK') throw bad('الاعتماد اليدوي للتحويلات البنكية فقط');
  await db.ref(`orders/${req.params.id}/status`).set('PENDING');
  await applyStatus(req.params.id, 'PAID', `BANK-${req.user!.uid.slice(-4)}`);
  await audit(req, 'order.approve', req.params.id);
  return orderView(req.params.id, await get(`orders/${req.params.id}`));
}));
r.post('/orders/:id/reject', FINANCE, wrap(async (req) => {
  await db.ref(`orders/${req.params.id}`).update({ status: 'FAILED', failureReason: str(req.body?.reason, 200) || 'REJECTED', updatedAt: now() });
  await audit(req, 'order.reject', req.params.id, req.body?.reason);
  return ok();
}));

// ---- support tickets
r.get('/tickets', SUPPORT, wrap(async (req) => {
  const status = req.query.status as string;
  return listPage<any>('supportQueue', { sortKey: 'updatedAt', limit: 100, filter: (t) => !status || t.status === status });
}));
r.get('/tickets/:id', SUPPORT, wrap(async (req) => {
  const q = await get<any>(`supportQueue/${req.params.id}`);
  if (!q) throw notFound('التذكرة');
  const t = await get<any>(`tickets/${q.uid}/${req.params.id}`);
  const msgs = Object.entries<any>((await get(`ticketMessages/${req.params.id}`)) || {}).map(([id, m]) => ({ ...m, id })).sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  return { ...t, id: req.params.id, uid: q.uid, user: await userView(q.uid), statusLabel: TICKET_STATUS[t?.status], messages: msgs };
}));
r.post('/tickets/:id/reply', SUPPORT, wrap(async (req) => {
  required(req.body, ['body']);
  const q = await get<any>(`supportQueue/${req.params.id}`);
  if (!q) throw notFound('التذكرة');
  const t = now(), status = req.body.close ? 'CLOSED' : 'ANSWERED';
  await db.ref().update({
    [`ticketMessages/${req.params.id}/${newId()}`]: { body: str(req.body.body, 10000), fromSupport: true, authorName: 'فريق الدعم', createdAt: t },
    [`tickets/${q.uid}/${req.params.id}/status`]: status, [`tickets/${q.uid}/${req.params.id}/updatedAt`]: t,
    [`tickets/${q.uid}/${req.params.id}/lastMessage`]: str(req.body.body, 200), [`supportQueue/${req.params.id}/status`]: status, [`supportQueue/${req.params.id}/updatedAt`]: t,
    [`notifications/${q.uid}/${newId()}`]: { type: 'TICKET', title: 'رد جديد من الدعم', body: str(req.body.body, 120), createdAt: t, refType: 'TICKET', refId: req.params.id },
  });
  await audit(req, 'ticket.reply', req.params.id);
  return ok();
}));

// ---- library content
r.get('/library/items', CONTENT, wrap(async () => {
  const items = (await get<Record<string, any>>('library/items')) || {};
  return { items: Object.entries<any>(items).map(([id, it]) => ({ id, type: it.type, title: it.title, number: it.number, year: it.year, categoryId: it.categoryId,
    status: it.status || null, statusNote: it.statusNote || null, verified: !!it.verified, textQuality: it.textQuality, articleCount: it.articleCount || 0, notes: it.notes || null }))
    .sort((a, b) => (a.categoryId || '').localeCompare(b.categoryId || '') || (b.year || 0) - (a.year || 0)), stats: lib.stats() };
}));
r.patch('/library/items/:id', CONTENT, wrap(async (req) => {
  const path = `library/items/${req.params.id}`;
  if (!(await get(path))) throw notFound('المصدر');
  const upd: Record<string, unknown> = { updatedAt: now() };
  if (req.body?.status !== undefined) { if (![null, 'IN_FORCE', 'AMENDED', 'REPEALED'].includes(req.body.status)) throw bad('حالة غير صالحة'); upd.status = req.body.status; }
  for (const k of ['statusNote', 'title', 'subtitle', 'notes', 'gazette', 'issueDate', 'categoryId']) if (req.body?.[k] !== undefined) upd[k] = str(req.body[k], 1000) ?? null;
  if (req.body?.verified !== undefined) upd.verified = !!req.body.verified;
  await db.ref(path).update(upd);
  await db.ref('library/index/meta/version').set(Date.now());
  await audit(req, 'library.update', req.params.id, upd);
  return ok('تم الحفظ');
}));
r.delete('/library/items/:id', CONTENT, wrap(async (req) => {  // unpublish: removes the item, its articles/pages and RAG chunks
  const id = req.params.id;
  await db.ref().update({ [`library/items/${id}`]: null, [`library/articles/${id}`]: null, [`library/pages/${id}`]: null, [`library/chunks/${id}`]: null,
    'library/index/meta/version': Date.now() });
  await audit(req, 'library.unpublish', id);
  return ok('تم إلغاء النشر');
}));
r.post('/library/reload', CONTENT, wrap(async () => { const sync = await syncContent(); await lib.forceReload(); return { ok: true, sync, stats: lib.stats() }; }));
r.post('/library/embed', CONTENT, wrap(async () => buildEmbeddings()));
r.get('/library/embed', CONTENT, wrap(async () => embedStatus()));
r.get('/system', SUPER, wrap(async () => ({ store: { backend: 'Supabase (Postgres + Storage)', databaseURL: SUPABASE_URL(), bucket: STORAGE_BUCKET(), inlinePdf: inlineIngest(),
  lastBackup: await get('system/lastBackup') }, library: lib.stats(), providers: await providersStatus(), node: process.version, uptimeSec: Math.round(process.uptime()) })));
/** Everything except library/chunks (search chunks + vectors: rebuilt by re-saving a law and `npm run embed`). */
const exportDatabase = () => exportTree(['library/chunks']);
const inlineIngest = () => process.env.INGEST_INLINE === 'true';
/** Full JSON export of the database into Cloud Storage (backups/…json.gz) + a 1-hour download link. */
r.post('/system/backup', SUPER, wrap(async (req) => {
  const all = await exportDatabase();
  const key = `backups/rtdb-${now().replace(/[:.]/g, '-')}.json.gz`, buf = zlib.gzipSync(Buffer.from(JSON.stringify(all)));
  await bucket().file(key).save(buf, { contentType: 'application/gzip' });
  const [url] = await bucket().file(key).getSignedUrl({ action: 'read', expires: Date.now() + 3600e3, responseDisposition: `attachment; filename*=UTF-8''${encodeURIComponent(key.split('/')[1])}` });
  await db.ref('system/lastBackup').set({ key, bytes: buf.length, at: now() });
  await audit(req, 'system.backup', key);
  return { ok: true, key, bytes: buf.length, url };
}));

// ---- templates
r.get('/templates', CONTENT, wrap(async () => ({ items: Object.entries<any>((await get('contractTemplates')) || {})
  .map(([id, t]) => ({ id, name: t.name, category: t.category, categoryLabel: t.categoryLabel, active: t.active !== false, fields: (t.fields || []).length, credits: t.credits ?? 1, autoFields: !!t.autoFields }))
  .sort((a, b) => a.category.localeCompare(b.category)) })));
r.get('/templates/:id', CONTENT, wrap(async (req) => { const t = await get(`contractTemplates/${req.params.id}`); if (!t) throw notFound('النموذج'); return t; }));
r.put('/templates/:id', CONTENT, wrap(async (req) => {
  const t = req.body || {};
  required(t, ['name', 'category', 'body']);
  if (!Array.isArray(t.fields)) throw bad('fields يجب أن تكون قائمة');
  const keys = new Set(t.fields.map((f: any) => f.key));
  const missing = [...String(t.body).matchAll(/\{\{(\w+)\}\}/g)].map((m) => m[1]).filter((k) => !keys.has(k) && k !== 'block');
  if (missing.length) throw bad(`متغيرات بلا خانات: ${[...new Set(missing)].join('، ')}`);
  await db.ref(`contractTemplates/${req.params.id}`).set({ ...t, id: req.params.id, updatedAt: now(), version: (Number(t.version) || 0) + 1 });
  invalidateTemplates();
  await audit(req, 'template.put', req.params.id);
  return ok('تم الحفظ');
}));
r.patch('/templates/:id/active', CONTENT, wrap(async (req) => {
  await db.ref(`contractTemplates/${req.params.id}/active`).set(!!req.body?.active);
  invalidateTemplates();
  await audit(req, 'template.active', req.params.id, !!req.body?.active);
  return ok();
}));

// ---- legal updates & broadcast notifications
r.get('/legal-updates', CONTENT, wrap(async () => listPage('legalUpdates', { sortKey: 'publishedAt', limit: 100 })));
r.post('/legal-updates', CONTENT, wrap(async (req) => {
  required(req.body, ['title', 'summary']);
  const id = newId();
  await db.ref(`legalUpdates/${id}`).set({ type: req.body.type || 'LAW', typeLabel: req.body.typeLabel || 'تشريع', title: str(req.body.title, 200), summary: str(req.body.summary, 2000),
    refType: req.body.refType || null, refId: req.body.refId || null, publishedAt: now() });
  await audit(req, 'update.create', id);
  return { id };
}));
r.delete('/legal-updates/:id', CONTENT, wrap(async (req) => { await db.ref(`legalUpdates/${req.params.id}`).remove(); await audit(req, 'update.delete', req.params.id); return ok(); }));
r.post('/notifications/broadcast', SUPER, wrap(async (req) => {
  required(req.body, ['title', 'body']);
  const uids: string[] = Array.isArray(req.body.uids) && req.body.uids.length ? req.body.uids : Object.keys((await get('users')) || {});
  const t = now(), upd: Record<string, unknown> = {};
  for (const uid of uids) upd[`notifications/${uid}/${newId()}`] = { type: req.body.type || 'ANNOUNCEMENT', title: str(req.body.title, 120), body: str(req.body.body, 500), createdAt: t,
    refType: req.body.refType || null, refId: req.body.refId || null };
  for (let i = 0, e = Object.entries(upd); i < e.length; i += 500) await db.ref().update(Object.fromEntries(e.slice(i, i + 500)));
  await audit(req, 'notify.broadcast', `${uids.length} users`, req.body.title);
  return { ok: true, sent: uids.length };
}));

// ---- uploads: laws & templates
const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9-]+/g, '-').replace(/^-|-$/g, '').slice(0, 60);
r.post('/uploads/template', CONTENT, upload.single('file'), wrap(async (req) => {
  const f = (req as any).file as Express.Multer.File | undefined;
  if (!f) throw bad('ارفع ملف Word (.docx) أو JSON', { file: 'مطلوب' });
  let t: any;
  if (/\.docx$/i.test(f.originalname)) {
    const conv = await docxToTemplate(f.buffer, f.originalname.replace(/\.docx$/i, ''));
    t = { ...conv, description: `نموذج ${conv.name} قابل للملء — ${conv.fields.length} خانة.`, credits: 1, active: true, autoFields: true,
      category: req.body?.category || 'CIVIL_OTHER', categoryLabel: req.body?.categoryLabel || 'عقود مدنية أخرى', legalBasis: [], sort: 500,
      draftingInstructions: 'املأ الخانات بقيم المستخدم حرفيًا وأبقِ البنود كما هي، وراجع اتساق الأسماء والمبالغ والتواريخ دون اختلاق أرقام مواد.' };
  } else if (/\.json$/i.test(f.originalname)) {
    t = JSON.parse(f.buffer.toString('utf8'));
  } else throw bad('الصيغ المدعومة: ‎.docx و‎.json');
  validateTemplate(t);
  const id = slug(String(req.body?.id || t.id || '')) || `up-${newId().slice(-8).toLowerCase()}`;
  await db.ref(`contractTemplates/${id}`).set({ ...t, id, version: 1, source: `رفع من لوحة التحكم — ${f.originalname}`, updatedAt: now() });
  const { invalidateTemplates } = await import('../services/templates');
  invalidateTemplates();
  await audit(req, 'upload.template', id);
  return { id, name: t.name, fields: t.fields.length };
}));

r.post('/uploads/law', CONTENT, upload.single('file'), wrap(async (req) => {
  const f = (req as any).file as Express.Multer.File | undefined, b = req.body || {};
  if (!f) throw bad('ارفع ملف القانون', { file: 'مطلوب' });
  const ext = (f.originalname.match(/\.(\w+)$/)?.[1] || '').toLowerCase();
  if (ext === 'json') {  // output of backend/tools/ingest (content/laws/<id>.json)
    const doc = JSON.parse(f.buffer.toString('utf8'));
    if (!doc.meta?.id || !Array.isArray(doc.articles)) throw bad('ملف JSON ليس بصيغة مخرجات أداة المعالجة');
    const res = await writeLaw({ ...doc.meta, categoryId: doc.meta.categoryId }, doc.articles);
    await audit(req, 'upload.law.json', res.id);
    return { ...res, status: 'PUBLISHED' };
  }
  required(b, ['title', 'categoryId']);
  const id = slug(b.id || (b.number && b.year ? `law-${b.number}-${b.year}` : '')) || `up-${newId().slice(-8).toLowerCase()}`;
  const meta = { id, title: str(b.title, 200), subtitle: str(b.subtitle, 400) || str(b.title, 200), number: Number(b.number) || null, year: Number(b.year) || null,
    categoryId: b.categoryId, instrument: b.instrument || 'LAW', status: b.status || null, statusNote: str(b.statusNote, 400) || null, gazette: str(b.gazette, 200) || null,
    sourceFile: f.originalname, verified: b.verified === 'true', notes: str(b.notes, 400) || null };
  if (ext === 'docx' || ext === 'txt') {
    const text = ext === 'docx' ? (await docxParagraphs(f.buffer)).join('\n') : f.buffer.toString('utf8');
    const arts = segmentText(text);
    if (!arts.length) throw bad('لم يُعثر على مواد ("مادة 1 …") في الملف — راجع التنسيق');
    const res = await writeLaw({ ...meta, textQuality: ext === 'docx' ? 'WORD' : 'TEXT' }, arts);
    await audit(req, `upload.law.${ext}`, id, { articles: arts.length });
    return { ...res, status: 'PUBLISHED', firstArticles: arts.slice(0, 3).map((a) => a.label) };
  }
  if (ext === 'pdf') {  // the Python pipeline (font decoding / OCR) needs python + poppler/tesseract → runs inline only with INGEST_INLINE=true, else queued for `npm run process-queue`
    const key = `ingest/${id}-${Date.now()}.pdf`;
    await bucket().file(key).save(f.buffer, { contentType: 'application/pdf' });
    const local = inlineIngest();
    await db.ref(`ingestQueue/${id}`).set({ meta, storageKey: key, status: local ? 'PROCESSING' : 'WAITING_LOCAL', createdAt: now(), by: req.user!.uid });
    if (local) processPdf(id, key, meta).catch(() => undefined);
    await audit(req, 'upload.law.pdf', id);
    return { id, status: 'QUEUED', message: local ? 'تم الرفع؛ جارٍ المعالجة وسيظهر في المكتبة تلقائيًا'
      : 'تم الرفع ✅ — ملفات PDF تُعالج على جهازك: شغّل npm run process-queue داخل مجلد backend وسيُنشر تلقائيًا' };
  }
  throw bad('الصيغ المدعومة: PDF وWord وTXT وJSON');
}));
r.get('/uploads/queue', CONTENT, wrap(async () => ({ items: Object.entries<any>((await get('ingestQueue')) || {}).map(([id, q]) => ({ id, ...q }))
  .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt))) })));
r.post('/uploads/queue/:id/publish', CONTENT, wrap(async (req) => {
  const doc = await get<any>(`ingestDrafts/${req.params.id}`);
  if (!doc) throw notFound('المسودة');
  const res = await writeLaw({ ...doc.meta, published: true }, doc.articles);
  await db.ref().update({ [`ingestDrafts/${req.params.id}`]: null, [`ingestQueue/${req.params.id}/status`]: 'DONE' });
  await audit(req, 'upload.law.publishDraft', req.params.id);
  return res;
}));
r.get('/ai/providers', requireAdmin(), wrap(async () => providersStatus()));
r.post('/ai/test', requireAdmin(), wrap(async () => testProviders()));

// ---- admins & audit
r.get('/admins', SUPER, wrap(async () => ({ items: await Promise.all(Object.entries<any>((await get('admins')) || {}).map(async ([uid, a]) => ({ uid, role: a.role, user: await userView(uid) }))) })));
r.put('/admins/:uid', SUPER, wrap(async (req) => {
  if (req.params.uid === req.user!.uid) throw forbidden('لا يمكنك تعديل صلاحياتك');
  const role = req.body?.role;
  if (role !== null && !['SUPER', 'CONTENT', 'SUPPORT', 'FINANCE'].includes(role)) throw bad('دور غير صالح');
  await db.ref(`admins/${req.params.uid}`).set(role ? { role, grantedBy: req.user!.uid, at: now() } : null);
  await audit(req, 'admin.role', req.params.uid, role);
  return ok();
}));
r.get('/audit', SUPER, wrap(async (req) => listPage('adminAudit', { sortKey: 'at', limit: 100, cursor: req.query.cursor as string })));
export default r;
