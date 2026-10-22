import { Router } from 'express';
import crypto from 'node:crypto';
import { db, get, listPage, newId, now } from '../db';
import { ApiError, bad, notFound, ok, required, str, wrap } from '../http';
import { env } from '../env';
import { addCredits } from '../services/wallet';

const r = Router();
export const webhooks = Router();
const sha256 = (s: string) => crypto.createHash('sha256').update(s, 'utf8').digest('hex');
const money = (n: number) => Number(n).toFixed(2);

r.get('/plans', wrap(async (req) => {
  const plans = (await get<Record<string, any>>('catalog/plans')) || {}, sub = await get<any>(`subscriptions/${req.user!.uid}`);
  return { items: Object.entries<any>(plans).filter(([, p]) => p.active !== false).sort((a, b) => (a[1].sort || 0) - (b[1].sort || 0))
    .map(([id, p]) => ({ id, ...p, current: sub?.planId === id && sub?.status === 'ACTIVE' })), nextCursor: null };
}));
r.get('/credit-packs', wrap(async () => ({ items: Object.entries<any>((await get('catalog/creditPacks')) || {}).filter(([, p]) => p.active !== false)
  .sort((a, b) => (a[1].sort || 0) - (b[1].sort || 0)).map(([id, p]) => ({ id, ...p })), nextCursor: null })));
r.get('/payment-methods', wrap(async () => {
  const cfg = (await get<any>('config/payments')) || {};
  return { items: [
    { code: 'FAWRY_CARD', label: 'بطاقة بنكية', description: 'فيزا / ماستركارد / ميزة عبر فوري', provider: 'FAWRY', type: 'REDIRECT', icon: 'card' },
    { code: 'FAWRY_WALLET', label: 'محفظة إلكترونية', description: 'المحافظ الإلكترونية عبر فوري', provider: 'FAWRY', type: 'REDIRECT', icon: 'wallet' },
    { code: 'FAWRY_REF', label: 'كود فوري', description: 'ادفع في أي منفذ فوري بالرقم المرجعي', provider: 'FAWRY', type: 'REFERENCE', icon: 'store' },
    ...(cfg.bank ? [{ code: 'BANK_TRANSFER', label: 'تحويل بنكي', description: 'يُراجع التحويل يدويًا', provider: 'BANK', type: 'BANK', icon: 'bank' }] : []),
  ].filter((m) => cfg.disabled?.[m.code] !== true), nextCursor: null };
}));
async function itemOf(type: string, id: string) {
  const it = await get<any>(`catalog/${type === 'PLAN' ? 'plans' : 'creditPacks'}/${id}`);
  if (!it || it.active === false) throw notFound('العنصر');
  return it;
}
async function promo(code: string | undefined, amount: number) {
  if (!code) return null;
  const p = await get<any>(`catalog/promoCodes/${String(code).trim().toUpperCase()}`);
  if (!p || p.active === false || (p.expiresAt && p.expiresAt < now()) || (p.maxUses && (p.uses || 0) >= p.maxUses)) return { valid: false, code, message: 'الكود غير صالح أو منتهي' };
  const discount = p.discountType === 'PERCENT' ? Math.round(amount * p.discountValue) / 100 : Math.min(amount, p.discountValue || 0);
  return { valid: true, code: String(code).toUpperCase(), message: 'تم تطبيق الكود', discountType: p.discountType, discountValue: p.discountValue, bonusCredits: p.bonusCredits || 0,
    originalAmount: amount, discountAmount: discount, finalAmount: Math.max(0, amount - discount), currency: 'EGP' };
}
r.post('/promo-codes/validate', wrap(async (req) => {
  required(req.body, ['code', 'itemType', 'itemId']);
  return promo(req.body.code, (await itemOf(req.body.itemType, req.body.itemId)).price);
}));

/** FawryPay Express Checkout. Signature (per Fawry integration guide — verify against the current version before go-live):
 * SHA256(merchantCode + merchantRefNum + customerProfileId + returnUrl + Σ(itemId + quantity + price.2dp) + secureKey) */
function fawryCheckout(order: any, user: any) {
  const items = [{ itemId: order.itemId, description: order.itemName, price: money(order.total), quantity: 1 }];
  const signature = sha256(env.fawry.merchantCode + order.id + order.uid + env.fawry.returnUrl + items.map((i) => i.itemId + i.quantity + i.price).join('') + env.fawry.secureKey);
  return { merchantCode: env.fawry.merchantCode, merchantRefNum: order.id, customerProfileId: order.uid, customerName: user.name, customerMobile: user.phone,
    customerEmail: user.email, language: 'ar-eg', chargeItems: items, returnUrl: env.fawry.returnUrl,
    paymentMethod: order.paymentMethod === 'FAWRY_REF' ? 'PayAtFawry' : order.paymentMethod === 'FAWRY_WALLET' ? 'MWALLET' : 'CARD', authCaptureModePayment: false, signature };
}
export const orderView = (id: string, o: any) => ({ id, itemType: o.itemType, itemId: o.itemId, itemName: o.itemName, status: o.status, amount: o.amount, discount: o.discount,
  total: o.total, currency: o.currency, paymentMethod: o.paymentMethod, provider: o.provider, referenceNumber: o.fawryRef || id, expiresAt: o.expiresAt, createdAt: o.createdAt,
  paidAt: o.paidAt || null, failureReason: o.failureReason || null, creditsGranted: o.creditsGranted || 0 });

