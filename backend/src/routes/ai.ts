import { Router, Request } from 'express';
import { uploadSingle } from '../upload';
import { db, get, listPage, newId, now, bucket } from '../db';
import { ApiError, bad, notFound, ok, required, str, wrap } from '../http';
import { answer, MODES } from '../services/assistant';
import { assertCanAfford, charge, getWallet, operationCost } from '../services/wallet';
import { appConfig, aiConfig } from '../services/config';

const r = Router();
const upload = { single: (field: string) => uploadSingle(field, 20 * 1024 * 1024) };
async function chatOf(uid: string, id: string) {
  const c = await get<any>(`aiChats/${uid}/${id}`);
  if (!c) throw notFound('المحادثة');
  return { ...c, id };
}
r.get('/ai/costs', wrap(async () => {
  const ops = (await aiConfig()).operations || {};
  const items = await Promise.all(Object.keys(ops).map(async (op) => { const c = await operationCost(op); return { operation: op, label: c.label, credits: c.credits, usesFreeService: c.usesFreeUse }; }));
  return { items, nextCursor: null };
}));
r.get('/ai/chats', wrap(async (req) => listPage(`aiChats/${req.user!.uid}`, { cursor: req.query.cursor as string, sortKey: 'updatedAt' })));
r.post('/ai/chats', wrap(async (req) => {
  const mode = String(req.body?.mode || 'QUESTION');
  if (!MODES[mode]) throw bad('وضع غير معروف', { mode: 'غير مدعوم' });
  const id = newId(), t = now();
  let caseTitle = null;
  if (req.body?.caseId) { const c = await get<any>(`officeData/${req.user!.officeId}/cases/${req.body.caseId}`); caseTitle = c ? `${c.caseNumber || ''} ${c.opponent || ''}`.trim() : null; }
  const chat = { title: str(req.body?.title, 120) || 'محادثة جديدة', mode, caseId: req.body?.caseId || null, caseTitle, lastMessage: null, createdAt: t, updatedAt: t };
  await db.ref(`aiChats/${req.user!.uid}/${id}`).set(chat);
  return { ...chat, id };
}));
r.get('/ai/chats/:id', wrap(async (req) => chatOf(req.user!.uid, req.params.id)));
r.get('/ai/chats/:id/messages', wrap(async (req) => {
  await chatOf(req.user!.uid, req.params.id);
  const p = await listPage(`aiMessages/${req.params.id}`, { cursor: req.query.cursor as string, limit: 50 });
  return { ...p, items: p.items.reverse() };
}));
r.delete('/ai/chats/:id', wrap(async (req) => {
  await chatOf(req.user!.uid, req.params.id);
  await db.ref().update({ [`aiChats/${req.user!.uid}/${req.params.id}`]: null, [`aiMessages/${req.params.id}`]: null });
  return ok();
}));

async function run(req: Request, mode: string, content: string, attachmentIds: string[] = [], clientMessageId?: string) {
  const uid = req.user!.uid, chat = await chatOf(uid, req.params.id), cfg = await appConfig();
  if (!cfg.features?.ai || cfg.features?.[`ai.${mode}`] === false) throw new ApiError(403, 'FEATURE_DISABLED', 'هذه الخدمة غير متاحة حاليًا');
  const m = MODES[mode] || MODES.QUESTION;
  await assertCanAfford(uid, m.op);
  if (clientMessageId && (await get(`aiIdem/${uid}/${clientMessageId}`))) throw new ApiError(409, 'DUPLICATE', 'تم إرسال هذه الرسالة من قبل');
  const prev = (await listPage(`aiMessages/${chat.id}`, { limit: 8 })).items.reverse();
  const history = prev.map((x: any) => ({ role: x.role === 'user' ? 'user' as const : 'model' as const, text: x.content }));
  let extra = '';
  for (const aid of attachmentIds) { const a = await get<any>(`aiUploads/${uid}/${aid}`); if (a?.text) extra += `\n[${a.name}]\n${a.text.slice(0, 12000)}`; }
  if (chat.caseId) { const c = await get<any>(`officeData/${req.user!.officeId}/cases/${chat.caseId}`); if (c) extra += `\n[ملف القضية] ${c.caseNumber || ''} — ${c.court || ''} — ${c.description || ''}`.slice(0, 4000); }
  const t = now(), userMsgId = newId();
  const userMessage = { role: 'user', content, citations: [], attachments: attachmentIds.map((id) => ({ id })), createdAt: t, creditsCharged: 0, usedFreeUse: false };
  await db.ref(`aiMessages/${chat.id}/${userMsgId}`).set(userMessage);
  let res;
  try { res = await answer(mode, content, history, extra); }
  catch (e) { await db.ref(`aiMessages/${chat.id}/${userMsgId}`).remove(); throw e; }  // nothing charged, no orphan message
  const ch = await charge(uid, m.op, `${mode}: ${content.slice(0, 60)}`);
  const msgId = newId(), title = chat.title === 'محادثة جديدة' ? content.slice(0, 60) : chat.title;
  const message = { role: 'assistant', content: res.content, citations: res.citations, attachments: [], createdAt: now(), creditsCharged: ch.credits, usedFreeUse: ch.usedFreeUse, fromWeb: !!res.fromWeb };
  await db.ref().update({ [`aiMessages/${chat.id}/${msgId}`]: message, [`aiChats/${uid}/${chat.id}/lastMessage`]: res.content.slice(0, 140),
    [`aiChats/${uid}/${chat.id}/updatedAt`]: message.createdAt, [`aiChats/${uid}/${chat.id}/title`]: title,
    ...(clientMessageId ? { [`aiIdem/${uid}/${clientMessageId}`]: msgId } : {}) });
  await db.ref(`stats/aiUsage/${t.slice(0, 10)}/${m.op}`).transaction((n) => (n || 0) + 1);
  return { userMessage: { ...userMessage, id: userMsgId }, message: { ...message, id: msgId }, wallet: await getWallet(uid), charge: ch,
    chat: { ...chat, title, lastMessage: res.content.slice(0, 140), updatedAt: message.createdAt } };
}
r.post('/ai/chats/:id/messages', wrap(async (req) => {
  required(req.body, ['content']);
  const chat = await chatOf(req.user!.uid, req.params.id);
  return run(req, chat.mode, String(req.body.content).slice(0, 8000), req.body.attachmentIds || [], req.body.clientMessageId);
}));
r.post('/ai/chats/:id/debate-analysis', wrap(async (req) => run(req, 'DEBATE_ANALYSIS', 'حلّل المناظرة السابقة وحدد الطرف الأرجح قانونًا.')));
r.post('/ai/uploads', upload.single('file'), wrap(async (req) => {
  const f = (req as any).file as Express.Multer.File | undefined;
  if (!f) throw bad('لم يتم إرفاق ملف', { file: 'مطلوب' });
  const id = newId(), uid = req.user!.uid;
  const text = /text|json|csv/.test(f.mimetype) ? f.buffer.toString('utf8') : '';
  await bucket().file(`aiUploads/${uid}/${id}/${f.originalname}`).save(f.buffer, { contentType: f.mimetype });
  await db.ref(`aiUploads/${uid}/${id}`).set({ name: f.originalname, mimeType: f.mimetype, sizeBytes: f.size, text: text.slice(0, 200000), createdAt: now() });
  return { id, name: f.originalname, mimeType: f.mimetype, sizeBytes: f.size };
}));
export default r;
