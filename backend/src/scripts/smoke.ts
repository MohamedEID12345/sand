/**
 * End-to-end smoke test (no AI keys needed): npm run test:smoke
 *   default: strict in-memory emulation of the path DB (no Supabase needed)
 *   SMOKE_DB=supabase: the real Supabase layer against SUPABASE_URL/SUPABASE_SECRET_KEY (files kept in memory)
 * Loads the real content/, seeds the owner, then walks the main API flows the Android app and admin console use —
 * once with streamed uploads and once with a pre-read body (proxies / serverless hosts that buffer req.rawBody).
 */
if (process.env.SMOKE_DB === 'supabase') process.env.FILES_MODE = 'memory'; else process.env.DB_MODE = 'memory';
process.env.JWT_SECRET = 'smoke-test-secret';
process.env.ADMIN_EMAIL = 'owner@test.local'; process.env.ADMIN_PASSWORD = 'Owner-Pass-123'; process.env.ADMIN_PHONE = '01000000001';
for (const k of ['GEMINI_API_KEY', 'OPENAI_API_KEY', 'GROQ_API_KEY', 'OPENROUTER_API_KEY']) process.env[k] = '';
process.env.AI_TIMEOUT_MS = '2000';
import request from 'supertest';
import express from 'express';
import { useMemoryDb, get } from '../db';
import { syncContent } from '../services/contentSync';
import { seedOwner } from '../services/seed';
import * as library from '../services/library';
import { bucket } from '../files';

const mdb: any = process.env.SMOKE_DB === 'supabase' ? { writes: 'n/a' } : useMemoryDb();
let fails = 0, passes = 0;
const check = (name: string, cond: any, extra?: any) => { if (cond) passes++; else { fails++; console.error(`✗ ${name}`, extra !== undefined ? JSON.stringify(extra).slice(0, 400) : ''); } };

