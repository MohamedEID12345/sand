import { Router } from 'express';
import { db, get, listPage, newId, now } from '../db';
import { notFound, ok, required, str, wrap } from '../http';
import * as lib from '../services/library';
import { allTemplates } from '../services/templates';

const r = Router();
r.get('/notifications', wrap(async (req) => {
  const p = await listPage<any>(`notifications/${req.user!.uid}`, { cursor: req.query.cursor as string });
  return { ...p, items: p.items.map((n: any) => ({ ...n, read: !!n.readAt })) };
}));
r.get('/notifications/unread-count', wrap(async (req) => ({ count: Object.values<any>((await get(`notifications/${req.user!.uid}`)) || {}).filter((n) => !n.readAt).length })));
r.post('/notifications/:id/read', wrap(async (req) => { await db.ref(`notifications/${req.user!.uid}/${req.params.id}/readAt`).set(now()); return ok(); }));
r.post('/notifications/read-all', wrap(async (req) => {
  const all = (await get<Record<string, any>>(`notifications/${req.user!.uid}`)) || {}, upd: Record<string, string> = {};
  for (const [id, n] of Object.entries(all)) if (!n.readAt) upd[`notifications/${req.user!.uid}/${id}/readAt`] = now();
  if (Object.keys(upd).length) await db.ref().update(upd);
  return ok();
}));

export const TICKET_CATS = [{ value: 'BILLING', label: 'الدفع والاشتراكات' }, { value: 'AI', label: 'المساعد الذكي' }, { value: 'LIBRARY', label: 'المكتبة القانونية' },
  { value: 'ACCOUNT', label: 'الحساب' }, { value: 'BUG', label: 'مشكلة تقنية' }, { value: 'OTHER', label: 'أخرى' }];
export const TICKET_STATUS: Record<string, string> = { OPEN: 'مفتوحة', PENDING: 'بانتظار ردك', ANSWERED: 'تم الرد', CLOSED: 'مغلقة' };
r.get('/tickets/categories', wrap(async () => ({ items: TICKET_CATS, nextCursor: null })));
r.get('/tickets', wrap(async (req) => {
  const p = await listPage<any>(`tickets/${req.user!.uid}`, { cursor: req.query.cursor as string, sortKey: 'updatedAt' });
  return { ...p, items: p.items.map((t: any) => ({ ...t, statusLabel: TICKET_STATUS[t.status] })) };
}));
r.post('/tickets', wrap(async (req) => {
  required(req.body, ['subject', 'body']);
  const id = newId(), t = now(), u = await get<any>(`users/${req.user!.uid}`);
  const ticket = { subject: str(req.body.subject, 200), category: req.body.category || 'OTHER', priority: req.body.priority || 'NORMAL', status: 'OPEN', createdAt: t, updatedAt: t, lastMessage: str(req.body.body, 200) };
  await db.ref().update({ [`tickets/${req.user!.uid}/${id}`]: ticket,
    [`ticketMessages/${id}/${newId()}`]: { body: str(req.body.body, 10000), fromSupport: false, authorName: u?.name, createdAt: t },
    [`supportQueue/${id}`]: { uid: req.user!.uid, subject: ticket.subject, category: ticket.category, status: 'OPEN', updatedAt: t } });
  return { ...ticket, id, statusLabel: TICKET_STATUS.OPEN, messages: [] };
}));
r.get('/tickets/:id', wrap(async (req) => {
  const t = await get<any>(`tickets/${req.user!.uid}/${req.params.id}`);
  if (!t) throw notFound('التذكرة');
  const msgs = Object.entries<any>((await get(`ticketMessages/${req.params.id}`)) || {}).map(([id, m]) => ({ ...m, id })).sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  return { ...t, id: req.params.id, statusLabel: TICKET_STATUS[t.status], messages: msgs };
}));
r.post('/tickets/:id/messages', wrap(async (req) => {
  required(req.body, ['body']);
  if (!(await get(`tickets/${req.user!.uid}/${req.params.id}`))) throw notFound('التذكرة');
  const id = newId(), t = now(), u = await get<any>(`users/${req.user!.uid}`);
  const m = { body: str(req.body.body, 10000), fromSupport: false, authorName: u?.name, createdAt: t };
  await db.ref().update({ [`ticketMessages/${req.params.id}/${id}`]: m, [`tickets/${req.user!.uid}/${req.params.id}/updatedAt`]: t,
    [`tickets/${req.user!.uid}/${req.params.id}/status`]: 'OPEN', [`tickets/${req.user!.uid}/${req.params.id}/lastMessage`]: m.body!.slice(0, 200),
    [`supportQueue/${req.params.id}/status`]: 'OPEN', [`supportQueue/${req.params.id}/updatedAt`]: t });
  return { ...m, id };
}));

r.get('/search', wrap(async (req) => {
  const q = String(req.query.q || '').trim();
  if (!q) return { groups: [] };
  const o = req.user!.officeId;
  const [library, cases, clients, templates] = await Promise.all([lib.search(q), get<any>(`officeData/${o}/cases`), get<any>(`officeData/${o}/clients`), allTemplates()]);
  const item = (id: string, type: string, title: string, subtitle?: string) => ({ id, type, title, subtitle: subtitle || null, snippet: null, categoryName: null, effectiveDate: null, status: null });
  return { groups: [
    { type: 'LIBRARY', label: 'المكتبة القانونية', items: library.items.slice(0, 8) },
    { type: 'CASE', label: 'القضايا', items: Object.entries<any>(cases || {}).filter(([, c]) => `${c.caseNumber} ${c.opponent} ${c.description}`.includes(q)).slice(0, 5)
      .map(([id, c]) => item(id, 'CASE', `${c.caseNumber || ''} لسنة ${c.year || ''}`, c.opponent)) },
    { type: 'CLIENT', label: 'العملاء', items: Object.entries<any>(clients || {}).filter(([, c]) => `${c.name} ${c.phone}`.includes(q)).slice(0, 5).map(([id, c]) => item(id, 'CLIENT', c.name, c.phone)) },
    { type: 'TEMPLATE', label: 'النماذج والعقود', items: Object.entries<any>(templates).filter(([, t]) => t.active !== false && String(t.name).includes(q)).slice(0, 5)
      .map(([id, t]) => item(id, 'TEMPLATE', t.name, t.categoryLabel)) },
  ].filter((g) => g.items.length) };
}));
export default r;