r.post('/orders', wrap(async (req) => {
  required(req.body, ['itemType', 'itemId', 'paymentMethod']);
  const uid = req.user!.uid, it = await itemOf(req.body.itemType, req.body.itemId), user = await get<any>(`users/${uid}`);
  const pr = await promo(req.body.promoCode, it.price);
  if (pr && !pr.valid) throw bad(pr.message!, { promoCode: 'غير صالح' });
  const id = newId(), t = now();
  const order = { uid, itemType: req.body.itemType, itemId: req.body.itemId, itemName: it.name, status: 'PENDING', amount: it.price, discount: pr?.discountAmount || 0,
    total: pr?.finalAmount ?? it.price, currency: 'EGP', paymentMethod: req.body.paymentMethod, provider: req.body.paymentMethod === 'BANK_TRANSFER' ? 'BANK' : 'FAWRY',
    promoCode: pr?.valid ? pr.code : null, bonusCredits: pr?.bonusCredits || 0, createdAt: t, expiresAt: new Date(Date.now() + 48 * 3600e3).toISOString() };
  await db.ref(`orders/${id}`).set(order);
  let payment: any;
  if (order.provider === 'BANK') {
    payment = { provider: 'BANK', type: 'BANK', referenceNumber: id, expiresAt: order.expiresAt, checkoutUrl: null,
      instructions: 'حوّل المبلغ ثم أرسل إثبات التحويل من صفحة الطلب', bankDetails: (await get<any>('config/payments/bank')) || null };
  } else {
    const resp = await fetch(`${env.fawry.baseUrl}/fawrypay-api/api/payments/init`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fawryCheckout({ ...order, id }, user)) });
    const text = (await resp.text()).replace(/"/g, '');
    if (!resp.ok || !/^https?:/.test(text)) throw new ApiError(502, 'PAYMENT_PROVIDER_ERROR', 'تعذر إنشاء عملية الدفع، حاول مرة أخرى');
    payment = { provider: 'FAWRY', type: order.paymentMethod === 'FAWRY_REF' ? 'REFERENCE' : 'REDIRECT', checkoutUrl: text, referenceNumber: id, expiresAt: order.expiresAt, instructions: null, bankDetails: null };
  }
  return { order: orderView(id, order), payment };
}));
async function myOrder(uid: string, id: string) {
  const o = await get<any>(`orders/${id}`);
  if (!o || o.uid !== uid) throw notFound('الطلب');
  return o;
}
r.get('/orders/:id', wrap(async (req) => orderView(req.params.id, await myOrder(req.user!.uid, req.params.id))));
/** Client-triggered status check after returning from checkout. Fulfilment only happens on a status confirmed by Fawry. */
r.post('/orders/:id/verify', wrap(async (req) => {
  const o = await myOrder(req.user!.uid, req.params.id);
  if (o.status === 'PENDING' && o.provider === 'FAWRY' && env.fawry.merchantCode) {
    const sig = sha256(env.fawry.merchantCode + req.params.id + env.fawry.secureKey);
    const resp = await fetch(`${env.fawry.baseUrl}/ECommerceWeb/Fawry/payments/status/v2?merchantCode=${env.fawry.merchantCode}&merchantRefNumber=${req.params.id}&signature=${sig}`);
    if (resp.ok) { const s: any = await resp.json(); await applyStatus(req.params.id, s.orderStatus, s.fawryRefNumber, Number(s.paymentAmount)); }
  }
  return orderView(req.params.id, await get(`orders/${req.params.id}`));
}));
r.post('/orders/:id/bank-proof', wrap(async (req) => {
  await myOrder(req.user!.uid, req.params.id);
  await db.ref(`orders/${req.params.id}`).update({ bankProofNote: str(req.body?.note, 500) || null, status: 'UNDER_REVIEW', updatedAt: now() });
  return ok('تم استلام إثبات التحويل وسيُراجع');
}));

