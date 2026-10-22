/**
 * Processes law PDFs uploaded from the admin console (status WAITING_LOCAL) with the Python pipeline in tools/ingest — npm run process-queue.
 * Needs python3 + the packages in tools/ingest/README.md on this PC.
 */
import '../env';
import { get } from '../db';
import { processPdf } from '../routes/admin';

(async () => {
  const q = (await get<Record<string, any>>('ingestQueue')) || {};
  const todo = Object.entries(q).filter(([, x]) => x?.status === 'WAITING_LOCAL' || (process.argv.includes('--retry') && x?.status === 'FAILED'));
  if (!todo.length) { console.log('لا توجد ملفات PDF في الانتظار.'); process.exit(0); }
  for (const [id, x] of todo) {
    console.log(`→ ${id} — ${x.meta?.title || ''}`);
    await processPdf(id, x.storageKey, x.meta);
    const s = await get<any>(`ingestQueue/${id}`);
    console.log(`  ${s?.status}${s?.articles ? ` — ${s.articles} مادة` : ''}${s?.error ? `\n  ${String(s.error).slice(-300)}` : ''}`);
  }
  process.exit(0);
})().catch((e) => { console.error('✗', e?.message || e); process.exit(1); });
