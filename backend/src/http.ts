import { NextFunction, Request, Response } from 'express';

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public fields?: Record<string, string>) { super(message); }
}
export const bad = (message: string, fields?: Record<string, string>) => new ApiError(400, 'VALIDATION_ERROR', message, fields);
export const notFound = (what = 'العنصر') => new ApiError(404, 'NOT_FOUND', `${what} غير موجود`);
export const forbidden = (m = 'غير مسموح') => new ApiError(403, 'FORBIDDEN', m);
export const unauthorized = (m = 'يلزم تسجيل الدخول') => new ApiError(401, 'UNAUTHORIZED', m);
export const noCredits = () => new ApiError(402, 'INSUFFICIENT_CREDITS', 'رصيدك غير كافٍ لهذه العملية');

export const wrap = (fn: (req: Request, res: Response) => Promise<unknown>) =>
  (req: Request, res: Response, next: NextFunction) => fn(req, res).then((r) => { if (!res.headersSent) res.json(r ?? { ok: true }); }).catch(next);

export function errorHandler(err: any, _req: Request, res: Response, _next: NextFunction) {
  if (err instanceof ApiError) {
    return res.status(err.status).json({ error: { code: err.code, message: err.message, details: err.fields ? { fields: err.fields } : undefined } });
  }
  if (err?.type === 'entity.too.large') return res.status(413).json({ error: { code: 'TOO_LARGE', message: 'حجم الطلب كبير' } });
  console.error(err);
  return res.status(500).json({ error: { code: 'INTERNAL', message: 'حدث خطأ غير متوقع، حاول مرة أخرى' } });
}
export function required(body: any, keys: string[]) {
  const fields: Record<string, string> = {};
  for (const k of keys) if (body?.[k] === undefined || body?.[k] === null || String(body[k]).trim() === '') fields[k] = 'مطلوب';
  if (Object.keys(fields).length) throw bad('بيانات ناقصة', fields);
}
export const str = (v: unknown, max = 2000) => (v === undefined || v === null ? undefined : String(v).trim().slice(0, max));
export const ok = (message?: string) => ({ ok: true, message });
