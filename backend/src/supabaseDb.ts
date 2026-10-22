/**
 * Realtime-Database-style path tree stored in Supabase Postgres (table public.lg_nodes, see supabase/schema.sql).
 *
 * Each row holds the JSON of one "document" at a fixed depth per root (users/<uid>, library/articles/<lawId>,
 * officeData/<office>/<kind>/<id>, …). Reads above that depth assemble rows; writes below it patch the row with
 * optimistic concurrency (version check in lg_apply), so multi-path updates and transactions stay atomic.
 */
import { supabase } from './supabase';
import { pushId } from './memoryDb';

const DEPTH: Record<string, number> = {
  library: 3, officeData: 4, userIndex: 3, catalog: 3, stats: 3, payments: 3,
  aiChats: 3, aiIdem: 3, aiMessages: 3, aiUploads: 3, contracts: 3, creditTx: 3, invoices: 3, notifications: 3,
  ticketMessages: 3, tickets: 3, officeTemplates: 3, ordersByUser: 3, caseDocuments: 4, caseNotes: 4, caseAnalyses: 4,
  clients: 3, cases: 3, hearings: 3, officeMembers: 3, invitations: 3, promoRedemptions: 3, private: 3, auditLogs: 3, aiUsage: 3,
};
const depthOf = (segs: string[]) => DEPTH[segs[0]] || 2;
const join = (s: string[]) => s.join('/');
const isObj = (v: any) => v !== null && typeof v === 'object';
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** RTDB storage normal form: drop nulls and empty objects; empty → null. */
function prune(v: any): any {
  if (v === undefined || v === null) return null;
  if (Array.isArray(v)) { const a = v.map(prune); return a.some((x) => x !== null) ? a : null; }
  if (!isObj(v)) return v;
  const o: Record<string, any> = {};
  for (const [k, x] of Object.entries(v)) { const c = prune(x); if (c !== null) o[k] = c; }
  return Object.keys(o).length ? o : null;
}
function getIn(v: any, keys: string[]): any {
  let n = v;
  for (const k of keys) { if (!isObj(n)) return null; n = n[k]; if (n === undefined) return null; }
  return n ?? null;
}
/** Immutable deep set with RTDB semantics (null deletes and prunes empty parents). */
function setIn(base: any, keys: string[], val: any): any {
  if (!keys.length) return prune(val);
  const [k, ...rest] = keys;
  const node = isObj(base) ? (Array.isArray(base) ? [...base] : { ...base }) : {};
  const child = setIn(isObj(base) ? (base as any)[k] : null, rest, val);
  if (child === null) { if (Array.isArray(node)) (node as any)[Number(k)] = null; else delete (node as any)[k]; }
  else (node as any)[k] = child;
  return prune(node);
}

type Row = { path: string; value: any; ver: number };
/** Retries transient network failures (dropped keep-alive sockets, 502/503 from the gateway). */
const TRANSIENT = /fetch failed|other side closed|ECONNRESET|ETIMEDOUT|EPIPE|socket hang up|UND_ERR|502|503|504|Bad Gateway|Service Unavailable/i;
async function sb<T extends { error: any }>(call: () => PromiseLike<T>): Promise<T> {
  for (let i = 0; ; i++) {
    const r = await call();
    const msg = r.error ? `${r.error.message || ''} ${r.error.details || ''} ${r.error.code || ''}` : '';
    if (!r.error || !TRANSIENT.test(msg) || i >= 4) return r;
    await sleep(200 * (i + 1) + Math.random() * 200);
  }
}
async function rowsExact(paths: string[]): Promise<Row[]> {
  if (!paths.length) return [];
  const out: Row[] = [];
  for (let i = 0; i < paths.length; i += 100) {
    const chunk = paths.slice(i, i + 100);
    const { data, error } = await sb(() => supabase().from('lg_nodes').select('path,value,ver').in('path', chunk));
    if (error) throw new Error(`supabase read: ${error.message}`);
    out.push(...((data as Row[]) || []));
  }
  return out;
}
async function rowsUnder(prefix: string, exclude: string[] = []): Promise<Row[]> {
  const out: Row[] = [];
  const PAGE = 100;
  for (let from = 0; ; from += PAGE) {
    const { data, error } = await sb(() => {
      let q = supabase().from('lg_nodes').select('path,value,ver');
      if (prefix) q = q.gte('path', prefix + '/').lt('path', prefix + '0');
      for (const ex of exclude) q = q.not('path', 'like', `${ex.replace(/[%_\\]/g, '\\$&')}/%`);
      return q.order('path').range(from, from + PAGE - 1);
    });
    if (error) throw new Error(`supabase read: ${error.message}`);
    out.push(...((data as Row[]) || []));
    if (!data || data.length < PAGE) break;
  }
  return out;
}
async function apply(ops: any[]) {
  if (!ops.length) return;
  const { error } = await sb(() => supabase().rpc('lg_apply', { ops }));
  if (error) {
    if (/LG_CONFLICT/.test(error.message)) throw Object.assign(new Error('LG_CONFLICT'), { conflict: true });
    if (/lg_apply|lg_nodes/.test(error.message) && /not find|does not exist|schema cache/i.test(error.message))
      throw new Error('جدول قاعدة البيانات غير موجود — شغّل supabase/schema.sql في Supabase → SQL Editor أولًا');
    throw new Error(`supabase write: ${error.message}`);
  }
}