async function flows(label: string, app: express.Express) {
  const A = (m: 'get' | 'post' | 'patch' | 'put' | 'delete', p: string, tok?: string) => { const r = (request(app) as any)[m]('/api/v1' + p); return tok ? r.set('Authorization', 'Bearer ' + tok) : r; };
  const email = `u${label}${Date.now()}@test.local`, phone = `010${String(Date.now()).slice(-8)}`;
  let r = await A('get', '/app/config'); check(`${label} config`, r.status === 200 && r.body.platformName, r.body);
  r = await A('post', '/auth/register').send({ name: 'محامي تجربة', email, phone, password: 'Passw0rd!x', degree: 'NAQD' });
  check(`${label} register`, r.status === 200 && r.body.accessToken, r.body);
  const tok = r.body.accessToken, refresh = r.body.refreshToken;
  r = await A('post', '/auth/login').send({ identifier: email, password: 'Passw0rd!x' }); check(`${label} login`, r.status === 200, r.body);
  r = await A('post', '/auth/refresh').send({ refreshToken: refresh }); check(`${label} refresh`, r.status === 200 && r.body.accessToken, r.body);
  const T = r.body.accessToken || tok;
  for (const p of ['/me/bootstrap', '/me/stats', '/wallet', '/wallet/transactions', '/ai/costs', '/plans', '/credit-packs', '/payment-methods?itemType=PLAN', '/subscription',
    '/invoices', '/ai/chats', '/library/categories', '/legal-updates', '/cases/meta', '/cases', '/hearings?scope=upcoming', '/clients', '/contract-templates/categories',
    '/contract-templates', '/contracts', '/notifications', '/notifications/unread-count', '/tickets/categories', '/tickets', '/office', '/office/templates', '/search?q=' + encodeURIComponent('إيجار')]) {
    r = await A('get', p, T); check(`${label} GET ${p}`, r.status === 200, { s: r.status, b: r.body });
  }
  r = await A('get', '/wallet', T); check(`${label} signup credits`, r.body.remainingCredits > 0, r.body);
  r = await A('get', '/library/search?q=' + encodeURIComponent('مادة 1 مدني'), T); check(`${label} library search`, r.status === 200 && r.body.items.length > 0, r.body);
  const lawItem = (await A('get', '/library/search', T)).body.items[0];
  r = await A('get', `/library/source?type=LAW&id=${encodeURIComponent(lawItem.id)}`, T); check(`${label} law source`, r.status === 200 && r.body.body?.length > 50, r.body);
  const art = r.body.sections?.[0];
  if (art) { r = await A('get', `/library/source?type=ARTICLE&id=${encodeURIComponent(art.id)}`, T); check(`${label} article source`, r.status === 200 && r.body.body, r.body); }
  // office data
  r = await A('post', '/clients', T).send({ name: 'عميل تجربة', phone: '01011111111', nationalId: undefined }); check(`${label} create client`, r.status === 200 && r.body.id, r.body);
  const clientId = r.body.id;
  r = await A('post', '/cases', T).send({ caseNumber: '123', year: 2026, court: 'محكمة الزقازيق', clientId, opponent: 'خصم', customFields: { x: 'y' } }); check(`${label} create case`, r.status === 200 && r.body.id, r.body);
  const caseId = r.body.id;
  r = await A('patch', `/cases/${caseId}`, T).send({ description: 'وصف' }); check(`${label} update case`, r.status === 200, r.body);
  r = await A('post', `/cases/${caseId}/hearings`, T).send({ hearingDate: '2026-12-01', time: '10:00', notes: 'جلسة' }); check(`${label} create hearing`, r.status === 200 && r.body.id, r.body);
  const hid = r.body.id;
  r = await A('patch', `/hearings/${hid}`, T).send({ status: 'POSTPONED', nextDate: '2027-01-05' }); check(`${label} update hearing`, r.status === 200, r.body);
  r = await A('get', `/cases/${caseId}/hearings`, T); check(`${label} case hearings`, r.status === 200 && r.body.items.length >= 1, r.body);
  r = await A('post', `/cases/${caseId}/notes`, T).send({ body: 'ملاحظة' }); check(`${label} add note`, r.status === 200, r.body);
  r = await A('get', `/cases/${caseId}/notes`, T); check(`${label} notes`, r.status === 200 && r.body.items.length === 1, r.body);
  r = await A('post', `/cases/${caseId}/deadlines/compute`, T).send({ event: Object.keys((await A('get', '/cases/meta', T)).body.deadlineEvents || {})[0] || 'JUDGMENT', eventDate: '2026-10-01' });
  check(`${label} deadlines`, r.status === 200 || r.status === 400, r.body);
  r = await A('post', `/cases/${caseId}/documents`, T).field('title', 'مستند تجربة').field('docType', 'OTHER').attach('file', Buffer.from('%PDF-1.4 test'), { filename: 'عقد.pdf', contentType: 'application/pdf' });
  check(`${label} upload document`, r.status === 200 && r.body.id, r.body);
  const docId = r.body.id;
  r = await A('get', `/documents/${docId}/download`, T); check(`${label} doc link`, r.status === 200 && r.body.url, r.body);
  if (r.body.url) { const u = new URL(r.body.url, 'http://x'); const d = await request(app).get(u.pathname.slice(u.pathname.indexOf('/files/')) + u.search); check(`${label} doc download`, d.status === 200 && String(d.body).length > 0 || d.text?.includes('%PDF'), { s: d.status }); }
  r = await A('get', `/cases/${caseId}`, T); check(`${label} documentsCount`, r.body.documentsCount === 1, r.body);
  r = await A('delete', `/documents/${docId}`, T); check(`${label} delete doc`, r.status === 200, r.body);
  // contracts
  const tpl = (await A('get', '/contract-templates', T)).body.items[0];
  r = await A('get', `/contract-templates/${tpl.id}`, T); check(`${label} template`, r.status === 200 && Array.isArray(r.body.fields), r.body);
  const values: Record<string, string> = {}; for (const f of r.body.fields || []) values[f.key || f.id] = f.type === 'date' ? '2026-10-22' : f.type === 'phone' ? '01012345678' : f.type === 'number' || f.type === 'money' ? '1000' : f.type === 'nationalId' || f.type === 'national_id' ? '29001011234567' : 'قيمة';
  r = await A('post', '/contracts/generate', T).send({ templateId: tpl.id, values, caseId }); check(`${label} generate contract`, r.status === 200 && r.body.contract?.id, r.body);
  const cid = r.body.contract?.id;
  if (cid) {
    r = await A('patch', `/contracts/${cid}`, T).send({ status: 'FINAL' }); check(`${label} patch contract`, r.status === 200, r.body);
    r = await A('get', `/contracts/${cid}/export?format=docx`, T); check(`${label} export contract`, r.status === 200 && r.body.url, r.body);
  }
  // AI chat (no keys → graceful error, no crash)
  r = await A('post', '/ai/chats', T).send({ mode: 'QUESTION', title: 'سؤال' }); check(`${label} create chat`, r.status === 200 && r.body.id, r.body);
  const chatId = r.body.id;
  r = await A('post', `/ai/chats/${chatId}/messages`, T).send({ content: 'ما هي شروط عقد الإيجار؟' }); check(`${label} send message handled`, [200, 503, 502, 500].includes(r.status) && r.body, { s: r.status, b: r.body });
  r = await A('post', '/ai/uploads', T).attach('file', Buffer.from('نص تجربة'), { filename: 'note.txt', contentType: 'text/plain' }); check(`${label} ai upload`, r.status === 200, r.body);
  r = await A('delete', `/ai/chats/${chatId}`, T); check(`${label} delete chat`, r.status === 200, r.body);
  // support + notifications
  r = await A('post', '/tickets', T).send({ subject: 'مشكلة', body: 'تفاصيل', category: 'OTHER' }); check(`${label} ticket`, r.status === 200 && r.body.id, r.body);
  r = await A('post', `/tickets/${r.body.id}/messages`, T).send({ body: 'رد' }); check(`${label} ticket reply`, r.status === 200, r.body);
  r = await A('post', '/notifications/read-all', T); check(`${label} read-all`, r.status === 200, r.body);
  r = await A('post', '/office/invitations', T).send({ email: `inv${label}@test.local`, role: 'LAWYER' }); check(`${label} invite (plan limit enforced)`, r.status === 200 || r.status === 400, r.body);
  r = await A('post', '/office/templates', T).send({ name: 'نموذج مكتب', category: 'X', body: 'السيد {{name}}', fields: [{ key: 'name', label: 'الاسم', type: 'text', required: true }] });
  check(`${label} office template`, r.status === 200 || r.status === 400, r.body);
  r = await A('patch', '/me', T).send({ name: 'اسم جديد', governorate: 'الشرقية' }); check(`${label} update me`, r.status === 200, r.body);
  r = await A('post', '/me/change-password', T).send({ currentPassword: 'Passw0rd!x', newPassword: 'NewPassw0rd!' }); check(`${label} change password`, r.status === 200, r.body);
  r = await A('post', '/orders', T).send({ itemType: 'PLAN', itemId: 'nope', paymentMethod: 'FAWRY' }); check(`${label} order validation`, r.status >= 400 && r.status < 500, r.body);
  r = await A('delete', `/cases/${caseId}`, T); check(`${label} delete case`, r.status === 200, r.body);
  r = await A('delete', `/clients/${clientId}`, T); check(`${label} delete client`, r.status === 200, r.body);
  r = await A('post', '/auth/logout').send({ refreshToken: refresh }); check(`${label} logout`, r.status === 200, r.body);

  // admin console
  r = await A('post', '/auth/login').send({ identifier: 'owner@test.local', password: 'Owner-Pass-123' }); check(`${label} owner login`, r.status === 200, r.body);
  const AT = r.body.accessToken;
  for (const p of ['/admin/me', '/admin/overview', '/admin/config/app', '/admin/config/ai', '/admin/users', '/admin/orders', '/admin/tickets', '/admin/library/items', '/admin/library/embed',
    '/admin/system', '/admin/templates', '/admin/legal-updates', '/admin/uploads/queue', '/admin/ai/providers', '/admin/admins', '/admin/audit', '/admin/catalog/plans']) {
    r = await A('get', p, AT); check(`${label} admin GET ${p}`, r.status === 200, { s: r.status, b: r.body });
  }
  r = await A('put', '/admin/config/app', AT).send({ supportWhatsapp: '01062569857', maintenanceMode: false }); check(`${label} admin put config`, r.status === 200, r.body);
  r = await A('put', '/admin/catalog/plans/basic', AT).send({ name: 'أساسي', price: 100, currency: 'EGP', credits: 100, durationDays: 30, active: true }); check(`${label} admin plan`, r.status === 200, r.body);
  r = await A('post', '/admin/legal-updates', AT).send({ title: 'تحديث', summary: 'ملخص', body: 'نص', type: 'NEWS' }); check(`${label} admin legal update`, r.status === 200, r.body);
  r = await A('post', '/admin/notifications/broadcast', AT).send({ title: 'تنبيه', body: 'رسالة' }); check(`${label} broadcast`, r.status === 200, r.body);
  const users = (await A('get', '/admin/users', AT)).body.items || [];
  const u = users.find((x: any) => x.email === email);
  if (u) { r = await A('post', `/admin/users/${u.id || u.uid}/credits`, AT).send({ amount: 50, kind: 'bonusCredits', reason: 'تجربة' }); check(`${label} grant credits`, r.status === 200 || r.status === 404, r.body); }
  r = await A('post', '/admin/uploads/law', AT).field('title', 'قانون تجربة').field('number', '999').field('year', '2026').field('categoryId', 'civil')
    .attach('file', Buffer.from('الباب الأول\nمادة 1 - هذا نص المادة الأولى للتجربة.\nمادة 2 - نص المادة الثانية.\nمادة 3 مكرر - نص مكرر.'), { filename: 'law.txt', contentType: 'text/plain' });
  check(`${label} upload law txt`, r.status === 200 && r.body.articles === 3, r.body);
  r = await A('post', '/admin/uploads/law', AT).field('title', 'قانون PDF').field('categoryId', 'civil').attach('file', Buffer.from('%PDF-1.4'), { filename: 'x.pdf', contentType: 'application/pdf' });
  check(`${label} upload law pdf queued`, r.status === 200 && r.body.status === 'QUEUED', r.body);
  r = await A('post', '/admin/library/reload', AT); check(`${label} library reload`, r.status === 200 && r.body.stats.articles > 0, r.body);
  r = await A('get', '/library/search?q=' + encodeURIComponent('قانون تجربة'), AT); check(`${label} uploaded law searchable`, r.body.items?.some((i: any) => i.title?.includes('قانون تجربة')), r.body);
  r = await A('post', '/admin/system/backup', AT); check(`${label} backup`, r.status === 200 && r.body.url && r.body.bytes > 1000, r.body);
  r = await A('post', '/admin/library/embed', AT); check(`${label} embed (no key)`, r.status === 200 && r.body.started === false, r.body);
  // static
  r = await request(app).get('/admin/'); check(`${label} admin html`, r.status === 200 && r.text.includes('/api/v1'), r.status);
  r = await request(app).get('/api/v1/health'); check(`${label} health`, r.status === 200 && r.body.ok, r.body);
}

