/** Background builder for semantic-search vectors: embeds every library chunk that has no vector yet (Gemini embeddings). */
import { GoogleGenAI } from '@google/genai';
import { db, get, now } from '../db';
import { aiConfig } from './config';

let running = false;
export const embedStatus = async () => ({ running, ...((await get<any>('library/index/embedding')) || {}) });
/**
 * Embeds library chunks that have no vector yet. Resumable: each call continues where the last stopped.
 * budgetMs bounds one run (an admin request); the local script (npm run embed) runs until everything is done.
 */
export async function runEmbeddings(budgetMs = Infinity, log = false) {
  if (running) return { started: false, reason: 'already running' };
  if (!process.env.GEMINI_API_KEY) return { started: false, reason: 'GEMINI_API_KEY missing' };
  running = true;
  const t0 = Date.now();
  let done = 0, total = 0, finished = false;
  try {
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY! });
    const m = (await aiConfig()).models || {};
    const model = m.embedding || process.env.GEMINI_EMBEDDING_MODEL || 'gemini-embedding-001', dims = Number(m.embeddingDimensions || process.env.EMBEDDING_DIMENSIONS || 768);
    const chunks = (await get<Record<string, Record<string, any>>>('library/chunks')) || {};
    const todo: [string, string, string][] = [];
    for (const [item, rows] of Object.entries(chunks)) for (const [cid, c] of Object.entries(rows || {})) if (c && !c.embedding && c.text) todo.push([item, cid, c.text]);
    total = todo.length;
    const BATCH = Math.max(1, Number(process.env.EMBED_BATCH || 20));
    const PAUSE = Math.max(0, Number(process.env.EMBED_PAUSE_MS ?? 1500));
    for (let i = 0; i < todo.length; i += BATCH) {
      if (Date.now() - t0 > budgetMs) break;
      const batch = todo.slice(i, i + BATCH);
      let res: any;
      for (let attempt = 0; ; attempt++) {
        try { res = await ai.models.embedContent({ model, contents: batch.map((b) => b[2]), config: { taskType: 'RETRIEVAL_DOCUMENT', outputDimensionality: dims } }); break; }
        catch (e: any) {
          const msg = String(e?.message || e);
          if (!/429|RESOURCE_EXHAUSTED|quota/i.test(msg)) throw e;
          if (/PerDay|per day|daily/i.test(msg)) throw Object.assign(new Error('DAILY_QUOTA'), { daily: true });   // free-tier daily cap → resume tomorrow
          if (attempt >= 8) throw e;
          const wait = Math.min(90, Number(msg.match(/retry(?:Delay)?\D{0,10}(\d+(?:\.\d+)?)s/i)?.[1] || 30) + 2) * 1000;
          if (log) console.log(`[embed] rate limit — waiting ${Math.round(wait / 1000)}s`);
          if (Date.now() - t0 + wait > budgetMs) throw Object.assign(new Error('BUDGET'), { budget: true });
          await new Promise((r) => setTimeout(r, wait));
        }
      }
      const upd: Record<string, string> = {};
      res.embeddings?.forEach((e: any, j: number) => { if (e.values) upd[`library/chunks/${batch[j][0]}/${batch[j][1]}/embedding`] = Buffer.from(new Float32Array(e.values).buffer).toString('base64'); });
      await db.ref().update(upd);
      done += batch.length;
      if (done % 200 < BATCH || done >= total) await db.ref('library/index/embedding').set({ model, dimensions: dims, done, total, updatedAt: now() });
      if (log) console.log(`[embed] ${done}/${total}`);
      if (PAUSE) await new Promise((r) => setTimeout(r, PAUSE));
    }
    finished = done >= total;
    if (done) await db.ref('library/index/meta/version').set(Date.now());
    return { started: true, done, remaining: total - done, finished };
  } catch (e: any) {
    if (done) await db.ref('library/index/meta/version').set(Date.now());
    if (e.budget) return { started: true, done, remaining: total - done, finished: false };
    const error = e.daily ? 'تم استهلاك الحصة اليومية المجانية لـ Gemini Embeddings — أكمل غدًا بنفس الأمر، أو فعّل الفوترة في Google AI Studio' : String(e.message).slice(0, 300);
    await db.ref('library/index/embedding/error').set(error);
    return { started: true, done, remaining: total - done, finished: false, error };
  } finally { running = false; }
}
/** Admin button: runs up to ~6 minutes inside the request (bounded so the request does not time out). Press again to continue. */
export async function buildEmbeddings() { return runEmbeddings(6 * 60_000); }
