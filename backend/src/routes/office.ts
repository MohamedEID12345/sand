import { Router, Request } from 'express';
import { uploadSingle } from '../upload';
import { db, get, listPage, newId, now, bucket } from '../db';
import { bad, notFound, ok, required, str, wrap, forbidden } from '../http';
import { need, ROLE_LABELS, can } from '../services/auth';
import { appConfig } from '../services/config';
import { assertCanAfford, charge, getWallet } from '../services/wallet';
import { answer } from '../services/assistant';

const r = Router();
const upload = { single: (field: string) => uploadSingle(field, 25 * 1024 * 1024) };
const base = (req: Request, c: string) => `officeData/${req.user!.officeId}/${c}`;
const pick = (b: any, keys: string[]) => Object.fromEntries(keys.filter((k) => b?.[k] !== undefined).map((k) => [k, typeof b[k] === 'string' ? str(b[k], 10000) : b[k]]));
const label = (list: { value: string; label: string }[], v?: string) => list.find((x) => x.value === v)?.label || null;
const CASE_FIELDS = ['caseTypeId', 'caseNumber', 'year', 'court', 'circuit', 'clientId', 'opponent', 'lawyers', 'status', 'priority', 'description', 'customFields'];
const HEARING_FIELDS = ['hearingDate', 'court', 'circuit', 'purpose', 'notes', 'status', 'decision'];
const CLIENT_FIELDS = ['name', 'phone', 'email', 'address', 'notes', 'feesTotal', 'feesPaid'];

export async function caseMeta() {
  const m = (await get<any>('config/caseMeta')) || {};
  return {
    statuses: m.statuses || [{ value: 'ACTIVE', label: 'متداولة' }, { value: 'SUSPENDED', label: 'موقوفة' }, { value: 'RESERVED', label: 'محجوزة للحكم' }, { value: 'CLOSED', label: 'منتهية' }],
    priorities: m.priorities || [{ value: 'LOW', label: 'عادية' }, { value: 'MEDIUM', label: 'متوسطة' }, { value: 'HIGH', label: 'عاجلة' }],
    hearingStatuses: m.hearingStatuses || [{ value: 'SCHEDULED', label: 'محددة' }, { value: 'ADJOURNED', label: 'مؤجلة' }, { value: 'DONE', label: 'تمت' }, { value: 'CANCELLED', label: 'ملغاة' }],
    caseTypes: m.caseTypes || [['civil', 'مدني'], ['criminal', 'جنائي'], ['family', 'أحوال شخصية'], ['labor', 'عمالي'], ['commercial', 'تجاري'], ['administrative', 'إداري'], ['tax', 'ضرائب']]
      .map(([id, name]) => ({ id, name, fields: [] })),
    documentTypes: m.documentTypes || [{ value: 'PLEADING', label: 'صحيفة' }, { value: 'MEMO', label: 'مذكرة' }, { value: 'JUDGMENT', label: 'حكم' }, { value: 'EVIDENCE', label: 'مستند' }, { value: 'OTHER', label: 'أخرى' }],
    deadlineEvents: m.deadlineEvents || Object.entries(DEADLINE_RULES).map(([value, x]) => ({ value, label: x.label })),
    analysisTypes: m.analysisTypes || [{ code: 'SUMMARY', label: 'ملخص وتقييم القضية', description: 'الوقائع، المسائل القانونية، نقاط القوة والضعف', credits: 3 },
      { code: 'DEFENSE', label: 'خطة الدفاع', description: 'الدفوع الشكلية والموضوعية مع السند', credits: 4 }],
  };
}
/** Defaults; override in /config/deadlineRules. Sources point at articles that exist in the library. */
const DEADLINE_RULES: Record<string, { label: string; title: string; days: number; rule: string; legalSource: string }> = {
  JUDGMENT_CIVIL_APPEAL: { label: 'صدور حكم مدني ابتدائي', title: 'ميعاد الاستئناف', days: 40, rule: '40 يومًا من تاريخ صدور الحكم ما لم ينص القانون على غير ذلك', legalSource: 'قانون المرافعات — المادة 227' },
  JUDGMENT_CASSATION: { label: 'صدور حكم استئنافي', title: 'ميعاد الطعن بالنقض', days: 60, rule: '60 يومًا من تاريخ صدور الحكم', legalSource: 'قانون المرافعات — المادة 252' },
  JUDGMENT_URGENT_APPEAL: { label: 'صدور حكم في مادة مستعجلة', title: 'ميعاد استئناف الحكم المستعجل', days: 15, rule: '15 يومًا في المواد المستعجلة', legalSource: 'قانون المرافعات — المادة 227' },
};
r.get('/cases/meta', wrap(async () => caseMeta()));

