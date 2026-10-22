import { db, get, newId, now } from '../db';
import { noCredits } from '../http';
import { appConfig, aiConfig } from './config';

export function walletView(w: any) {
  w = w || {};
  const purchased = w.purchasedCredits || 0, bonus = w.bonusCredits || 0, free = w.freeCredits || 0, used = w.usedCredits || 0;
  const total = purchased + bonus + free;
  return { totalCredits: total, usedCredits: used, remainingCredits: Math.max(0, total - used), bonusCredits: bonus,
    purchasedCredits: purchased, freeCredits: free, freeServiceUses: w.freeServiceUses || 0 };
}
export const getWallet = async (uid: string) => walletView(await get(`wallets/${uid}`));

export async function initWallet(uid: string) {
  const cfg = await appConfig();
  await db.ref(`wallets/${uid}`).set({ purchasedCredits: 0, bonusCredits: 0, freeCredits: cfg.freeCredits, usedCredits: 0, freeServiceUses: cfg.freeServiceUses, updatedAt: now() });
  if (cfg.freeCredits) await addTx(uid, cfg.freeCredits, 'FREE', 'SIGNUP', 'رصيد مجاني عند التسجيل');
}
export async function operationCost(op: string) {
  const c = (await aiConfig()).operations?.[op] || {};
  return { label: c.label || op, credits: Number(c.credits ?? 1), usesFreeUse: c.usesFreeUse !== false, active: c.active !== false };
}
/** Platform admins (admins/{uid}.freeUsage !== false) use every service for free — for testing and support. */
const freeCache = new Map<string, { at: number; free: boolean }>();
export async function isFreeUser(uid: string) {
  const c = freeCache.get(uid);
  if (c && Date.now() - c.at < 60_000) return c.free;
  const a = await get<any>(`admins/${uid}`);
  const free = !!a && a.freeUsage !== false;
  freeCache.set(uid, { at: Date.now(), free });
  return free;
}
/** 402 when the user cannot afford the operation — checked before calling the model. */
export async function assertCanAfford(uid: string, op: string) {
  const cost = await operationCost(op);
  if (await isFreeUser(uid)) return cost;
  const w = await getWallet(uid);
  if (cost.credits === 0 || (cost.usesFreeUse && w.freeServiceUses > 0) || w.remainingCredits >= cost.credits) return cost;
  throw noCredits();
}
/** Charges after the operation succeeded, atomically (RTDB transaction). Returns the Charge object the app expects. */
export async function charge(uid: string, op: string, description: string) {
  const cost = await operationCost(op);
  if (await isFreeUser(uid)) { await addTx(uid, 0, 'ADMIN_FREE', op, description); return { credits: 0, usedFreeUse: false, operation: op }; }
  let usedFree = false, credits = 0;
  const res = await db.ref(`wallets/${uid}`).transaction((w: any) => {
    if (!w) return w;
    usedFree = false; credits = 0;
    if (cost.credits === 0) return w;
    if (cost.usesFreeUse && (w.freeServiceUses || 0) > 0) { w.freeServiceUses -= 1; usedFree = true; return w; }
    if (walletView(w).remainingCredits < cost.credits) return;
    w.usedCredits = (w.usedCredits || 0) + cost.credits; credits = cost.credits; w.updatedAt = now();
    return w;
  });
  if (!res.committed) throw noCredits();
  if (credits || usedFree) await addTx(uid, -credits, usedFree ? 'FREE_USE' : 'USAGE', op, description);
  return { credits, usedFreeUse: usedFree, operation: op };
}
export async function addCredits(uid: string, amount: number, kind: 'purchasedCredits' | 'bonusCredits', op: string, description: string) {
  await db.ref(`wallets/${uid}/${kind}`).transaction((v) => (v || 0) + amount);
  await addTx(uid, amount, kind === 'purchasedCredits' ? 'PURCHASE' : 'BONUS', op, description);
}
export async function addTx(uid: string, amount: number, type: string, operation: string, description: string) {
  await db.ref(`creditTx/${uid}/${newId()}`).set({ amount, type, operation, description, createdAt: now() });
}
