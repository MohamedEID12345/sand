/** LLM providers: Gemini, OpenAI, Groq and OpenRouter. AI_PROVIDER=auto tries them in order (gemini → openai → groq → openrouter)
 * with AI_TIMEOUT_MS per call and AI_MAX_RETRIES retries each; a specific name (e.g. AI_PROVIDER=groq) puts that provider first.
 * Admin console overrides: config/ai.models { primary, gemini, geminiPro, openai, groq, openrouter, embedding }. */
import { GoogleGenAI } from '@google/genai';
import { aiConfig } from './config';
import { ApiError } from '../http';

const gemini = process.env.GEMINI_API_KEY ? new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY }) : null;
const openaiKey = process.env.OPENAI_API_KEY || '';
const groqKey = process.env.GROQ_API_KEY || '';
const openrouterKey = process.env.OPENROUTER_API_KEY || '';
const TIMEOUT = Number(process.env.AI_TIMEOUT_MS || 15000);
const RETRIES = Number(process.env.AI_MAX_RETRIES ?? 1);
type Provider = 'gemini' | 'openai' | 'groq' | 'openrouter';
const ALL: Provider[] = ['gemini', 'openai', 'groq', 'openrouter'];
const configured = (p: Provider) => ({ gemini: !!gemini, openai: !!openaiKey, groq: !!groqKey, openrouter: !!openrouterKey }[p]);
function withTimeout<T>(p: Promise<T>, ms: number): Promise<T> {
  return new Promise((resolve, reject) => { const t = setTimeout(() => reject(new Error(`timeout ${ms}ms`)), ms); p.then((v) => { clearTimeout(t); resolve(v); }, (e) => { clearTimeout(t); reject(e); }); });
}
type Slot = 'fast' | 'pro';
export interface WebSource { title: string; url: string }

async function models() {
  const m = (await aiConfig()).models || {};
  const g = process.env.GEMINI_MODEL || 'gemini-2.5-flash';
  return {
    primary: String(m.primary || process.env.AI_PROVIDER || process.env.AI_PRIMARY || 'auto'),
    gemini: { fast: m.gemini || m.fast || g, pro: m.geminiPro || m.pro || process.env.GEMINI_MODEL_PRO || g },
    openai: m.openai || process.env.OPENAI_MODEL || 'gpt-4o-mini',
    groq: m.groq || process.env.GROQ_MODEL || 'openai/gpt-oss-120b',
    openrouter: m.openrouter || process.env.OPENROUTER_MODEL || 'openrouter/free',
    embedding: m.embedding || process.env.GEMINI_EMBEDDING_MODEL || 'gemini-embedding-001',
    dims: Number(m.embeddingDimensions || process.env.EMBEDDING_DIMENSIONS || 768),
  };
}
export async function providersStatus() {
  const m = await models();
  return { primary: m.primary, order: order(m.primary), gemini: gemini ? m.gemini : null, openai: openaiKey ? m.openai : null, groq: groqKey ? m.groq : null,
    openrouter: openrouterKey ? m.openrouter : null, embedding: gemini ? m.embedding : null, timeoutMs: TIMEOUT, retries: RETRIES };
}

interface GenOpts { slot?: Slot; system: string; history?: { role: 'user' | 'model'; text: string }[]; prompt: string; json?: boolean; temperature?: number; web?: boolean }

async function viaGemini(o: GenOpts, model: string): Promise<{ text: string; web: WebSource[] }> {
  if (!gemini) throw new Error('gemini not configured');
  const contents = [...(o.history || []).map((h) => ({ role: h.role, parts: [{ text: h.text }] })), { role: 'user', parts: [{ text: o.prompt }] }];
  const res: any = await gemini.models.generateContent({ model, contents, config: { systemInstruction: o.system, temperature: o.temperature ?? 0.2,
    ...(o.json && !o.web ? { responseMimeType: 'application/json' } : {}), ...(o.web ? { tools: [{ googleSearch: {} }] } : {}) } });
  const chunks = res.candidates?.[0]?.groundingMetadata?.groundingChunks || [];
  return { text: res.text || '', web: chunks.filter((c: any) => c.web?.uri).map((c: any) => ({ title: c.web.title || c.web.uri, url: c.web.uri })) };
}

