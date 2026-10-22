import { get } from '../db';
import { ROLE_LABELS, ROLE_PERMISSIONS } from './auth';

export const normEmail = (e?: string) => (e || '').trim().toLowerCase();
export const normPhone = (p?: string) => {
  let d = (p || '').replace(/[٠-٩]/g, (c) => String('٠١٢٣٤٥٦٧٨٩'.indexOf(c))).replace(/\D/g, '');
  if (d.startsWith('20')) d = d.slice(2);
  return d.startsWith('0') ? d : '0' + d;
};
export const emailKey = (e: string) => Buffer.from(normEmail(e)).toString('base64url');

export async function userView(uid: string) {
  const u = await get<any>(`users/${uid}`);
  if (!u) return null;
  const office = u.officeId ? await get<any>(`offices/${u.officeId}`) : null;
  return { id: uid, name: u.name, email: u.email, phone: u.phone, governorate: u.governorate || null, status: u.status || 'ACTIVE',
    officeId: u.officeId, officeName: office?.name || null, officeRole: u.officeRole, officeRoleLabel: ROLE_LABELS[u.officeRole] || null,
    professional: u.professional || null, avatarUrl: u.avatarUrl || null, createdAt: u.createdAt };
}
export const permissionsOf = (role: string) => ROLE_PERMISSIONS[role] || [];