/** Idempotent fulfilment: credits/subscription are granted once per order, guarded by a transaction on the order status. */
export async function applyStatus(orderId: string, providerStatus: string, fawryRef?: string, paidAmount?: number) {
  const map: Record<string, string> = { PAID: 'PAID', EXPIRED: 'EXPIRED', CANCELED: 'CANCELLED', CANCELLED: 'CANCELLED', REFUNDED: 'REFUNDED', FAILED: 'FAILED', UNPAID: 'PENDING', NEW: 'PENDING' };
  const next = map[String(providerStatus).toUpperCase()] || 'PENDING';
  let fulfil = false;
  const res = await db.ref(`orders/${orderId}`).transaction((o: any) => {
    if (!o) return o;
    fulfil = false;
    if (o.status === 'PAID' || o.status === next) return;
    if (next === 'PAID' && paidAmount !== undefined && !isNaN(paidAmount) && paidAmount + 0.009 < Number(o.total)) { o.status = 'FAILED'; o.failureReason = 'AMOUNT_MISMATCH'; return o; }
    o.status = next; o.fawryRef = fawryRef || o.fawryRef || null; o.updatedAt = now();
    if (next === 'PAID') { o.paidAt = now(); fulfil = true; }
    return o;
  });
  if (!res.committed || !fulfil) return;
  const o = res.snapshot.val();
  const it = await get<any>(`catalog/${o.itemType === 'PLAN' ? 'plans' : 'creditPacks'}/${o.itemId}`);
  const credits = (it?.credits || 0) + (it?.bonusCredits || 0) + (o.bonusCredits || 0);
  if (credits) await addCredits(o.uid, credits, 'purchasedCredits', 'ORDER', `${o.itemName} — طلب ${orderId}`);
  if (o.itemType === 'PLAN') {
    const cur = await get<any>(`subscriptions/${o.uid}`);
    const start = cur?.status === 'ACTIVE' && cur.planId === o.itemId && cur.endAt > now() ? new Date(cur.endAt) : new Date();
    const end = new Date(start.getTime() + (it?.durationDays || 30) * 864e5);
    await db.ref(`subscriptions/${o.uid}`).set({ planId: o.itemId, planName: o.itemName, status: 'ACTIVE', startAt: new Date().toISOString(), endAt: end.toISOString(), limits: it?.limits || null });
    if (it?.freeServiceUses) await db.ref(`wallets/${o.uid}/freeServiceUses`).transaction((v) => (v || 0) + it.freeServiceUses);
  }
  if (o.promoCode) await db.ref(`catalog/promoCodes/${o.promoCode}/uses`).transaction((v) => (v || 0) + 1);
  await db.ref().update({
    [`orders/${orderId}/creditsGranted`]: credits,
    [`invoices/${o.uid}/${newId()}`]: { number: `LG-${new Date().getFullYear()}-${orderId.slice(-6).toUpperCase()}`, orderId, itemName: o.itemName, amount: o.amount, total: o.total,
      currency: o.currency, paymentMethod: o.paymentMethod, transactionId: o.fawryRef || orderId, status: 'PAID', issuedAt: now(), createdAt: now() },
    [`notifications/${o.uid}/${newId()}`]: { type: 'PAYMENT', title: 'تم الدفع بنجاح', body: `تم تفعيل ${o.itemName}`, createdAt: now(), refType: 'ORDER', refId: orderId },
    [`stats/revenue/${now().slice(0, 7)}/${orderId}`]: Number(o.total),
  });
}
r.get('/invoices', wrap(async (req) => listPage(`invoices/${req.user!.uid}`, { cursor: req.query.cursor as string })));
r.get('/invoices/:id/pdf', wrap(async (req) => {
  const inv = await get<any>(`invoices/${req.user!.uid}/${req.params.id}`);
  if (!inv) throw notFound('الفاتورة');
  const html = `<!doctype html><html dir="rtl" lang="ar"><meta charset="utf-8"><body style="font-family:sans-serif;max-width:640px;margin:auto"><h2>فاتورة ${inv.number}</h2>
<p>${inv.itemName}</p><p>المبلغ: ${inv.amount} — الإجمالي: ${inv.total} ${inv.currency}</p><p>المعاملة: ${inv.transactionId}</p><p>التاريخ: ${String(inv.issuedAt).slice(0, 10)}</p></body></html>`;
  return { url: `data:text/html;charset=utf-8;base64,${Buffer.from(html).toString('base64')}`, fileName: `${inv.number}.html`, mimeType: 'text/html' };
}));

/** Fawry server notification V2. messageSignature = SHA256(fawryRefNumber + merchantRefNumber + paymentAmount.2dp + orderAmount.2dp + orderStatus + paymentMethod + paymentRefrenceNumber + secureKey) */
webhooks.post('/fawry', wrap(async (req) => {
  const b = req.body || {};
  const expected = sha256(`${b.fawryRefNumber || ''}${b.merchantRefNumber || ''}${money(b.paymentAmount || 0)}${money(b.orderAmount || 0)}${b.orderStatus || ''}${b.paymentMethod || ''}${b.paymentRefrenceNumber || ''}${env.fawry.secureKey}`);
  if (!env.fawry.secureKey || !b.messageSignature || b.messageSignature !== expected) throw new ApiError(401, 'BAD_SIGNATURE', 'invalid signature');
  const eventKey = sha256(`${b.fawryRefNumber}|${b.orderStatus}`);
  const first = await db.ref(`payments/webhookEvents/${eventKey}`).transaction((v) => (v ? undefined : { receivedAt: now(), order: b.merchantRefNumber, status: b.orderStatus }));
  if (first.committed) await applyStatus(String(b.merchantRefNumber), String(b.orderStatus), b.fawryRefNumber, Number(b.paymentAmount));
  return ok();
}));
export default r;