async function caseView(req: Request, id: string, c: any) {
  const meta = await caseMeta(), client = c.clientId ? await get<any>(`${base(req, 'clients')}/${c.clientId}`) : null;
  return { ...c, id, clientName: client?.name || null, caseTypeName: meta.caseTypes.find((t: any) => t.id === c.caseTypeId)?.name || null,
    statusLabel: label(meta.statuses, c.status), priorityLabel: label(meta.priorities, c.priority) };
}
r.get('/cases', need('cases.read'), wrap(async (req) => {
  const q = String(req.query.q || ''), status = req.query.status as string;
  const p = await listPage<any>(base(req, 'cases'), { cursor: req.query.cursor as string, sortKey: 'updatedAt',
    filter: (c) => (!status || c.status === status) && (!q || `${c.caseNumber} ${c.opponent} ${c.description} ${c.court}`.includes(q)) });
  return { ...p, items: await Promise.all(p.items.map((c: any) => caseView(req, c.id, c))) };
}));
r.get('/cases/:id', need('cases.read'), wrap(async (req) => {
  const c = await get<any>(`${base(req, 'cases')}/${req.params.id}`);
  if (!c) throw notFound('القضية');
  return caseView(req, req.params.id, c);
}));
r.post('/cases', need('cases.create'), wrap(async (req) => {
  const id = newId(), t = now();
  const c = { status: 'ACTIVE', priority: 'MEDIUM', ...pick(req.body, CASE_FIELDS), documentsCount: 0, hearingsCount: 0, createdBy: req.user!.uid, createdAt: t, updatedAt: t };
  await db.ref(`${base(req, 'cases')}/${id}`).set(c);
  return caseView(req, id, c);
}));
r.patch('/cases/:id', need('cases.update'), wrap(async (req) => {
  const path = `${base(req, 'cases')}/${req.params.id}`;
  if (!(await get(path))) throw notFound('القضية');
  await db.ref(path).update({ ...pick(req.body, CASE_FIELDS), updatedAt: now() });
  return caseView(req, req.params.id, await get(path));
}));
r.delete('/cases/:id', need('cases.delete'), wrap(async (req) => { await db.ref(`${base(req, 'cases')}/${req.params.id}`).remove(); return ok(); }));

async function hearingView(req: Request, h: any) {
  const c = h.caseId ? await get<any>(`${base(req, 'cases')}/${h.caseId}`) : null, meta = await caseMeta();
  return { ...h, caseNumber: c?.caseNumber || null, caseTitle: c ? `${c.caseNumber || ''} ${c.opponent ? '— ' + c.opponent : ''}`.trim() : null, statusLabel: label(meta.hearingStatuses, h.status) };
}
async function syncNextHearing(req: Request, caseId: string) {
  const all = Object.values<any>((await get(base(req, 'hearings'))) || {}).filter((h) => h.caseId === caseId);
  const next = all.filter((h) => h.hearingDate >= now() && h.status !== 'CANCELLED').sort((a, b) => a.hearingDate.localeCompare(b.hearingDate))[0];
  await db.ref(`${base(req, 'cases')}/${caseId}`).update({ nextHearingAt: next?.hearingDate || null, hearingsCount: all.length });
}
r.get('/hearings', need('hearings.read'), wrap(async (req) => {
  const from = req.query.from as string, to = req.query.to as string;
  const p = await listPage<any>(base(req, 'hearings'), { cursor: req.query.cursor as string, sortKey: 'hearingDate', asc: true,
    filter: (h) => (!from || h.hearingDate >= from) && (!to || h.hearingDate <= to) });
  return { ...p, items: await Promise.all(p.items.map((h: any) => hearingView(req, h))) };
}));
r.get('/cases/:id/hearings', need('hearings.read'), wrap(async (req) => {
  const p = await listPage<any>(base(req, 'hearings'), { sortKey: 'hearingDate', limit: 200, filter: (h) => h.caseId === req.params.id });
  return { ...p, items: await Promise.all(p.items.map((h: any) => hearingView(req, h))) };
}));
r.get('/hearings/:id', need('hearings.read'), wrap(async (req) => {
  const h = await get<any>(`${base(req, 'hearings')}/${req.params.id}`);
  if (!h) throw notFound('الجلسة');
  return hearingView(req, { ...h, id: req.params.id });
}));
r.post('/cases/:id/hearings', need('hearings.create'), wrap(async (req) => {
  required(req.body, ['hearingDate']);
  const id = newId(), h = { caseId: req.params.id, status: 'SCHEDULED', ...pick(req.body, HEARING_FIELDS), createdAt: now() };
  await db.ref(`${base(req, 'hearings')}/${id}`).set(h);
  await syncNextHearing(req, req.params.id);
  return hearingView(req, { ...h, id });
}));
r.patch('/hearings/:id', need('hearings.update'), wrap(async (req) => {
  const path = `${base(req, 'hearings')}/${req.params.id}`, h = await get<any>(path);
  if (!h) throw notFound('الجلسة');
  await db.ref(path).update({ ...pick(req.body, HEARING_FIELDS), updatedAt: now() });
  await syncNextHearing(req, h.caseId);
  return hearingView(req, { ...(await get<any>(path)), id: req.params.id });
}));
r.delete('/hearings/:id', need('hearings.delete'), wrap(async (req) => {
  const path = `${base(req, 'hearings')}/${req.params.id}`, h = await get<any>(path);
  await db.ref(path).remove();
  if (h?.caseId) await syncNextHearing(req, h.caseId);
  return ok();
}));

