/** Office-private templates: an office owner/admin adds his own forms (Word upload or field editor); every member of the office
 * sees them in the contracts screen under "نماذج مكتبي" and fills them like the platform templates. */
import { Router, Request } from 'express';
import { uploadSingle } from '../upload';
import { db, get, newId, now } from '../db';
import { bad, notFound, ok, required, str, wrap } from '../http';
import { need } from '../services/auth';
import { docxToTemplate } from '../services/docxTemplate';

const r = Router();
const upload = { single: (field: string) => uploadSingle(field, 10 * 1024 * 1024) };
const base = (req: Request) => `officeTemplates/${req.user!.officeId}`;
const TYPES = new Set(['text', 'textarea', 'date', 'number', 'money', 'select', 'multiselect', 'national_id', 'phone', 'email']);

export function validateTemplate(t: any) {
  required(t, ['name', 'body']);
  if (!Array.isArray(t.fields)) throw bad('fields يجب أن تكون قائمة');
  const keys = new Set<string>();
  for (const f of t.fields) {
    if (!f?.key || !/^\w{1,40}$/.test(f.key)) throw bad(`مفتاح خانة غير صالح: ${f?.key}`);
    if (keys.has(f.key)) throw bad(`مفتاح مكرر: ${f.key}`);
    keys.add(f.key);
    if (!f.label) throw bad(`الخانة ${f.key} بلا عنوان`);
    if (f.type && !TYPES.has(f.type)) throw bad(`نوع غير مدعوم: ${f.type}`);
  }
  const missing = [...String(t.body).matchAll(/\{\{(\w+)\}\}/g)].map((m) => m[1]).filter((k) => !keys.has(k));
  if (missing.length) throw bad(`متغيرات في النص بلا خانات: ${[...new Set(missing)].join('، ')}`);
}
const clean = (t: any) => ({ name: str(t.name, 150), description: str(t.description, 500) || '', categoryLabel: str(t.categoryLabel, 60) || 'نماذج مكتبي',
  credits: Math.max(0, Number(t.credits ?? 1)), active: t.active !== false, body: String(t.body).slice(0, 200000),
  fields: t.fields.map((f: any) => ({ key: f.key, label: str(f.label, 120), type: f.type || 'text', required: !!f.required, group: str(f.group, 60) || null,
    placeholder: str(f.placeholder, 120) || null, hint: str(f.hint, 200) || null, defaultValue: f.defaultValue ?? '............',
    options: Array.isArray(f.options) ? f.options.slice(0, 50).map((o: any) => ({ value: str(o.value, 60), label: str(o.label, 120) })) : null })) });

r.get('/office/templates', wrap(async (req) => ({ items: Object.entries<any>((await get(base(req))) || {}).map(([id, t]) => ({ id, ...t }))
  .sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt))) })));
r.get('/office/templates/:id', wrap(async (req) => { const t = await get<any>(`${base(req)}/${req.params.id}`); if (!t) throw notFound('النموذج'); return { id: req.params.id, ...t }; }));
r.post('/office/templates', need('office.manage'), wrap(async (req) => {
  validateTemplate(req.body);
  const id = newId(), t = { ...clean(req.body), createdBy: req.user!.uid, createdAt: now(), updatedAt: now() };
  await db.ref(`${base(req)}/${id}`).set(t);
  return { id, ...t };
}));
r.post('/office/templates/upload', need('office.manage'), upload.single('file'), wrap(async (req) => {
  const f = (req as any).file as Express.Multer.File | undefined;
  if (!f || !/\.docx$/i.test(f.originalname)) throw bad('ارفع ملف Word بصيغة ‎.docx', { file: 'مطلوب' });
  const conv = await docxToTemplate(f.buffer, f.originalname.replace(/\.docx$/i, ''));
  if (!conv.body) throw bad('الملف فارغ');
  const id = newId(), t = { ...clean({ ...conv, description: `نموذج المكتب — ${conv.fields.length} خانة`, credits: 1 }), autoFields: true, createdBy: req.user!.uid, createdAt: now(), updatedAt: now() };
  await db.ref(`${base(req)}/${id}`).set(t);
  return { id, ...t };
}));
r.put('/office/templates/:id', need('office.manage'), wrap(async (req) => {
  const path = `${base(req)}/${req.params.id}`, old = await get<any>(path);
  if (!old) throw notFound('النموذج');
  validateTemplate(req.body);
  await db.ref(path).set({ ...old, ...clean(req.body), updatedAt: now() });
  return { id: req.params.id, ...(await get<any>(path)) };
}));
r.delete('/office/templates/:id', need('office.manage'), wrap(async (req) => { await db.ref(`${base(req)}/${req.params.id}`).remove(); return ok(); }));
export default r;
