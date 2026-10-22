/**
 * One-time / after-content-change setup, run from your PC (needs backend/service-account.json):
 *   npm run setup   → uploads content/ (laws, templates, books) to the Realtime Database and creates/updates the owner account.
 */
import '../env';
import { syncContent, CONTENT_DIR } from '../services/contentSync';
import { seedOwner } from '../services/seed';
import { get } from '../db';
import { SUPABASE_URL, supabase, STORAGE_BUCKET } from '../supabase';

(async () => {
  console.log(`→ Supabase: ${SUPABASE_URL()}\n→ Content:  ${CONTENT_DIR}`);
  const t = await supabase().from('lg_nodes').select('path', { head: true, count: 'exact' });
  if (t.error) throw new Error(`قاعدة البيانات غير جاهزة (${t.error.message})\n  ← شغّل الملف supabase/schema.sql مرة واحدة: Supabase Dashboard → SQL Editor → الصق → Run`);
  const b = await supabase().storage.getBucket(STORAGE_BUCKET());
  if (b.error) {
    const c = await supabase().storage.createBucket(STORAGE_BUCKET(), { public: false, fileSizeLimit: 50 * 1024 * 1024 });
    if (c.error) throw new Error(`تعذر إنشاء مساحة الملفات ${STORAGE_BUCKET()}: ${c.error.message}`);
    console.log(`✓ أُنشئت مساحة الملفات ${STORAGE_BUCKET()}`);
  }
  console.log(`✓ الاتصال بـ Supabase سليم (${t.count ?? 0} صف حاليًا)`);
  const r = await syncContent();
  console.log(`✓ المحتوى: ${r.laws} قانون، ${r.templates} نموذج، ${r.books} كتاب (الملفات غير المتغيرة تُتخطى)`);
  await seedOwner();
  console.log(process.env.ADMIN_EMAIL ? `✓ حساب المالك: ${process.env.ADMIN_EMAIL}` : '⚠ ADMIN_EMAIL / ADMIN_PASSWORD غير موجودين في .env — لم يُنشأ حساب المالك');
  const meta = await get<any>('library/index/meta');
  console.log(`✓ المكتبة: ${meta?.lawCount ?? 0} تشريع، ${meta?.articleCount ?? 0} مادة`);
  process.exit(0);
})().catch((e) => { console.error('✗', e?.message || e); process.exit(1); });
