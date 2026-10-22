/** Builds all missing semantic-search vectors (Gemini embeddings) — npm run embed. Resumable. */
import '../env';
import { runEmbeddings } from '../services/embeddings';

(async () => {
  const r = await runEmbeddings(Infinity, true);
  console.log(r);
  process.exit(r.started && !(r as any).error ? 0 : 1);
})().catch((e) => { console.error('✗', e?.message || e); process.exit(1); });
