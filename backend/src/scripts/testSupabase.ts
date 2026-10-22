/** Checks the Supabase connection end-to-end: npm run test:supabase (table, write/read/transaction, storage bucket). */
import '../env';
import { db, get } from '../db';
import { bucket } from '../files';
import { SUPABASE_URL } from '../supabase';

(async () => {
  console.log(`→ ${SUPABASE_URL()}`);
  const p = `_healthcheck/${Date.now()}`;
  await db.ref(p).set({ ok: true, ar: 'سند', n: 1 });
  await db.ref(p).update({ n: 2, 'deep/x': 'y' });
  await db.ref(`${p}/n`).transaction((v: any) => (v || 0) + 1);
  const v = await get<any>(p);
  if (!v || v.n !== 3 || v.ar !== 'سند' || v.deep?.x !== 'y') throw new Error('قراءة/كتابة غير متطابقة: ' + JSON.stringify(v));
  await db.ref('_healthcheck').remove();
  console.log('✓ قاعدة البيانات: كتابة / تحديث / معاملة / قراءة / حذف');
  const f = bucket().file(`_healthcheck/ملف تجربة.txt`);
  await f.save(Buffer.from('سند'), { contentType: 'text/plain' });
  const [buf] = await f.download();
  const [url] = await f.getSignedUrl({ action: 'read', expires: Date.now() + 120e3 });
  await f.delete();
  if (buf.toString() !== 'سند') throw new Error('محتوى الملف غير مطابق');
  console.log('✓ التخزين: رفع / تحميل / رابط موقّع / حذف\n  ' + url.slice(0, 90) + '…');
  console.log('\nكل شيء سليم ✅');
  process.exit(0);
})().catch((e) => { console.error('✗', e?.message || e); process.exit(1); });
