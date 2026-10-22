/**
 * Data access — a path tree (db.ref(path).get/set/update/remove/push/child/transaction + get/set/listPage helpers)
 * stored in Supabase Postgres (supabaseDb.ts → table lg_nodes). Values are cleaned the RTDB way:
 * undefined / NaN dropped, objects with numeric keys that come back as sparse arrays are turned back into objects.
 * DB_MODE=memory runs on a strict in-memory emulation (smoke tests, no Supabase needed).
 */
import { MemoryDb } from './memoryDb';
import { SupabaseDb } from './supabaseDb';

export { bucket } from './files';

/* ---------- value hygiene ---------- */
export function clean(v: any): any {
  if (v === undefined) return undefined;
  if (typeof v === 'number') return Number.isFinite(v) ? v : null;
  if (v === null || typeof v !== 'object') return v;
  if (v instanceof Date) return v.toISOString();
  if (Array.isArray(v)) return v.map((x) => { const c = clean(x); return c === undefined ? null : c; });
  const out: Record<string, any> = {};
  for (const [k, x] of Object.entries(v)) { const c = clean(x); if (c !== undefined) out[k] = c; }
  return out;
}
/** RTDB returns {"1":a,"2":b} as [ , a, b]; turn sparse arrays back into objects (dense arrays stay arrays). */
export function unsparse(v: any): any {
  if (v === null || typeof v !== 'object') return v;
  if (Array.isArray(v)) {
    let holes = false;
    for (let i = 0; i < v.length; i++) if (!(i in v) || v[i] === null || v[i] === undefined) { holes = true; break; }
    if (!holes) return v.map(unsparse);
    const o: Record<string, any> = {};
    v.forEach((x, i) => { if (x !== null && x !== undefined) o[String(i)] = unsparse(x); });
    return o;
  }
  const o: Record<string, any> = {};
  for (const [k, x] of Object.entries(v)) o[k] = unsparse(x);
  return o;
}

/* ---------- backend ---------- */
interface RawRef { key: string | null; get(): Promise<{ exists(): boolean; val(): any }>; set(v: any): Promise<any>; update(v: any): Promise<any>;
  remove(): Promise<any>; push(): RawRef; child(p: string): RawRef; transaction(fn: (c: any) => any): Promise<{ committed: boolean; snapshot: { exists(): boolean; val(): any } }> }
interface RawDb { ref(p?: string): RawRef }

let raw: RawDb | null = null;
function backend(): RawDb {
  if (raw) return raw;
  raw = (process.env.DB_MODE === 'memory' ? new MemoryDb() : new SupabaseDb()) as unknown as RawDb;
  return raw!;
}
export function useMemoryDb() { process.env.DB_MODE = 'memory'; raw = null; return backend() as unknown as MemoryDb; }

class Snap { constructor(private v: any) {} exists() { return this.v !== null && this.v !== undefined; } val() { return this.v; } }
const norm = (p: string) => p.split('/').filter(Boolean).join('/');

export class Ref {
  constructor(private p: string) {}
  private r() { return backend().ref(this.p || undefined); }
  get key() { const s = this.p.split('/').filter(Boolean); return s[s.length - 1] || null; }
  async get() { const s = await this.r().get(); return new Snap(s.exists() ? unsparse(s.val()) : null); }
  async set(v: unknown) { const c = clean(v); await this.r().set(c === undefined ? null : c); }
  async update(v: Record<string, unknown>) {
    const u: Record<string, any> = {};
    for (const [k, x] of Object.entries(v || {})) { const c = clean(x); u[norm(k)] = c === undefined ? null : c; }
    if (Object.keys(u).length) await this.r().update(u);
  }
  async remove() { await this.r().remove(); }
  push() { return new Ref(`${this.p}/${backend().ref(this.p || undefined).push().key}`); }
  child(p: string) { return new Ref(`${this.p}/${p}`); }
  /** RTDB optimistic transaction: fn may run more than once (first with null); return undefined to abort. */
  async transaction(fn: (cur: any) => any) {
    const res = await this.r().transaction((cur: any) => {
      const next = fn(cur === null || cur === undefined ? cur : unsparse(cur));
      if (next === undefined) return undefined;
      const c = clean(next);
      return c === undefined ? null : c;
    });
    return { committed: res.committed, snapshot: new Snap(res.snapshot.exists() ? unsparse(res.snapshot.val()) : null) };
  }
}
export const db = { ref: (p = '') => new Ref(norm(p)) };
/** Whole database except the given subtrees (backups). */
export async function exportTree(exclude: string[] = []): Promise<Record<string, any>> {
  const b: any = backend();
  if (b.exportTree) return unsparse(await b.exportTree(exclude));
  const all = unsparse((await b.ref().get()).val()) || {};
  for (const ex of exclude) { const k = ex.split('/'); let n = all; for (const s of k.slice(0, -1)) n = n?.[s]; if (n) delete n[k[k.length - 1]]; }
  return all;
}
export const now = () => new Date().toISOString();
export const newId = () => backend().ref().push().key as string;

export async function get<T = any>(path: string): Promise<T | null> {
  const s = await db.ref(path).get();
  return s.exists() ? (s.val() as T) : null;
}
export const set = (path: string, v: unknown) => db.ref(path).set(v);

/** Newest-first page over a node; cursor = id of the last row of the previous page. */
export async function listPage<T extends Record<string, any>>(path: string, opts: {
  cursor?: string; limit?: number; filter?: (v: T) => boolean; sortKey?: string; asc?: boolean;
} = {}) {
  const all = (await get<Record<string, T>>(path)) || {};
  const k = opts.sortKey || 'createdAt';
  let rows = Object.entries(all).filter(([, v]) => v && typeof v === 'object').map(([id, v]) => ({ ...v, id }))
    .filter((v) => (opts.filter ? opts.filter(v as unknown as T) : true))
    .sort((a: any, b: any) => (opts.asc ? 1 : -1) * String(a[k] || '').localeCompare(String(b[k] || '')));
  const total = rows.length;
  if (opts.cursor) { const i = rows.findIndex((r) => r.id === opts.cursor); if (i >= 0) rows = rows.slice(i + 1); }
  const limit = Math.min(opts.limit || 20, 200);
  const items = rows.slice(0, limit);
  return { items, nextCursor: rows.length > limit ? items[items.length - 1].id : null, total };
}
