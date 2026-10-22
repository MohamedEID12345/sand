/**
 * multipart/form-data upload (single file) that works both on Cloud Functions — where the body was already read into req.rawBody —
 * and on a normal Node server (streams the request). Same shape as multer: req.file = { buffer, originalname, mimetype, size, fieldname }, req.body = fields.
 */
import Busboy from 'busboy';
import { NextFunction, Request, Response } from 'express';
import { ApiError } from './http';

export function uploadSingle(field: string, maxBytes: number) {
  return (req: Request, res: Response, next: NextFunction) => {
    const ct = String(req.headers['content-type'] || '');
    if (!ct.startsWith('multipart/form-data')) return next();
    let bb: Busboy.Busboy;
    try { bb = Busboy({ headers: req.headers, limits: { fileSize: maxBytes, files: 1, fields: 50 }, defParamCharset: 'utf8' }); }
    catch { return next(new ApiError(400, 'VALIDATION_ERROR', 'طلب رفع غير صالح')); }
    const body: Record<string, string> = {};
    let file: any = null, tooBig = false, done = false;
    const finish = (err?: any) => { if (done) return; done = true; if (err) return next(err); (req as any).body = body; (req as any).file = file || undefined; next(); };
    bb.on('field', (k, v) => { body[k] = v; });
    bb.on('file', (name, stream, info) => {
      const chunks: Buffer[] = [];
      stream.on('data', (d: Buffer) => chunks.push(d));
      stream.on('limit', () => { tooBig = true; });
      stream.on('end', () => {
        if (name !== field || tooBig) return;
        const buffer = Buffer.concat(chunks);
        file = { fieldname: name, originalname: info.filename, mimetype: info.mimeType, encoding: info.encoding, size: buffer.length, buffer };
      });
    });
    bb.on('error', (e) => finish(new ApiError(400, 'VALIDATION_ERROR', 'تعذر قراءة الملف المرفوع: ' + String((e as any)?.message || e))));
    bb.on('close', () => (tooBig ? finish(new ApiError(413, 'TOO_LARGE', `حجم الملف أكبر من ${Math.round(maxBytes / 1048576)} ميجا`)) : finish()));
    const rawBody: Buffer | undefined = (req as any).rawBody;
    if (rawBody) bb.end(rawBody); else req.pipe(bb);
  };
}
