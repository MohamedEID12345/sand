/**
 * File storage — Supabase Storage (private bucket "sand-files"). Same small API the routes use:
 * bucket().file(key).save/exists/delete/download/getSignedUrl. Download links are Supabase signed URLs (short-lived).
 * FILES_MODE=memory (or DB_MODE=memory) keeps files in memory and serves them via GET /files/<key> (tests).
 */
import crypto from 'node:crypto';
import { supabase, STORAGE_BUCKET } from './supabase';

const secret = () => process.env.FILES_SECRET || process.env.JWT_SECRET || 'dev-only-files';
export const sign = (key: string, exp: number, name: string) => crypto.createHmac('sha256', secret()).update(`${key}|${exp}|${name}`).digest('base64url');
const okKey = (key: string) => { if (!key || key.includes('..') || key.startsWith('/')) throw new Error('bad key'); return key; };
/** Supabase object names allow ASCII only → non-ASCII segments (Arabic file names) are base64url-encoded. */
const objectName = (key: string) => key.split('/').map((s) => (/^[A-Za-z0-9._-]+$/.test(s) ? s : 'b64_' + Buffer.from(s).toString('base64url'))).join('/');

const mem = new Map<string, { buf: Buffer; contentType: string }>();
const memory = () => (process.env.FILES_MODE || (process.env.DB_MODE === 'memory' ? 'memory' : 'supabase')) === 'memory';
const store = () => supabase().storage.from(STORAGE_BUCKET());
const fail = (what: string, e: any) => { throw new Error(`storage ${what}: ${e?.message || e}`); };

class StoredFile {
  private name: string;
  constructor(private key: string) { okKey(key); this.name = objectName(key); }
  async save(buf: Buffer, opts?: { contentType?: string }) {
    const contentType = opts?.contentType || 'application/octet-stream';
    if (memory()) { mem.set(this.key, { buf: Buffer.from(buf), contentType }); return; }
    const { error } = await store().upload(this.name, buf, { contentType, upsert: true });
    if (error) fail('upload', error);
  }
  async exists() {
    if (memory()) return mem.has(this.key);
    const { data, error } = await store().exists(this.name);
    // Supabase answers a missing object with 400/404 ("Bad Request" / "Object not found")
    if (error) { const st = Number((error as any).status || (error as any).statusCode || 0); if ([400, 404].includes(st) || /not found|bad request|400|404/i.test(String((error as any).message))) return false; fail('exists', error); }
    return !!data;
  }
  async delete(_o?: { ignoreNotFound?: boolean }) {
    if (memory()) { mem.delete(this.key); return; }
    const { error } = await store().remove([this.name]);
    if (error) fail('delete', error);
  }
  async download(): Promise<[Buffer]> {
    if (memory()) { const f = mem.get(this.key); if (!f) throw Object.assign(new Error('not found'), { code: 404 }); return [f.buf]; }
    const { data, error } = await store().download(this.name);
    if (error || !data) fail('download', error || 'empty');
    return [Buffer.from(await data!.arrayBuffer())];
  }
  async contentType() { return memory() ? mem.get(this.key)?.contentType || 'application/octet-stream' : 'application/octet-stream'; }
  async getSignedUrl(o: { expires: number; responseDisposition?: string; action?: string }): Promise<[string]> {
    const enc = o.responseDisposition?.match(/filename\*=UTF-8''(.+)$/)?.[1];
    const name = enc ? decodeURIComponent(enc) : this.key.split('/').pop() || 'file';
    if (!memory()) {
      const secs = Math.max(60, Math.ceil((o.expires - Date.now()) / 1000));
      const { data, error } = await store().createSignedUrl(this.name, secs, { download: name });
      if (error || !data) fail('signed url', error || 'empty');
      return [data!.signedUrl];
    }
    const base = (process.env.PUBLIC_BASE_URL || '').replace(/\/$/, '');
    return [`${base}/files/${encodeURIComponent(this.key)}?exp=${o.expires}&n=${encodeURIComponent(name)}&sig=${sign(this.key, o.expires, name)}`];
  }
}
export const bucket = () => ({ file: (key: string) => new StoredFile(key) });

/** GET /files/:key?exp&n&sig — only used in memory mode (tests); production links point to Supabase directly. */
export async function serveFile(req: any, res: any) {
  const key = decodeURIComponent(req.params[0] || req.params.key || ''), exp = Number(req.query.exp), name = String(req.query.n || '');
  if (!exp || exp < Date.now() || sign(key, exp, name) !== req.query.sig) return res.status(403).json({ error: { code: 'LINK_EXPIRED', message: 'انتهت صلاحية الرابط' } });
  try {
    const f = bucket().file(key);
    if (!(await f.exists())) return res.status(404).json({ error: { code: 'NOT_FOUND', message: 'الملف غير موجود' } });
    const [buf] = await f.download();
    res.setHeader('Content-Type', await f.contentType());
    res.setHeader('Content-Disposition', `attachment; filename*=UTF-8''${encodeURIComponent(name)}`);
    res.end(buf);
  } catch (e: any) {
    if (e?.message === 'bad key') return res.status(400).end();
    console.error('[files]', e?.message);
    res.status(500).json({ error: { code: 'INTERNAL', message: 'تعذر تحميل الملف' } });
  }
}
