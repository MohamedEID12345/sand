# سند (Sand) — Backend API + لوحة التحكم

Node 22 / TypeScript / Express. البيانات في **Supabase Postgres** (`src/supabaseDb.ts`: شجرة مسارات بواجهة `ref().get/set/update/transaction` مخزّنة في جدول `lg_nodes` مع تحقق إصدار ذري) والملفات في **Supabase Storage** (`src/files.ts`) بروابط تحميل موقّعة مؤقتة.
الذكاء الاصطناعي: Gemini/OpenAI/Groq/OpenRouter. الدفع فوري. يخدم تطبيق Android (`/api/v1/*`) ولوحة المشرفين (`/admin`) وبوابة المكاتب (`/office`).

## الأوامر
| الأمر | الوظيفة |
|---|---|
| `npm run test:supabase` | فحص الاتصال بـ Supabase (قاعدة البيانات + التخزين) |
| `npm run setup` | رفع `content/` لقاعدة البيانات + إنشاء حساب المالك + إنشاء مساحة الملفات |
| `npm run embed` | بناء متجهات البحث الدلالي الناقصة (Gemini) — قابل للاستئناف |
| `npm run process-queue` | معالجة ملفات PDF المرفوعة من اللوحة بخط `tools/ingest` (Python) ونشرها |
| `npm run dev` / `npm start` | تشغيل الـ API (المنفذ `PORT` أو 4000) |
| `npm run test:smoke` | اختبار شامل لكل المسارات (محاكاة في الذاكرة؛ أو `SMOKE_DB=supabase` على Supabase حقيقي) |

الإعدادات: `backend/.env` (انظر `.env.example`)، أو Secret File باسم `.env` على Render (`/etc/secrets/.env`).
خطوات التشغيل الكاملة: `docs/SETUP_AR.md`.

## الوحدات
| المسار | المحتوى |
|---|---|
| `routes/auth.ts` | تسجيل/دخول/تجديد التوكن (Refresh دوّار)/استعادة كلمة المرور؛ الانضمام لمكتب بدعوة |
| `routes/me.ts` | bootstrap، الإحصائيات، الملف الشخصي، المحفظة، الاشتراك |
| `routes/library.ts` + `services/library.ts` | التصنيفات، البحث (مادة 147 مدني / كلمات / دلالي)، عرض القانون مقسّمًا والمادة، المستجدات |
| `routes/ai.ts` + `services/assistant.ts` | المحادثات وأوضاعها، الاسترجاع الهجين، مدقق الاستشهادات (يحذف أي مصدر غير مسترجع)، الخصم بعد النجاح فقط |
| `routes/contracts.ts` + `services/templates.ts` | النماذج (115)، الملء والتحقق، ملاحظات مراجعة AI مستندة للمكتبة، تصدير Word |
| `routes/office.ts` | القضايا، الجلسات، العملاء، الملاحظات، المستندات، المواعيد الإجرائية، تحليل القضية، الفريق |
| `routes/misc.ts` | الإشعارات، تذاكر الدعم، البحث العام |
| `routes/payments.ts` | الباقات، حزم الرصيد، الأكواد، الطلبات، فوري + webhook (توقيع + منع التكرار)، الفواتير |
| `routes/admin.ts` + `public/admin/` | لوحة التحكم: نظرة عامة، المستخدمون والرصيد والإيقاف، اعتماد التحويلات، الرد على التذاكر، حالة/توثيق القوانين وإلغاء النشر، تحرير النماذج وتفعيلها، الباقات والأكواد، إعدادات التطبيق والنماذج والتكلفة، المستجدات والإشعارات الجماعية، المشرفون وسجل العمليات |

أدوار المشرفين: `SUPER` (كل شيء)، `CONTENT` (المكتبة/النماذج/المستجدات)، `SUPPORT` (المستخدمون/التذاكر)، `FINANCE` (الطلبات/الباقات/الرصيد). كل تعديل يُسجل في `/adminAudit`.

## إضافات
- **مزودو الذكاء الاصطناعي:** `AI_PROVIDER=auto` ← Gemini ثم OpenAI ثم Groq ثم OpenRouter، مع `AI_TIMEOUT_MS` و`AI_MAX_RETRIES` (`services/gemini.ts`).
- **أولوية الإجابة:** المكتبة أولًا؛ إن لم توجد نصوص كافية يُستخدم بحث الإنترنت وتُعلَّم الإجابة بأنها غير موثّقة مع روابطها.
- **حساب المالك:** `services/seed.ts` ينشئه من `ADMIN_EMAIL/ADMIN_PASSWORD` (SUPER + استخدام مجاني + باقة غير محدودة).
- **الرفع من اللوحة:** `POST /admin/uploads/law` (PDF → قائمة انتظار تُعالج على جهازك بـ `npm run process-queue`؛ Word/TXT/JSON → نشر فوري) و`POST /admin/uploads/template`.
- **النسخ الاحتياطي:** `POST /admin/system/backup` يصدّر قاعدة البيانات (عدا متجهات البحث) إلى Storage ‏`backups/*.json.gz` ويعيد رابط تحميل.
- **المكتبة:** تُقرأ من RTDB مرة لكل إصدار وتُحفظ نسخة مضغوطة في Storage (`cache/library-v*.json.gz`) يحمّلها كل تشغيل جديد للدالة.
- **نماذج المكتب:** `routes/officeTemplates.ts` + بوابة `/office`؛ تظهر في التطبيق ضمن «نماذج مكتبي» (معرّفات `o_…`).

## ملاحظات قبل الإطلاق
- راجع معادلتي توقيع فوري (`payments.ts`) مع دليل التكامل الحالي، وسجّل رابط الـ webhook.
- حدّد معرّفات نماذج Gemini من لوحة التحكم (`config/ai.models`)؛ الافتراضي `gemini-2.5-flash` / `gemini-2.5-pro` / `gemini-embedding-001`.
- القيم الافتراضية للتكلفة في `services/config.ts` (`DEFAULT_OPERATIONS`) وتُعدّل من اللوحة.