async function viaOpenAI(o: GenOpts, model: string): Promise<{ text: string; web: WebSource[] }> {
  if (!openaiKey) throw new Error('openai not configured');
  const headers = { Authorization: `Bearer ${openaiKey}`, 'Content-Type': 'application/json' };
  if (o.web) {  // Responses API with the hosted web-search tool
    const input = [{ role: 'system', content: o.system }, ...(o.history || []).map((h) => ({ role: h.role === 'model' ? 'assistant' : 'user', content: h.text })), { role: 'user', content: o.prompt }];
    const r = await fetch('https://api.openai.com/v1/responses', { method: 'POST', headers, body: JSON.stringify({ model, input, tools: [{ type: 'web_search_preview' }], temperature: o.temperature ?? 0.2 }) });
    const j: any = await r.json();
    if (!r.ok) throw new Error(j.error?.message || `openai ${r.status}`);
    const msg = (j.output || []).find((x: any) => x.type === 'message');
    const parts = msg?.content || [];
    const text = parts.map((p: any) => p.text || '').join('');
    const web = parts.flatMap((p: any) => (p.annotations || []).filter((a: any) => a.type === 'url_citation').map((a: any) => ({ title: a.title || a.url, url: a.url })));
    return { text, web };
  }
  const messages = [{ role: 'system', content: o.system }, ...(o.history || []).map((h) => ({ role: h.role === 'model' ? 'assistant' : 'user', content: h.text })), { role: 'user', content: o.prompt }];
  const r = await fetch('https://api.openai.com/v1/chat/completions', { method: 'POST', headers,
    body: JSON.stringify({ model, messages, temperature: o.temperature ?? 0.2, ...(o.json ? { response_format: { type: 'json_object' } } : {}) }) });
  const j: any = await r.json();
  if (!r.ok) throw new Error(j.error?.message || `openai ${r.status}`);
  return { text: j.choices?.[0]?.message?.content || '', web: [] };
}

/** OpenAI-compatible chat endpoints (Groq, OpenRouter). */
async function viaCompatible(o: GenOpts, url: string, key: string, model: string, extraHeaders: Record<string, string> = {}) {
  const messages = [{ role: 'system', content: o.system }, ...(o.history || []).map((h) => ({ role: h.role === 'model' ? 'assistant' : 'user', content: h.text })), { role: 'user', content: o.prompt }];
  const ctl = new AbortController(), t = setTimeout(() => ctl.abort(), TIMEOUT);
  try {
    const r = await fetch(url, { method: 'POST', signal: ctl.signal, headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json', ...extraHeaders },
      body: JSON.stringify({ model, messages, temperature: o.temperature ?? 0.2, ...(o.json ? { response_format: { type: 'json_object' } } : {}) }) });
    const j: any = await r.json();
    if (!r.ok) throw new Error(j.error?.message || `${r.status}`);
    return { text: j.choices?.[0]?.message?.content || '', web: [] as WebSource[] };
  } finally { clearTimeout(t); }
}
function order(primary: string): Provider[] {
  const first = ALL.includes(primary as Provider) ? [primary as Provider] : [];
  return [...first, ...ALL.filter((p) => !first.includes(p))].filter(configured);
}
async function call(p: Provider, o: GenOpts, m: Awaited<ReturnType<typeof models>>) {
  switch (p) {
    case 'gemini': return withTimeout(viaGemini(o, m.gemini[o.slot || 'fast']), o.web ? TIMEOUT * 2 : TIMEOUT);
    case 'openai': return withTimeout(viaOpenAI(o, m.openai), o.web ? TIMEOUT * 2 : TIMEOUT);
    case 'groq': return viaCompatible(o, 'https://api.groq.com/openai/v1/chat/completions', groqKey, m.groq);
    case 'openrouter': return viaCompatible(o, 'https://openrouter.ai/api/v1/chat/completions', openrouterKey, m.openrouter,
      { 'HTTP-Referer': process.env.PUBLIC_BASE_URL || 'https://legalguide.app', 'X-Title': 'Sand' });
  }
}
/** Tries every configured provider in order (with retries). Web-grounded requests prefer providers that can search
 * (Gemini, OpenAI); if none of them answers, the others reply from general knowledge and webUnavailable is set. */
