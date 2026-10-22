import { get } from '../db';
import { bad } from '../http';

let cache: { at: number; all: Record<string, any> } = { at: 0, all: {} };
export function invalidateTemplates() { cache.at = 0; }
export async function allTemplates() {
  if (Date.now() - cache.at > 60_000) cache = { at: Date.now(), all: (await get<Record<string, any>>('contractTemplates')) || {} };
  return cache.all;
}
export const publicTemplate = (id: string, t: any) => ({ id, name: t.name, category: t.category, categoryLabel: t.categoryLabel,
  description: t.description, credits: t.credits ?? 1, fields: t.fields || [] });

/** Validates values and fills {{key}} / {{block:key}}; empty optional fields print their default (dots). */
export function fill(t: any, values: Record<string, any>) {
  const errors: Record<string, string> = {}, fields: any[] = t.fields || [];
  const shown = (f: any) => !f.showIf || (f.showIf.in || []).includes(String(values[f.showIf.field] ?? ''));
  const empty = (v: any) => v === undefined || v === null || String(v).trim() === '' || (Array.isArray(v) && !v.length);
  for (const f of fields) {
    const v = values[f.key];
    if (f.required && shown(f) && empty(v)) errors[f.key] = 'مطلوب';
    if (empty(v)) continue;
    if (f.type === 'national_id' && !/^\d{14}$/.test(String(v))) errors[f.key] = 'الرقم القومي 14 رقمًا';
    if (f.type === 'phone' && !/^01[0125]\d{8}$/.test(String(v))) errors[f.key] = 'رقم موبايل مصري غير صحيح';
    if (f.maxLength && String(v).length > f.maxLength) errors[f.key] = `الحد الأقصى ${f.maxLength} حرف`;
  }
  if (Object.keys(errors).length) throw bad('راجع البيانات المدخلة', errors);
  const render = (key: string) => {
    const f = fields.find((x) => x.key === key);
    let v = values[key];
    if (empty(v)) return f?.defaultValue ?? '............';
    if (f?.options && (f.type === 'select' || f.type === 'multiselect'))
      v = (Array.isArray(v) ? v : String(v).split(',')).map((x: string) => f.options.find((o: any) => o.value === String(x).trim())?.label || x).join('، ');
    if (f?.type === 'money' && !isNaN(Number(v))) v = `${Number(v).toLocaleString('ar-EG')} جنيه`;
    return String(v);
  };
  let body: string = t.body || fields.map((f) => `${f.label}: {{${f.key}}}`).join('\n');
  body = body.replace(/\{\{block:(\w+)\}\}/g, (_m, k) => t.blocks?.[k]?.[String(values[k] ?? '')] || '');
  return body.replace(/\{\{(\w+)\}\}/g, (_m, k) => render(k));
}
