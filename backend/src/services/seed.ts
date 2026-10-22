/** First start: creates/updates the platform owner account from ADMIN_EMAIL / ADMIN_PASSWORD (SUPER admin, free usage, unlimited plan). */
import bcrypt from 'bcryptjs';
import { db, get, newId, now } from '../db';
import { emailKey, normEmail, normPhone } from './users';
import { initWallet } from './wallet';

export async function seedOwner() {
  const email = normEmail(process.env.ADMIN_EMAIL), password = process.env.ADMIN_PASSWORD;
  if (!email || !password) return;
  let uid = await get<string>(`userIndex/email/${emailKey(email)}`);
  const t = now();
  if (!uid) {
    uid = newId();
    const officeId = newId(), phone = normPhone(process.env.ADMIN_PHONE || '01000000000');
    await db.ref().update({
      [`users/${uid}`]: { name: process.env.ADMIN_NAME || 'مدير المنصة', email, phone, status: 'ACTIVE', passwordHash: await bcrypt.hash(password, 10),
        officeId, officeRole: 'OWNER', createdAt: t, professional: { degree: 'NAQD', specialization: 'إدارة المنصة' } },
      [`userIndex/email/${emailKey(email)}`]: uid, [`userIndex/phone/${phone}`]: uid,
      [`offices/${officeId}`]: { name: 'مكتب إدارة المنصة', ownerUid: uid, createdAt: t, members: { [uid]: { role: 'OWNER', joinedAt: t } } },
    });
    await initWallet(uid);
    console.log(`[seed] owner account created: ${email}`);
  } else if (process.env.ADMIN_RESET_PASSWORD === 'true') {
    await db.ref(`users/${uid}/passwordHash`).set(await bcrypt.hash(password, 10));
  }
  await db.ref().update({
    [`admins/${uid}`]: { role: 'SUPER', freeUsage: true, grantedBy: 'seed', at: t },
    [`users/${uid}/status`]: 'ACTIVE',
    [`subscriptions/${uid}`]: { planId: 'owner', planName: 'مالك المنصة — غير محدود', status: 'ACTIVE', startAt: t, endAt: '2099-12-31T23:59:59Z',
      limits: { cases: 1000000, documents: 1000000, teamMembers: 1000, storageMb: 1000000, aiDailyRequests: 1000000 } },
  });
}
