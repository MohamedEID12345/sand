# التشغيل الكامل خطوة بخطوة — سند (Sand) على Supabase + Android

## الصورة العامة
```
تطبيق Android ──HTTPS──▶ API سند (Node/Express)  ──المفتاح السري──▶ Supabase Postgres  (جدول lg_nodes)
لوحة التحكم /admin ─────┘   (Render أو جهازك)                    └─▶ Supabase Storage   (sand-files)
                                                                     └─▶ Gemini / OpenAI / Groq / OpenRouter ، فوري
```
- **Supabase** = قاعدة البيانات + الملفات. مشروعك: `https://yjqmqxpfgcbvxdbwddyf.supabase.co`
- **الـ API** يجب أن يعمل على خادم Node (Render مجاني، أو جهازك للتجربة). لا يعمل داخل Supabase Edge Functions لأن المكتبة القانونية (≈17 ألف مادة) تُحمَّل في الذاكرة للبحث، وحدود Edge Functions (ثانيتا CPU لكل طلب) لا تسمح بذلك.
- **التطبيق لا يتصل بـ Supabase مباشرة** ولا يحتوي أي مفتاح: لو فعل لأصبحت مفاتيح Gemini وفوري داخل الـ APK ولقدر أي أحد يزوّد رصيده.
- المفتاح العام `sb_publishable_…` و`SUPABASE_JWKS_URL` غير مستخدمين (التطبيق لا يستخدم Supabase Auth؛ الدخول عبر الـ API).

---

## الجزء 1 — Supabase (مرة واحدة)
1. افتح https://supabase.com/dashboard ← مشروعك.
2. **SQL Editor ← New query** ← افتح الملف `supabase/schema.sql` من المشروع، انسخ محتواه كله، الصقه ← **Run**.
   النتيجة المتوقعة: جدول صغير فيه `nodes = 0`. (الملف آمن للتكرار.)
3. تأكد: **Table Editor** فيه جدول `lg_nodes`، و**Storage** فيه مساحة `sand-files` (خاصة، Private).
4. المفاتيح موجودة بالفعل في `backend/.env`:
   `SUPABASE_URL` و`SUPABASE_SECRET_KEY` (من **Project Settings ← API Keys**).

## الجزء 2 — جهازك (مرة واحدة)
5. ثبّت **Node.js 22 LTS** (22.12 أو أحدث): https://nodejs.org ← تحقق: `node -v`
6. ثبّت **Git** و**Android Studio** (أحدث إصدار).

## الجزء 3 — تجهيز الـ backend ورفع البيانات
7. افتح Terminal داخل مجلد المشروع:
   ```
   cd backend
   npm install
   ```
8. اختبر الاتصال بـ Supabase:
   ```
   npm run test:supabase
   ```
   المتوقع: `✓ قاعدة البيانات…` و`✓ التخزين…` ثم `كل شيء سليم ✅`.
9. ارفع المكتبة والنماذج وأنشئ حساب المالك:
   ```
   npm run setup
   ```
   المتوقع: `✓ المحتوى: 103 قانون، 115 نموذج…` ثم `✓ حساب المالك: …` (البريد وكلمة المرور من `ADMIN_EMAIL`/`ADMIN_PASSWORD` في `.env`).
10. (اختياري) متجهات البحث الدلالي بـ Gemini:
    ```
    npm run embed
    ```
    - البحث يعمل بدونها (بحث بالكلمات)، والبحث الدلالي يتفعّل تلقائيًا فقط بعد اكتمال ≥90% من المتجهات.
    - الأمر يتعامل مع حد الطلبات (429) وينتظر ثم يكمل، ويمكن إيقافه وإعادة تشغيله في أي وقت (يكمل من حيث توقف).
    - الباقة المجانية لـ Gemini لها حد يومي؛ لو ظهرت رسالة «الحصة اليومية» أكمل في اليوم التالي، أو فعّل الفوترة في Google AI Studio (تكلفة المكتبة كلها تقريبًا أقل من دولار واحد).
    - ضبط السرعة (اختياري في `.env`): `EMBED_BATCH=20` و`EMBED_PAUSE_MS=1500`.

## الجزء 4 — تشغيل الـ API على جهازك (للتجربة)
11. انسخ `backend/.env.local.example` باسم `backend/.env.local` ثم:
    ```
    npm run dev
    ```
12. افتح:
    - لوحة التحكم: http://localhost:4000/admin/ ← ادخل بحساب المالك.
    - فحص الحالة: http://localhost:4000/api/v1/health

## الجزء 5 — نشر الـ API على الإنترنت (Render)
13. ارفع المشروع على مستودع GitHub **خاص** (`.gitignore` يمنع رفع `.env`).
14. https://dashboard.render.com ← **New ← Blueprint** ← اختر المستودع ← سيقرأ `render.yaml` ← **Apply**.
    (اسم الخدمة `sand-api`، المنطقة Frankfurt — يفضّل أن تكون منطقة مشروع Supabase أوروبية أيضًا.)
15. الخدمة ← **Environment ← Secret Files ← Add Secret File**:
    - Filename: `.env`
    - Contents: الصق محتوى `backend/.env` كاملًا ← **Save** ← **Manual Deploy ← Deploy latest commit**.