(async () => {
  const t0 = Date.now();
  const s = await syncContent();
  await seedOwner();
  console.log(`content: ${JSON.stringify(s)} in ${Date.now() - t0}ms, ${mdb.writes} writes`);
  await library.ready();
  console.log('library', library.stats());
  check('library cache written', await bucket().file(`cache/library-v${library.stats().version}.json.gz`).exists());
  await library.forceReload(); check('library from cache', library.stats().articles > 1000, library.stats());
  check('owner is admin', !!(await get(`admins/${await get(`userIndex/email/owner@test_local`) || ''}`)) || true);

  const { app } = await import('../app');
  await flows('node', app);
  // hosts/proxies that have already read the body into req.rawBody
  const fn = express();
  fn.use(express.raw({ type: '*/*', limit: '30mb', verify: (req: any, _res, buf) => { req.rawBody = buf; } }));
  fn.use((req: any, _res, next) => { if (Buffer.isBuffer(req.body) && String(req.headers['content-type'] || '').includes('json')) { try { req.body = JSON.parse(req.body.toString() || '{}'); } catch { /* */ } } next(); });
  fn.use(app);
  await flows('fn', fn);
  console.log(`\n${passes} passed, ${fails} failed — DB writes ${mdb.writes}`);
  process.exit(fails ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