async function clientView(req: Request, id: string, c: any, withCases = false) {
  const cases = Object.entries<any>((await get(base(req, 'cases'))) || {}).filter(([, x]) => x.clientId === id);
  return { ...c, id, casesCount: cases.length, cases: withCases ? await Promise.all(cases.map(([cid, x]) => caseView(req, cid, x))) : null };
}
r.get('/clients', need('clients.read'), wrap(async (req) => {
  const q = String(req.query.q || '');
  const p = await listPage<any>(base(req, 'clients'), { cursor: req.query.cursor as string, filter: (c) => !q || `${c.name} ${c.phone}`.includes(q) });
  return { ...p, items: await Promise.all(p.items.map((c: any) => clientView(req, c.id, c))) };
}));
r.get('/clients/:id', need('clients.read'), wrap(async (req) => {
  const c = await get<any>(`${base(req, 'clients')}/${req.params.id}`);
  if (!c) throw notFound('العميل');
  return clientView(req, req.params.id, c, true);
}));
r.post('/clients', need('clients.create'), wrap(async (req) => {
  required(req.body, ['name']);
  const id = newId(), c = { ...pick(req.body, CLIENT_FIELDS), createdAt: now() };
  await db.ref(`${base(req, 'clients')}/${id}`).set(c);
  return clientView(req, id, c);
}));
r.patch('/clients/:id', need('clients.update'), wrap(async (req) => {
  const path = `${base(req, 'clients')}/${req.params.id}`;
  if (!(await get(path))) throw notFound('العميل');
  await db.ref(path).update({ ...pick(req.body, CLIENT_FIELDS), updatedAt: now() });
  return clientView(req, req.params.id, await get(path));
}));
r.delete('/clients/:id', need('clients.delete'), wrap(async (req) => { await db.ref(`${base(req, 'clients')}/${req.params.id}`).remove(); return ok(); }));

r.get('/cases/:id/notes', need('cases.read'), wrap(async (req) => listPage<any>(base(req, 'notes'), { limit: 200,
  filter: (n) => n.caseId === req.params.id && (n.visibility !== 'PRIVATE' || n.authorUid === req.user!.uid) })));
r.post('/cases/:id/notes', need('cases.read'), wrap(async (req) => {
  required(req.body, ['body']);
  const u = await get<any>(`users/${req.user!.uid}`), id = newId();
  const n = { caseId: req.params.id, body: str(req.body.body, 10000), visibility: req.body.visibility || 'OFFICE', authorUid: req.user!.uid, authorName: u?.name, createdAt: now() };
  await db.ref(`${base(req, 'notes')}/${id}`).set(n);
  return { ...n, id };
}));
r.delete('/notes/:id', wrap(async (req) => {
  const n = await get<any>(`${base(req, 'notes')}/${req.params.id}`);
  if (n && n.authorUid !== req.user!.uid && !can(req.user!, 'cases.delete')) throw forbidden();
  await db.ref(`${base(req, 'notes')}/${req.params.id}`).remove();
  return ok();
}));

