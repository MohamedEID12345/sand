import { NextFunction, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import crypto from 'node:crypto';
import { env } from '../env';
import { db, get, now, set } from '../db';
import { unauthorized, forbidden } from '../http';

export interface AuthUser { uid: string; officeId: string; role: string; }
declare global { namespace Express { interface Request { user?: AuthUser } } }

export const ROLE_PERMISSIONS: Record<string, string[]> = {
  OWNER: ['*'],
  ADMIN: ['cases.*', 'hearings.*', 'documents.*', 'clients.*', 'office.members.read', 'office.manage'],
  LAWYER: ['cases.*', 'hearings.*', 'documents.*', 'clients.*', 'office.members.read'],
  ASSISTANT: ['cases.read', 'hearings.create', 'hearings.read', 'documents.read', 'clients.read'],
};
export const ROLE_LABELS: Record<string, string> = { OWNER: 'مالك المكتب', ADMIN: 'مدير', LAWYER: 'محامٍ', ASSISTANT: 'مساعد' };

export function can(user: AuthUser, perm: string) {
  return (ROLE_PERMISSIONS[user.role] || []).some((p) => p === '*' || p === perm || (p.endsWith('.*') && perm.startsWith(p.slice(0, -1))));
}
export const need = (perm: string) => (req: Request, _res: Response, next: NextFunction) =>
  (req.user && can(req.user, perm) ? next() : next(forbidden('صلاحياتك في المكتب لا تسمح بهذه العملية')));

export function requireAuth(req: Request, _res: Response, next: NextFunction) {
  const h = req.header('Authorization') || '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : '';
  if (!token) return next(unauthorized());
  try {
    const p = jwt.verify(token, env.jwtSecret) as any;
    req.user = { uid: p.sub, officeId: p.oid, role: p.role };
    next();
  } catch {
    next(unauthorized('انتهت الجلسة، سجّل الدخول مرة أخرى'));
  }
}

/** Platform admins are listed under /admins/{uid} = { role: 'SUPER'|'CONTENT'|'SUPPORT'|'FINANCE' } (set with scripts/make-admin). */
export const requireAdmin = (...roles: string[]) => async (req: Request, _res: Response, next: NextFunction) => {
  const a = req.user ? await get<any>(`admins/${req.user.uid}`) : null;
  if (!a || (roles.length && a.role !== 'SUPER' && !roles.includes(a.role))) return next(forbidden('هذه الصفحة للمشرفين فقط'));
  (req as any).adminRole = a.role;
  next();
};

export async function issueTokens(uid: string, deviceId?: string) {
  const u = await get<any>(`users/${uid}`);
  const accessToken = jwt.sign({ sub: uid, oid: u?.officeId, role: u?.officeRole || 'OWNER' }, env.jwtSecret, { expiresIn: env.accessTtlSec });
  const tid = crypto.randomBytes(16).toString('hex'), secret = crypto.randomBytes(32).toString('hex');
  await set(`refreshTokens/${tid}`, { uid, deviceId: deviceId || null, hash: sha256(secret), createdAt: now(),
    expiresAt: new Date(Date.now() + env.refreshTtlDays * 864e5).toISOString() });
  return { accessToken, refreshToken: `${tid}.${secret}`, expiresIn: env.accessTtlSec };
}
/** Rotating refresh token: consumed on use; a new pair is issued. */
export async function rotateRefresh(refreshToken: string, deviceId?: string) {
  const [tid, secret] = String(refreshToken || '').split('.');
  if (!tid || !secret) throw unauthorized();
  const rec = await get<any>(`refreshTokens/${tid}`);
  if (!rec || rec.hash !== sha256(secret) || rec.expiresAt < now()) throw unauthorized('انتهت الجلسة، سجّل الدخول مرة أخرى');
  await db.ref(`refreshTokens/${tid}`).remove();
  const u = await get<any>(`users/${rec.uid}`);
  if (u?.status === 'SUSPENDED') throw unauthorized('الحساب موقوف');
  return { uid: rec.uid as string, tokens: await issueTokens(rec.uid, deviceId) };
}
export async function revokeRefresh(refreshToken?: string) {
  const [tid] = String(refreshToken || '').split('.');
  if (tid) await db.ref(`refreshTokens/${tid}`).remove();
}
export const sha256 = (s: string) => crypto.createHash('sha256').update(s).digest('hex');