export async function generateFull(o: GenOpts): Promise<{ text: string; web: WebSource[]; provider: Provider; webUnavailable?: boolean }> {
  const m = await models();
  const list = order(m.primary), errors: string[] = [];
  const tryList = async (ps: Provider[], opts: GenOpts) => {
    for (const p of ps) for (let a = 0; a <= RETRIES; a++) {
      try { const r = await call(p, opts, m); if (r.text.trim()) return { ...r, provider: p }; throw new Error('empty'); }
      catch (e: any) { errors.push(`${p}#${a + 1}: ${e.message}`); }
    }
    return null;
  };
  let res = o.web ? await tryList(list.filter((p) => p === 'gemini' || p === 'openai'), o) : null;
  if (o.web && res) return res;
  res = await tryList(o.web ? list.filter((p) => p === 'groq' || p === 'openrouter') : list, { ...o, web: false });
  if (res) return { ...res, webUnavailable: !!o.web };
  console.error('[ai] all providers failed', errors.slice(-8));
  throw new ApiError(503, 'AI_UNAVAILABLE', 'خدمة الذكاء الاصطناعي غير متاحة الآن، حاول بعد قليل');
}
export const generate = async (o: GenOpts) => (await generateFull(o)).text;

export async function embedQuery(text: string): Promise<Float32Array | null> {
  if (!gemini) return null;
  const m = await models();
  const res = await gemini.models.embedContent({ model: m.embedding, contents: [text], config: { taskType: 'RETRIEVAL_QUERY', outputDimensionality: m.dims } });
  const v = res.embeddings?.[0]?.values;
  return v ? Float32Array.from(v) : null;
}


/** Health check for every configured provider: a short chat, web search (Gemini/OpenAI) and embeddings. Used by the admin console. */
export async function testProviders() {
  const m = await models(), out: any[] = [];
  const probe: GenOpts = { system: 'أجب بكلمة واحدة فقط.', prompt: 'اكتب كلمة: يعمل', temperature: 0 };
  for (const p of ALL) {
    if (!configured(p)) { out.push({ provider: p, check: 'chat', ok: false, error: 'لا يوجد مفتاح' }); continue; }
    const model = p === 'gemini' ? m.gemini.fast : (m as any)[p];
    const t0 = Date.now();
    try { const r = await call(p, probe, m); out.push({ provider: p, model, check: 'chat', ok: !!r.text.trim(), ms: Date.now() - t0, reply: r.text.trim().slice(0, 60) }); }
    catch (e: any) { out.push({ provider: p, model, check: 'chat', ok: false, ms: Date.now() - t0, error: String(e.message).slice(0, 300) }); }
    if (p === 'gemini' || p === 'openai') {
      const t1 = Date.now();
      try { const r = await call(p, { ...probe, web: true, system: 'أجب باختصار شديد.', prompt: 'ما هو تاريخ اليوم؟' }, m);
        out.push({ provider: p, model, check: 'web', ok: !!r.text.trim(), ms: Date.now() - t1, reply: r.text.trim().slice(0, 60), sources: r.web.length }); }
      catch (e: any) { out.push({ provider: p, model, check: 'web', ok: false, ms: Date.now() - t1, error: String(e.message).slice(0, 300) }); }
    }
  }
  if (gemini) {
    const t2 = Date.now();
    try { const v = await embedQuery('اختبار'); out.push({ provider: 'gemini', model: m.embedding, check: 'embedding', ok: !!v?.length, ms: Date.now() - t2, reply: v ? `${v.length} بُعد` : '' }); }
    catch (e: any) { out.push({ provider: 'gemini', model: m.embedding, check: 'embedding', ok: false, ms: Date.now() - t2, error: String(e.message).slice(0, 300) }); }
  }
  return { order: order(m.primary), results: out };
}