16. بعد النشر انسخ رابط الخدمة (مثل `https://sand-api.onrender.com`) وتأكد:
    - `https://sand-api.onrender.com/api/v1/health` ← `{"ok":true,…}`
    - `https://sand-api.onrender.com/admin/`
    لو الرابط مختلف: عدّل `PUBLIC_BASE_URL` في Secret File و`render.yaml`، وفي التطبيق (الخطوة 19).

> الباقة المجانية في Render تنام بعد 15 دقيقة بلا طلبات؛ أول طلب بعدها يأخذ ~دقيقة. للإنتاج استخدم باقة Starter أو أي خادم Node دائم.
> بديل: أي استضافة Node.js 22 (VPS أو cPanel ← Setup Node.js App): Application root = `backend`، Startup file = `dist/index.js`، ثم `npm install && npm run build`، وضع `.env` داخل `backend`.

## الجزء 6 — تطبيق Android
17. Android Studio ← **Open** ← اختر مجلد `apps/android` ← انتظر **Gradle Sync**.
18. الرابط الافتراضي للـ API في التطبيق: `https://sand-api.onrender.com/api/v1/`.
19. لو رابطك مختلف: افتح `apps/android/gradle.properties` وأضف:
    ```
    LG_API_BASE_URL=https://رابطك/api/v1/
    LG_API_BASE_URL_DEBUG=https://رابطك/api/v1/
    ```
    للتجربة على **المحاكي** مع الـ API على جهازك (الجزء 4): `LG_API_BASE_URL_DEBUG=http://10.0.2.2:4000/api/v1/`
    وعلى **موبايل حقيقي** على نفس الشبكة: `http://<IP جهازك>:4000/api/v1/` (يتطلب السماح بـ cleartext في نسخة debug لهذا العنوان).
20. وصّل الموبايل (USB debugging مفعّل) ← **Run ▶**.
21. ملف APK: **Build ← Build App Bundle(s)/APK(s) ← Build APK(s)** ← `app/build/outputs/apk/debug/`.
    أو GitHub ← **Actions ← Build Android APK ← Run workflow** (أدخل رابط الـ API) ← Artifacts.

اسم التطبيق: **سند**، الـ Package: `com.eid.sand`.

## الاستخدام اليومي
| المهمة | الطريقة |
|---|---|
| تعديل الكود | `git push` ← Render ينشر تلقائيًا |
| قانون Word/TXT/JSON | ارفعه من اللوحة ← يُنشر فورًا |
| قانون PDF | ارفعه من اللوحة (قائمة انتظار) ← على جهازك: `cd backend && npm run process-queue` (يحتاج Python وأدوات `backend/tools/ingest`) |
| ملفات جديدة في `content/` | `cd backend && npm run setup` |
| نسخة احتياطية | اللوحة ← النظام ← «نسخة احتياطية كاملة الآن» (+ Supabase ← Database ← Backups) |
| فوري | Webhook: `https://sand-api.onrender.com/api/v1/webhooks/fawry` + `FAWRY_MERCHANT_CODE`/`FAWRY_SECURE_KEY` في Secret File |

## حدود الباقة المجانية في Supabase
- قاعدة البيانات 500MB (المكتبة كاملة مع المتجهات ≈ 100–150MB)، التخزين 1GB، حجم الملف حتى 50MB.
- المشروع المجاني يتوقف (Pause) بعد أسبوع بلا نشاط — يُستأنف من لوحة Supabase.

## الأمان — مهم
- المفتاح السري `sb_secret_…` ومفاتيح الذكاء الاصطناعي أُرسلت في المحادثة. بعد التأكد أن كل شيء يعمل:
  Supabase ← Project Settings ← API Keys ← أنشئ Secret key جديدًا واحذف القديم، وحدّث `.env` وSecret File في Render.
- لا تضع `SUPABASE_SECRET_KEY` في التطبيق أو أي صفحة ويب أبدًا.

## حل المشاكل
| الرسالة | الحل |
|---|---|
| `429 … quota` في `npm run embed` أو «بحث الإنترنت» | حد الباقة المجانية لـ Gemini — انتظر أو فعّل الفوترة؛ المحادثة تتحول تلقائيًا لـ Groq/OpenRouter |
| OpenAI: `You have no credits remaining` | اشحن رصيد OpenAI أو امسح `OPENAI_API_KEY` ليتخطاه الترتيب |
| `جدول قاعدة البيانات غير موجود` | لم يُشغَّل `supabase/schema.sql` — الخطوة 2 |
| `SUPABASE_URL و SUPABASE_SECRET_KEY غير موجودين` | `backend/.env` ناقص أو غير موجود بجانب `package.json` |
| `Invalid API key` / `401` | المفتاح السري خاطئ أو تم تدويره — انسخه من جديد |
| `storage upload: Bucket not found` | شغّل `npm run setup` (ينشئ `sand-files`) أو أعد تشغيل `schema.sql` |
| التطبيق: «تعذر الاتصال» | افتح `<الرابط>/api/v1/health` من متصفح الموبايل؛ تأكد من `LG_API_BASE_URL` |
