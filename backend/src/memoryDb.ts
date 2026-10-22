/**
 * Strict in-memory emulation of the path-tree semantics (RTDB-style) (for tests only — DB_MODE=memory):
 * rejects undefined/NaN/invalid keys/ancestor paths in update like the real SDK, stores arrays as numeric-key objects,
 * returns numeric-key objects as (sparse) arrays like RTDB, and runs transactions with an initial null like the real client.
 */
import crypto from 'node:crypto';

const BAD_KEY = /[.#$/\[\]\u0000-\u001F\u007F]/;
const split = (p?: string) => (p || '').split('/').filter(Boolean);
function validate(v: any, where: string): any {
  if (v === undefined) throw new Error(`RTDB: ${where} contains undefined`);
  if (typeof v === 'number' && !Number.isFinite(v)) throw new Error(`RTDB: ${where} contains NaN/Infinity`);
  if (typeof v === 'function' || typeof v === 'symbol' || typeof v === 'bigint') throw new Error(`RTDB: ${where} contains ${typeof v}`);
  if (v === null || typeof v !== 'object') return v;
  const out: Record<string, any> = {};
  for (const [k, x] of Object.entries(v)) {
    if (!k || BAD_KEY.test(k) || Buffer.byteLength(k) > 768) throw new Error(`RTDB: invalid key "${k}" in ${where}`);
    const c = validate(x, `${where}.${k}`);
    if (c !== null && !(typeof c === 'object' && !Object.keys(c).length)) out[k] = c;
  }
  return out;
}
function toVal(n: any): any {
  if (n === null || typeof n !== 'object') return n;
  const keys = Object.keys(n);
  let allInt = keys.length > 0, max = -1;
  for (const k of keys) { if (!/^(0|[1-9]\d{0,8})$/.test(k)) { allInt = false; break; } max = Math.max(max, Number(k)); }
  if (allInt && max < keys.length * 2) { const a: any[] = []; for (const k of keys) a[Number(k)] = toVal(n[k]); return a; }
  const o: Record<string, any> = {};
  for (const k of keys) o[k] = toVal(n[k]);
  return o;
}
const PUSH = '-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz';
let lastT = 0, lastR: number[] = [];
export function pushId() {
  let t = Date.now(); const dup = t === lastT; lastT = t;
  const ts: string[] = []; for (let i = 0; i < 8; i++) { ts.unshift(PUSH[t % 64]); t = Math.floor(t / 64); }
  if (!dup) lastR = Array.from(crypto.randomBytes(12), (b) => b % 64);
  else { let i = 11; while (i >= 0 && lastR[i] === 63) lastR[i--] = 0; if (i >= 0) lastR[i]++; }
  return ts.join('') + lastR.map((n) => PUSH[n]).join('');
}
class S { constructor(private v: any) {} exists() { return this.v !== null && this.v !== undefined; } val() { return this.v === undefined ? null : structuredClone(this.v); } }

export class MemoryDb {
  root: Record<string, any> = {};
  writes = 0; bytesRead = 0;
  private getAt(parts: string[]) { let n: any = this.root; for (const k of parts) { if (n === null || typeof n !== 'object' || !(k in n)) return null; n = n[k]; } return n; }
  private setAt(parts: string[], v: any) {
    if (!parts.length) { this.root = v && typeof v === 'object' ? v : {}; return; }
    const chain: any[] = [this.root]; let n = this.root;
    for (let i = 0; i < parts.length - 1; i++) {
      if (n[parts[i]] === null || typeof n[parts[i]] !== 'object') { if (v === null) return; n[parts[i]] = {}; }
      n = n[parts[i]]; chain.push(n);
    }
    const last = parts[parts.length - 1];
    if (v === null || (typeof v === 'object' && !Object.keys(v).length)) delete n[last]; else n[last] = structuredClone(v);
    for (let i = chain.length - 1; i > 0; i--) if (!Object.keys(chain[i]).length) delete chain[i - 1][parts[i - 1]]; else break;
  }
  ref(p = ''): any {
    const parts = split(p);
    for (const k of parts) if (BAD_KEY.test(k)) throw new Error(`RTDB: invalid path "${p}"`);
    const self = this;
    return {
      key: parts[parts.length - 1] || null,
      async get() { const v = toVal(self.getAt(parts)); self.bytesRead += JSON.stringify(v ?? null).length; return new S(v); },
      async set(v: any) { self.setAt(parts, validate(v, `set(${p})`)); self.writes++; },
      async update(u: Record<string, any>) {
        if (!u || typeof u !== 'object') throw new Error('RTDB: update needs an object');
        const paths = Object.keys(u).map((k) => split(k).join('/'));
        for (const a of paths) for (const b of paths) if (a !== b && b.startsWith(a + '/')) throw new Error(`RTDB: update path "${a}" is an ancestor of "${b}"`);
        const vals = Object.entries(u).map(([k, v]) => { for (const s of split(k)) if (BAD_KEY.test(s)) throw new Error(`RTDB: invalid key in update path "${k}"`); return [k, validate(v, `update(${p}/${k})`)] as const; });
        for (const [k, v] of vals) self.setAt([...parts, ...split(k)], v);
        self.writes++;
      },
      async remove() { self.setAt(parts, null); self.writes++; },
      push() { return self.ref([...parts, pushId()].join('/')); },
      child(c: string) { return self.ref([...parts, ...split(c)].join('/')); },
      async transaction(fn: (c: any) => any) {
        fn(null);  // the real client first runs against its (empty) local cache
        const cur = toVal(structuredClone(self.getAt(parts)));
        const next = fn(cur);
        if (next === undefined) return { committed: false, snapshot: new S(cur) };
        self.setAt(parts, validate(next, `transaction(${p})`)); self.writes++;
        return { committed: true, snapshot: new S(toVal(self.getAt(parts))) };
      },
    };
  }
}
