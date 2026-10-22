/** Standalone AI provider test (does not touch the database — safe while the server runs):
 *   npm run test:ai        (reads backend/.env) */
import '../env';
import { GoogleGenAI } from '@google/genai';

const T = Number(process.env.AI_TIMEOUT_MS || 15000) * 2;
const timeout = <X>(p: Promise<X>) => Promise.race([p, new Promise<X>((_, r) => setTimeout(() => r(new Error(`timeout ${T}ms`)), T))]);
async function compat(url: string, key: string, model: string, extra: Record<string, string> = {}) {
  const r = await timeout(fetch(url, { method: 'POST', headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json', ...extra },
    body: JSON.stringify({ model, messages: [{ role: 'user', content: 'اكتب كلمة واحدة: يعمل' }], temperature: 0 }) }));
  const j: any = await r.json();
  if (!r.ok) throw new Error(`${r.status} ${j.error?.message || JSON.stringify(j).slice(0, 200)}`);
  return String(j.choices?.[0]?.message?.content || '').trim();
}
const tests: [string, string | undefined, () => Promise<string>][] = [
  ['Gemini chat', process.env.GEMINI_API_KEY, async () => {
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY! });
    return (await timeout(ai.models.generateContent({ model: process.env.GEMINI_MODEL || 'gemini-2.5-flash', contents: 'اكتب كلمة واحدة: يعمل' }))).text?.trim() || '';
  }],
  ['Gemini web search', process.env.GEMINI_API_KEY, async () => {
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY! });
    const r: any = await timeout(ai.models.generateContent({ model: process.env.GEMINI_MODEL || 'gemini-2.5-flash', contents: 'ما تاريخ اليوم؟ أجب في سطر.', config: { tools: [{ googleSearch: {} }] } }));
    return `${r.text?.trim().slice(0, 50)} (مصادر: ${r.candidates?.[0]?.groundingMetadata?.groundingChunks?.length || 0})`;
  }],
  ['Gemini embeddings', process.env.GEMINI_API_KEY, async () => {
    const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY! });
    const r = await timeout(ai.models.embedContent({ model: process.env.GEMINI_EMBEDDING_MODEL || 'gemini-embedding-001', contents: ['اختبار'], config: { outputDimensionality: 768 } }));
    return `${r.embeddings?.[0]?.values?.length || 0} بُعد`;
  }],
  ['OpenAI chat', process.env.OPENAI_API_KEY, () => compat('https://api.openai.com/v1/chat/completions', process.env.OPENAI_API_KEY!, process.env.OPENAI_MODEL || 'gpt-4o-mini')],
  ['Groq chat', process.env.GROQ_API_KEY, () => compat('https://api.groq.com/openai/v1/chat/completions', process.env.GROQ_API_KEY!, process.env.GROQ_MODEL || 'openai/gpt-oss-120b')],
  ['OpenRouter chat', process.env.OPENROUTER_API_KEY, () => compat('https://openrouter.ai/api/v1/chat/completions', process.env.OPENROUTER_API_KEY!, process.env.OPENROUTER_MODEL || 'openrouter/free',
    { 'HTTP-Referer': process.env.PUBLIC_BASE_URL || 'https://legalguide.app', 'X-Title': 'Sand' })],
];
(async () => {
  let okCount = 0;
  for (const [name, key, fn] of tests) {
    if (!key) { console.log(`⚪ ${name.padEnd(20)} لا يوجد مفتاح في .env`); continue; }
    const t0 = Date.now();
    try { const r = await fn(); okCount++; console.log(`✅ ${name.padEnd(20)} ${Date.now() - t0}ms  ${r}`); }
    catch (e: any) { const msg = String(e.message); console.log(`❌ ${name.padEnd(20)} ${Date.now() - t0}ms  ${msg === 'fetch failed' ? 'تعذر الاتصال بالمزود (السيرفر ممنوع من الإنترنت أو الدومين محجوب)' : msg.slice(0, 220)}`); }
  }
  console.log(okCount ? `\nيعمل ${okCount} اختبار — التطبيق يستخدم أول مزود ناجح حسب AI_PROVIDER.` : '\nلا يوجد مزود يعمل — راجع المفاتيح وأسماء الموديلات.');
  process.exit(okCount ? 0 : 1);
})();