r.get('/cases/:id/documents', need('documents.read'), wrap(async (req) => listPage<any>(base(req, 'documents'), { limit: 200, filter: (d) => d.caseId === req.params.id })));
r.post('/cases/:id/documents', need('documents.create'), upload.single('file'), wrap(async (req) => {
  const f = (req as any).file as Express.Multer.File | undefined;
  if (!f) throw bad('لم يتم إرفاق ملف', { file: 'مطلوب' });
  const maxMb = (await appConfig()).maxUploadMb || 15;
  if (f.size > maxMb * 1024 * 1024) throw bad(`الحد الأقصى ${maxMb} ميجا`, { file: 'حجم كبير' });
  const id = newId(), key = `offices/${req.user!.officeId}/cases/${req.params.id}/${id}-${f.originalname}`;
  await bucket().file(key).save(f.buffer, { contentType: f.mimetype });
  const d = { caseId: req.params.id, title: str(req.body?.title, 200) || f.originalname, docType: req.body?.docType || 'OTHER', fileName: f.originalname,
    mimeType: f.mimetype, sizeBytes: f.size, storageKey: key, ocrStatus: 'NONE', createdAt: now(), uploadedBy: req.user!.uid };
  await db.ref(`${base(req, 'documents')}/${id}`).set(d);
  await db.ref(`${base(req, 'cases')}/${req.params.id}/documentsCount`).transaction((n) => (n || 0) + 1);
  return { ...d, id };
}));
r.delete('/documents/:id', need('documents.delete'), wrap(async (req) => {
  const d = await get<any>(`${base(req, 'documents')}/${req.params.id}`);
  if (!d) throw notFound('المستند');
  await bucket().file(d.storageKey).delete({ ignoreNotFound: true });
  await db.ref(`${base(req, 'documents')}/${req.params.id}`).remove();
  await db.ref(`${base(req, 'cases')}/${d.caseId}/documentsCount`).transaction((n) => Math.max(0, (n || 1) - 1));
  return ok();
}));
r.get('/documents/:id/download', need('documents.read'), wrap(async (req) => {
  const d = await get<any>(`${base(req, 'documents')}/${req.params.id}`);
  if (!d) throw notFound('المستند');
  const [url] = await bucket().file(d.storageKey).getSignedUrl({ action: 'read', expires: Date.now() + 10 * 60e3 });
  return { url, fileName: d.fileName, mimeType: d.mimeType };
}));

const withDaysLeft = (x: any) => ({ ...x, daysLeft: Math.ceil((Date.parse(x.dueDate) - Date.now()) / 864e5) });
r.get('/cases/:id/deadlines', need('cases.read'), wrap(async (req) => {
  const p = await listPage<any>(base(req, 'deadlines'), { limit: 200, sortKey: 'dueDate', asc: true, filter: (d) => d.caseId === req.params.id });
  return { ...p, items: p.items.map(withDaysLeft) };
}));
r.post('/cases/:id/deadlines/compute', need('cases.update'), wrap(async (req) => {
  required(req.body, ['event', 'eventDate']);
  const rule = (await get<any>(`config/deadlineRules/${req.body.event}`)) || DEADLINE_RULES[String(req.body.event)];
  if (!rule) throw bad('حدث غير معروف', { event: 'غير مدعوم' });
  const due = new Date(Date.parse(String(req.body.eventDate)) + rule.days * 864e5).toISOString().slice(0, 10), id = newId();
  await db.ref(`${base(req, 'deadlines')}/${id}`).set({ caseId: req.params.id, title: rule.title, dueDate: due, rule: rule.rule, legalSource: rule.legalSource,
    calculation: `${req.body.eventDate} + ${rule.days} يومًا = ${due} (راجع العطلات الرسمية وامتداد الميعاد)`, status: 'OPEN', createdAt: now() });
  const all = await listPage<any>(base(req, 'deadlines'), { limit: 200, sortKey: 'dueDate', asc: true, filter: (x) => x.caseId === req.params.id });
  return { ...all, items: all.items.map(withDaysLeft) };
}));

