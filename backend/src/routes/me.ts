import { Router } from 'express';
import bcrypt from 'bcryptjs';
import { db, get, listPage, now } from '../db';
import { bad, ok, required, str, wrap } from '../http';
import { getWallet } from '../services/wallet';
import { appConfig } from '../services/config';
import { permissionsOf, userView } from '../services/users';

const r = Router();
export function subscriptionView(s: any) {
  const daysLeft = s.endAt ? Math.max(0, Math.ceil((Date.parse(s.endAt) - Date.now()) / 864e5)) : null;
  return { id: s.id || null, planId: s.planId, planName: s.planName, status: daysLeft === 0 ? 'EXPIRED' : s.status || 'ACTIVE', startAt: s.startAt, endAt: s.endAt, daysLeft };
}
r.get('/me/bootstrap', wrap(async (req) => {
  const { uid, role } = req.user!;
  const [user, wallet, sub, cfg, notif] = await Promise.all([userView(uid), getWallet(uid), get<any>(`subscriptions/${uid}`), appConfig(), get<Record<string, any>>(`notifications/${uid}`)]);
  return { user, wallet, subscription: sub ? subscriptionView(sub) : null, permissions: permissionsOf(role), features: cfg.features,
    unreadNotifications: Object.values(notif || {}).filter((n: any) => !n.readAt).length };
}));
r.get('/me/stats', wrap(async (req) => {
  const o = req.user!.officeId;
  const [cases, clients, hearings, contracts] = await Promise.all([get<any>(`officeData/${o}/cases`), get<any>(`officeData/${o}/clients`),
    get<any>(`officeData/${o}/hearings`), get<any>(`contracts/${req.user!.uid}`)]);
  const today = new Date().toISOString().slice(0, 10), week = new Date(Date.now() + 7 * 864e5).toISOString().slice(0, 10);
  return { activeCases: Object.values<any>(cases || {}).filter((c) => (c.status || 'ACTIVE') === 'ACTIVE').length, clients: Object.keys(clients || {}).length,
    hearingsThisWeek: Object.values<any>(hearings || {}).filter((h) => { const d = String(h.hearingDate || '').slice(0, 10); return d >= today && d <= week; }).length,
    contracts: Object.keys(contracts || {}).length };
}));
r.patch('/me', wrap(async (req) => {
  const b = req.body || {}, upd: Record<string, unknown> = {};
  if (b.name !== undefined) upd.name = str(b.name, 120);
  if (b.governorate !== undefined) upd.governorate = str(b.governorate, 40);
  const prof = { ...(b.professional || {}), ...Object.fromEntries(['syndicateNumber', 'degree', 'specialization'].filter((k) => b[k] !== undefined).map((k) => [k, b[k]])) };
  for (const [k, v] of Object.entries(prof)) if (['syndicateNumber', 'degree', 'specialization'].includes(k)) upd[`professional/${k}`] = str(v, 80);
  if (Object.keys(upd).length) await db.ref(`users/${req.user!.uid}`).update({ ...upd, updatedAt: now() });
  return userView(req.user!.uid);
}));
r.post('/me/change-password', wrap(async (req) => {
  required(req.body, ['currentPassword', 'newPassword']);
  const u = await get<any>(`users/${req.user!.uid}`);
  if (!(await bcrypt.compare(String(req.body.currentPassword), u.passwordHash))) throw bad('كلمة المرور الحالية غير صحيحة', { currentPassword: 'غير صحيحة' });
  if (String(req.body.newPassword).length < 8) throw bad('كلمة المرور قصيرة', { newPassword: '8 أحرف على الأقل' });
  await db.ref(`users/${req.user!.uid}/passwordHash`).set(await bcrypt.hash(String(req.body.newPassword), 10));
  return ok('تم تغيير كلمة المرور');
}));
r.post('/me/deletion-request', wrap(async (req) => {
  await db.ref(`deletionRequests/${req.user!.uid}`).set({ reason: str(req.body?.reason, 500) || null, createdAt: now(), status: 'PENDING' });
  return ok('تم استلام طلب حذف الحساب وسيُراجع خلال 30 يومًا');
}));
r.get('/wallet', wrap(async (req) => getWallet(req.user!.uid)));
r.get('/wallet/transactions', wrap(async (req) => listPage(`creditTx/${req.user!.uid}`, { cursor: req.query.cursor as string })));
r.get('/subscription', wrap(async (req) => { const s = await get<any>(`subscriptions/${req.user!.uid}`); return s ? subscriptionView(s) : null; }));
export default r;