/** Reads a node at any depth. */
async function readPath(segs: string[]): Promise<any> {
  const D = depthOf(segs);
  const anc = segs.map((_, i) => join(segs.slice(0, i + 1)));
  if (segs.length >= D) {
    const rowPath = join(segs.slice(0, D));
    const rows = await rowsExact(segs.slice(0, D).map((_, i) => join(segs.slice(0, i + 1))));
    const row = rows.find((r) => r.path === rowPath);
    if (row) return getIn(row.value, segs.slice(D));
    const up = rows.sort((a, b) => b.path.length - a.path.length)[0];
    return up ? getIn(up.value, segs.slice(up.path.split('/').length)) : null;
  }
  const p = join(segs);
  const [exact, under] = await Promise.all([segs.length ? rowsExact(anc) : Promise.resolve([]), rowsUnder(p)]);
  let result: any = null;
  const self = exact.filter((r) => r.path === p || p.startsWith(r.path + '/')).sort((a, b) => b.path.length - a.path.length)[0];
  if (self) result = getIn(self.value, segs.slice(self.path.split('/').length));
  const base = segs.length;
  for (const r of under) result = setIn(result, r.path.split('/').slice(base), r.value);
  return result;
}

/** Splits a write into row operations. Patches inside a row are returned separately (need read + version check). */
function plan(segs: string[], value: any, ops: any[], patches: Map<string, { sub: string[]; v: any }[]>) {
  const D = depthOf(segs);
  if (!segs.length) throw new Error('writing the database root is not supported');
  for (let i = 1; i < Math.min(segs.length, D); i++) ops.push({ o: 'del', p: join(segs.slice(0, i)) });
  if (segs.length >= D) {
    const rowPath = join(segs.slice(0, D)), sub = segs.slice(D);
    if (!sub.length) { ops.push({ o: 'put', p: rowPath, v: prune(value) }); return; }
    if (!patches.has(rowPath)) patches.set(rowPath, []);
    patches.get(rowPath)!.push({ sub, v: value });
    return;
  }
  ops.push({ o: 'deltree', p: join(segs) });
  const explode = (s: string[], v: any) => {
    v = prune(v);
    if (v === null) return;
    if (s.length >= D || !isObj(v)) { ops.push({ o: 'put', p: join(s), v }); return; }
    for (const [k, x] of Object.entries(v)) explode([...s, k], x);
  };
  explode(segs, value);
}
async function write(entries: { segs: string[]; value: any }[]) {
  const ops: any[] = [], patches = new Map<string, { sub: string[]; v: any }[]>();
  for (const e of entries) plan(e.segs, e.value, ops, patches);
  for (let attempt = 0; ; attempt++) {
    const cas: any[] = [];
    if (patches.size) {
      const cur = new Map((await rowsExact([...patches.keys()])).map((r) => [r.path, r]));
      for (const [rowPath, list] of patches) {
        const r = cur.get(rowPath);
        let v = r ? r.value : null;
        for (const { sub, v: x } of list) v = setIn(v, sub, x);
        if (!r && v === null) continue;
        cas.push({ o: 'cas', p: rowPath, ver: r ? r.ver : 0, v });
      }
    }
    try { await apply([...ops, ...cas]); return; }
    catch (e: any) { if (!e.conflict || attempt > 40) throw e; await sleep(20 + Math.random() * 60 * Math.min(attempt + 1, 8)); }
  }
}

class Snap { constructor(private v: any) {} exists() { return this.v !== null && this.v !== undefined; } val() { return this.v ?? null; } }

class SbRef {
  constructor(private segs: string[]) {}
  get key() { return this.segs[this.segs.length - 1] || null; }
  async get() { return new Snap(await readPath(this.segs)); }
  async set(v: any) { await write([{ segs: this.segs, value: v }]); }
  async update(u: Record<string, any>) {
    await write(Object.entries(u).map(([k, v]) => ({ segs: [...this.segs, ...k.split('/').filter(Boolean)], value: v })));
  }
  async remove() { await write([{ segs: this.segs, value: null }]); }
  push() { return new SbRef([...this.segs, pushId()]); }
  child(p: string) { return new SbRef([...this.segs, ...p.split('/').filter(Boolean)]); }
  async transaction(fn: (cur: any) => any) {
    const D = depthOf(this.segs);
    if (this.segs.length < D) throw new Error(`transaction above document depth: ${join(this.segs)}`);
    const rowPath = join(this.segs.slice(0, D)), sub = this.segs.slice(D);
    for (let attempt = 0; ; attempt++) {
      const [r] = await rowsExact([rowPath]);
      const cur = r ? getIn(r.value, sub) : null;
      const next = fn(cur === null ? null : structuredClone(cur));
      if (next === undefined) return { committed: false, snapshot: new Snap(cur) };
      const rowVal = setIn(r ? r.value : null, sub, next);
      try {
        if (r || rowVal !== null) await apply([{ o: 'cas', p: rowPath, ver: r ? r.ver : 0, v: rowVal }]);
        return { committed: true, snapshot: new Snap(prune(next)) };
      } catch (e: any) { if (!e.conflict || attempt > 40) throw e; await sleep(20 + Math.random() * 60 * Math.min(attempt + 1, 8)); }
    }
  }
}

export class SupabaseDb {
  ref(p?: string) { return new SbRef((p || '').split('/').filter(Boolean)); }
  /** Whole tree except some subtrees (backup). */
  async exportTree(exclude: string[]) {
    let out: any = null;
    for (const r of await rowsUnder('', exclude)) out = setIn(out, r.path.split('/'), r.value);
    return out || {};
  }
}
