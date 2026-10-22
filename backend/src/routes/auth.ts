import { Router } from 'express';
import bcrypt from 'bcryptjs';
import crypto from 'node:crypto';
import { db, get, newId, now, set } from '../db';
import { ApiError, bad, ok, required, str, wrap } from '../http';
import { issueTokens, revokeRefresh, rotateRefresh, sha256 } from '../services/auth';
import { emailKey, normEmail, normPhone, userView } from '../services/users';
import { initWallet } from '../services/wallet';
import { appConfig } from '../services/config';
import { sendMail } from '../services/mail';

const r = Router();
const findUid = (id: string) => (id.includes('@') ? get<string>(`userIndex/email/${emailKey(id)}`) : get<string>(`userIndex/phone/${normPhone(id)}`));

r.post('/register', wrap(async (req) => {
  const b = req.body || {};
  required(b, ['name', 'email', 'phone', 'password']);
  if (!(await appConfig()).registrationOpen) throw new ApiError(403, 'REGISTRATION_CLOSED', 'التسجيل متوقف حاليًا');
  if (b.acceptTerms === false) throw bad('يجب الموافقة على الشروط', { acceptTerms: 'مطلوب' });
  if (String(b.password).length < 8) throw bad('كلمة المرور قصيرة', { password: '8 أحرف على الأقل' });
  const email = normEmail(b.email), phone = normPhone(b.phone);
  if (!/^\S+@\S+\.\S+$/.test(email)) throw bad('البريد غير صحيح', { email: 'صيغة غير صحيحة' });
  if (!/^01[0125]\d{8}$/.test(phone)) throw bad('رقم الموبايل غير صحيح', { phone: 'رقم مصري من 11 رقمًا' });
  const fields: Record<string, string> = {};
  if (await get(`userIndex/email/${emailKey(email)}`)) fields.email = 'مسجل من قبل';
  if (await get(`userIndex/phone/${phone}`)) fields.phone = 'مسجل من قبل';
  if (Object.keys(fields).length) throw new ApiError(409, 'ALREADY_EXISTS', 'الحساب موجود بالفعل', fields);
  const uid = newId(), t = now();
  // pending office invitation → join that office instead of creating one
  const invites = (await get<Record<string, any>>('officeInvitations')) || {};
  const inv = Object.entries(invites).find(([, i]) => i.email === email && i.status === 'PENDING');
  const officeId = inv ? inv[1].officeId : newId(), role = inv ? inv[1].role : 'OWNER';
  const upd: Record<string, unknown> = {
    [`users/${uid}`]: { name: str(b.name, 120), email, phone, governorate: str(b.governorate, 40) || null, status: 'ACTIVE',
      passwordHash: await bcrypt.hash(String(b.password), 10), officeId, officeRole: role, createdAt: t,
      professional: { syndicateNumber: str(b.syndicateNumber, 30) || null, degree: str(b.degree, 30) || null, specialization: str(b.specialization, 80) || null } },
    [`userIndex/email/${emailKey(email)}`]: uid, [`userIndex/phone/${phone}`]: uid,
    [`offices/${officeId}/members/${uid}`]: { role, joinedAt: t },
  };
  if (inv) upd[`officeInvitations/${inv[0]}/status`] = 'ACCEPTED';
  else { upd[`offices/${officeId}/name`] = `مكتب ${str(b.name, 100)}`; upd[`offices/${officeId}/ownerUid`] = uid; upd[`offices/${officeId}/createdAt`] = t; }
  await db.ref().update(upd);
  await initWallet(uid);
  return { ...(await issueTokens(uid, b.deviceId)), user: await userView(uid) };
}));

r.post('/login', wrap(async (req) => {
  const b = req.body || {};
  required(b, ['identifier', 'password']);
  const uid = await findUid(String(b.identifier).trim());
  const u = uid ? await get<any>(`users/${uid}`) : null;
  if (!u || !(await bcrypt.compare(String(b.password), u.passwordHash || ''))) throw new ApiError(401, 'INVALID_CREDENTIALS', 'بيانات الدخول غير صحيحة');
  if (u.status === 'SUSPENDED') throw new ApiError(403, 'ACCOUNT_SUSPENDED', 'الحساب موقوف، تواصل مع الدعم');
  await db.ref(`users/${uid}/lastLoginAt`).set(now());
  return { ...(await issueTokens(uid!, b.deviceId)), user: await userView(uid!) };
}));
r.post('/refresh', wrap(async (req) => { const { uid, tokens } = await rotateRefresh(req.body?.refreshToken, req.body?.deviceId); return { ...tokens, user: await userView(uid) }; }));
r.post('/logout', wrap(async (req) => { await revokeRefresh(req.body?.refreshToken); return ok(); }));

r.post('/forgot-password', wrap(async (req) => {
  required(req.body, ['identifier']);
  const uid = await findUid(String(req.body.identifier).trim());
  if (uid) {
    const u = await get<any>(`users/${uid}`), code = String(crypto.randomInt(100000, 999999));
    await set(`passwordResets/${uid}`, { hash: sha256(code), attempts: 0, expiresAt: new Date(Date.now() + 15 * 60e3).toISOString() });
    await sendMail(u.email, 'رمز استعادة كلمة المرور', `رمز الاستعادة: ${code}\nصالح لمدة 15 دقيقة.`);
  }
  return ok('إن كان الحساب موجودًا فستصلك رسالة برمز الاستعادة');
}));
r.post('/reset-password', wrap(async (req) => {
  const b = req.body || {};
  required(b, ['identifier', 'code', 'password']);
  if (String(b.password).length < 8) throw bad('كلمة المرور قصيرة', { password: '8 أحرف على الأقل' });
  const uid = await findUid(String(b.identifier).trim()), rec = uid ? await get<any>(`passwordResets/${uid}`) : null;
  if (!rec || rec.expiresAt < now() || rec.attempts >= 5 || rec.hash !== sha256(String(b.code))) {
    if (rec) await db.ref(`passwordResets/${uid}/attempts`).set((rec.attempts || 0) + 1);
    throw bad('الرمز غير صحيح أو منتهي', { code: 'غير صحيح' });
  }
  await db.ref().update({ [`users/${uid}/passwordHash`]: await bcrypt.hash(String(b.password), 10), [`passwordResets/${uid}`]: null });
  return ok('تم تغيير كلمة المرور');
}));
export default r;