r.get('/cases/:id/analyses', need('cases.read'), wrap(async (req) => listPage<any>(base(req, 'analyses'), { limit: 50, filter: (a) => a.caseId === req.params.id })));
r.get('/analyses/:id', need('cases.read'), wrap(async (req) => {
  const a = await get<any>(`${base(req, 'analyses')}/${req.params.id}`);
  if (!a) throw notFound('التحليل');
  return { ...a, id: req.params.id };
}));
r.post('/cases/:id/analysis', need('cases.read'), wrap(async (req) => {
  const c = await get<any>(`${base(req, 'cases')}/${req.params.id}`);
  if (!c) throw notFound('القضية');
  const meta = await caseMeta(), type = String(req.body?.type || 'SUMMARY');
  const t = meta.analysisTypes.find((x: any) => x.code === type) || meta.analysisTypes[0];
  const uid = req.user!.uid, op = `CASE_${t.code}`;
  await assertCanAfford(uid, op);
  const notes = Object.values<any>((await get(base(req, 'notes'))) || {}).filter((n) => n.caseId === req.params.id).map((n) => n.body).join('\n');
  const facts = `نوع القضية: ${c.caseTypeId || ''}\nالمحكمة: ${c.court || ''}\nالخصم: ${c.opponent || ''}\nالوصف: ${c.description || ''}\nملاحظات المكتب:\n${notes}`.slice(0, 12000);
  const res = await answer(t.code === 'DEFENSE' ? 'MEMO' : 'RESEARCH', `${t.label}: ${t.description}`, [], facts);
  const ch = await charge(uid, op, `${t.label} — ${c.caseNumber || ''}`), id = newId();
  const a = { caseId: req.params.id, type: t.code, typeLabel: t.label, status: 'DONE', createdAt: now(), creditsCharged: ch.credits,
    summary: res.content.split('\n').find((l) => l.trim())?.slice(0, 300) || '', sections: [{ title: t.label, body: res.content }],
    citations: res.citations, disclaimer: (await appConfig()).aiDisclaimer, error: null };
  await db.ref(`${base(req, 'analyses')}/${id}`).set(a);
  return { analysis: { ...a, id }, wallet: await getWallet(uid), charge: ch };
}));

const memberView = async (uid: string, m: any) => { const u = await get<any>(`users/${uid}`); return { id: uid, name: u?.name, email: u?.email, role: m.role, roleLabel: ROLE_LABELS[m.role], status: m.status || 'ACTIVE' }; };
r.get('/office', wrap(async (req) => {
  const o = await get<any>(`offices/${req.user!.officeId}`);
  if (!o) throw notFound('المكتب');
  const members = await Promise.all(Object.entries<any>(o.members || {}).map(([uid, m]) => memberView(uid, m)));
  const sub = await get<any>(`subscriptions/${o.ownerUid}`);
  return { id: req.user!.officeId, name: o.name, seatsUsed: members.length, seatsMax: sub?.limits?.teamMembers || 1, members,
    roles: Object.entries(ROLE_LABELS).filter(([k]) => k !== 'OWNER').map(([value, l]) => ({ value, label: l })) };
}));
r.post('/office/invitations', need('office.manage'), wrap(async (req) => {
  required(req.body, ['email', 'role']);
  if (!['ADMIN', 'LAWYER', 'ASSISTANT'].includes(req.body.role)) throw bad('دور غير صالح', { role: 'غير صالح' });
  const o = await get<any>(`offices/${req.user!.officeId}`), sub = await get<any>(`subscriptions/${o?.ownerUid}`);
  if (Object.keys(o?.members || {}).length >= (sub?.limits?.teamMembers || 1)) throw bad('وصلت للحد الأقصى لأعضاء الفريق في باقتك');
  await db.ref(`officeInvitations/${newId()}`).set({ officeId: req.user!.officeId, email: String(req.body.email).trim().toLowerCase(), role: req.body.role,
    invitedBy: req.user!.uid, createdAt: now(), status: 'PENDING' });
  return ok('تم إرسال الدعوة؛ ينضم العضو عند التسجيل بنفس البريد');
}));
r.patch('/office/members/:id', need('office.manage'), wrap(async (req) => {
  const o = req.user!.officeId;
  if (!(await get(`offices/${o}/members/${req.params.id}`))) throw notFound('العضو');
  const upd: any = {};
  if (req.body?.role && req.body.role !== 'OWNER') { upd[`offices/${o}/members/${req.params.id}/role`] = req.body.role; upd[`users/${req.params.id}/officeRole`] = req.body.role; }
  if (req.body?.status) upd[`offices/${o}/members/${req.params.id}/status`] = req.body.status;
  if (Object.keys(upd).length) await db.ref().update(upd);
  return memberView(req.params.id, await get(`offices/${o}/members/${req.params.id}`));
}));
r.delete('/office/members/:id', need('office.manage'), wrap(async (req) => {
  const o = await get<any>(`offices/${req.user!.officeId}`);
  if (o?.ownerUid === req.params.id) throw bad('لا يمكن حذف مالك المكتب');
  await db.ref(`offices/${req.user!.officeId}/members/${req.params.id}`).remove();
  return ok();
}));
export default r;
