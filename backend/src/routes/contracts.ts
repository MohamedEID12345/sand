import { Router } from 'express';
import { Document, Packer, Paragraph, TextRun, AlignmentType } from 'docx';
import { db, get, listPage, newId, now, bucket } from '../db';
import { notFound, required, str, wrap } from '../http';
import { allTemplates, fill, publicTemplate } from '../services/templates';
import { assertCanAfford, charge, getWallet } from '../services/wallet';
import { generate } from '../services/gemini';
import { retrieve } from '../services/library';
import { sourcesBlock, withCitations } from '../services/assistant';

const r = Router();
const DOCX = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
const pub = (id: string, c: any) => { const { values, ...rest } = c; return { ...rest, id }; };
const OFFICE = 'OFFICE', OFFICE_LABEL = 'نماذج مكتبي';
/** Platform templates + this office's private templates (ids prefixed "o_"). */
async function templatesFor(req: any): Promise<Record<string, any>> {
  const own = (await get<Record<string, any>>(`officeTemplates/${req.user.officeId}`)) || {};
  const out: Record<string, any> = { ...(await allTemplates()) };
  for (const [id, t] of Object.entries<any>(own)) out[`o_${id}`] = { ...t, category: OFFICE, categoryLabel: t.categoryLabel || OFFICE_LABEL, sort: -1 };
  return out;
}

r.get('/contract-templates/categories', wrap(async (req) => {
  const cats = new Map<string, string>();
  for (const t of Object.values<any>(await templatesFor(req))) if (t.active !== false) cats.set(t.category, t.category === OFFICE ? OFFICE_LABEL : t.categoryLabel);
  return { items: [...cats].map(([value, label]) => ({ value, label })), nextCursor: null };
}));
r.get('/contract-templates', wrap(async (req) => {
  const q = String(req.query.q || '').trim(), cat = req.query.category as string;
  const rows = Object.entries<any>(await templatesFor(req)).filter(([, t]) => t.active !== false && (!cat || t.category === cat) && (!q || `${t.name} ${t.description}`.includes(q)))
    .sort((a, b) => (a[1].sort || 0) - (b[1].sort || 0));
  const start = Number(req.query.cursor || 0);
  return { items: rows.slice(start, start + 30).map(([id, t]) => ({ ...publicTemplate(id, t), fields: [] })), nextCursor: start + 30 < rows.length ? String(start + 30) : null, total: rows.length };
}));
r.get('/contract-templates/:id', wrap(async (req) => {
  const t = (await templatesFor(req))[req.params.id];
  if (!t || t.active === false) throw notFound('النموذج');
  return publicTemplate(req.params.id, t);
}));
r.post('/contracts/generate', wrap(async (req) => {
  required(req.body, ['templateId']);
  const uid = req.user!.uid, t = (await templatesFor(req))[req.body.templateId];
  if (!t || t.active === false) throw notFound('النموذج');
  const body = fill(t, req.body.values || {});
  await assertCanAfford(uid, 'CONTRACT_GENERATE');
  const src = await retrieve(`${t.name} ${(t.legalBasis || []).join(' ')}`, 8), instructions = str(req.body.instructions, 2000);
  const raw = await generate({ slot: 'fast', json: true,
    system: 'أنت مراجع عقود ومحررات قانونية مصري. لا تعدّل النص المعبأ؛ أرجع ملاحظات مراجعة فقط. استشهد بالمصادر بصيغة [[Sn]] ولا تذكر مواد غير موجودة فيها.',
    prompt: `${t.draftingInstructions || ''}\n${instructions ? `تعليمات المستخدم: ${instructions}\n` : ''}أرجع JSON بالشكل {"reviewNotes": ["..."]} (من 3 إلى 8 ملاحظات).\n\nالنص:\n${body}\n\nالمصادر:\n${sourcesBlock(src)}` })
    .catch(() => '{"reviewNotes":[]}');
  let notes: string[] = [];
  try { notes = JSON.parse(raw.replace(/```json|```/g, '')).reviewNotes || []; } catch { notes = []; }
  const cited = notes.map((n) => withCitations(String(n), src));
  const citations = Object.values(Object.fromEntries(cited.flatMap((c) => c.citations).map((c) => [c.sourceId, c])));
  const ch = await charge(uid, 'CONTRACT_GENERATE', t.name), id = newId();
  let caseTitle = null;
  if (req.body.caseId) { const c = await get<any>(`officeData/${req.user!.officeId}/cases/${req.body.caseId}`); caseTitle = c ? `${c.caseNumber || ''} ${c.opponent || ''}`.trim() : null; }
  const contract = { title: t.name, templateId: req.body.templateId, templateName: t.name, body, status: 'DRAFT', reviewNotes: cited.map((c) => c.content),
    caseId: req.body.caseId || null, caseTitle, citations, createdAt: now(), values: req.body.values || {} };
  await db.ref(`contracts/${uid}/${id}`).set(contract);
  return { contract: pub(id, contract), wallet: await getWallet(uid), charge: ch };
}));
r.get('/contracts', wrap(async (req) => {
  const p = await listPage<any>(`contracts/${req.user!.uid}`, { cursor: req.query.cursor as string });
  return { ...p, items: p.items.map((c: any) => pub(c.id, c)) };
}));
r.get('/contracts/:id', wrap(async (req) => {
  const c = await get<any>(`contracts/${req.user!.uid}/${req.params.id}`);
  if (!c) throw notFound('العقد');
  return pub(req.params.id, c);
}));
r.patch('/contracts/:id', wrap(async (req) => {
  const path = `contracts/${req.user!.uid}/${req.params.id}`;
  if (!(await get(path))) throw notFound('العقد');
  const upd: any = { updatedAt: now() };
  for (const k of ['title', 'body', 'status', 'caseId']) if (req.body?.[k] !== undefined) upd[k] = str(req.body[k], 200000);
  await db.ref(path).update(upd);
  return pub(req.params.id, await get(path));
}));
r.get('/contracts/:id/export', wrap(async (req) => {
  const c = await get<any>(`contracts/${req.user!.uid}/${req.params.id}`);
  if (!c) throw notFound('العقد');
  const doc = new Document({ sections: [{ properties: {}, children: [
    new Paragraph({ alignment: AlignmentType.CENTER, bidirectional: true, children: [new TextRun({ text: c.title, bold: true, size: 32, rightToLeft: true })] }),
    ...String(c.body).split('\n').map((line) => new Paragraph({ bidirectional: true, alignment: AlignmentType.RIGHT, children: [new TextRun({ text: line, size: 26, rightToLeft: true })] })),
  ] }] });
  const buf = await Packer.toBuffer(doc), fileName = `${c.title}.docx`;
  const file = bucket().file(`exports/${req.user!.uid}/${req.params.id}.docx`);
  await file.save(buf, { contentType: DOCX });
  const [url] = await file.getSignedUrl({ action: 'read', expires: Date.now() + 15 * 60e3, responseDisposition: `attachment; filename*=UTF-8''${encodeURIComponent(fileName)}` });
  return { url, fileName, mimeType: DOCX };
}));
export default r;
