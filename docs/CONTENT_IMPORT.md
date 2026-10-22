> **التخزين:** Supabase (Postgres جدول `lg_nodes` + Storage) عبر الـ API. المحتوى يُرفع بأمر `cd backend && npm run setup`.

> المحتوى في `content/` يُرفع لقاعدة البيانات بأمر `cd backend && npm run setup` من جهازك.

# إضافة المحتوى: الكتب، نماذج العقود، نماذج الذكاء الاصطناعي

كل المحتوى يُكتب في RTDB **عن طريق الـ backend** (لوحة الأدمن أو سكربت استيراد يستخدم الـ Admin SDK)،
مش من التطبيق. أمثلة جاهزة في `content/samples/`.

## 1) نماذج العقود → `/contractTemplates/{id}`

ملف JSON لكل نموذج — المثال الكامل: `content/samples/contract-template.lease-residential.json`.

| الحقل | إلزامي | الوصف |
|---|---|---|
| `id` | ✔ | معرّف ثابت بالإنجليزي (`lease-residential-civil`) |
| `name`, `category`, `categoryLabel`, `description` | ✔ | تظهر في كتالوج العقود |
| `credits` | ✔ | تكلفة الإنشاء بالرصيد (يحددها الأدمن) |
| `active`, `sort`, `version` | ✔ | الإخفاء والترتيب؛ زوّد `version` عند أي تعديل |
| `fields[]` | ✔ | نموذج البيانات الديناميكي (الجدول تحت) |
| `body` | اختياري | هيكل ثابت بمتغيرات `{{key}}` — يُملأ آليًا قبل الصياغة، فيقلل الهلوسة |
| `draftingInstructions` | ✔ | تعليمات الصياغة للنموذج (القانون الحاكم، البنود الإلزامية، الممنوعات) |
| `legalBasis[]` | مستحسن | المصادر اللي يُسمح بالاستشهاد بيها |

**أنواع الحقول** المدعومة في التطبيق: `text`، `textarea`، `number`، `money`، `date`، `select`، `multiselect`،
`checkbox`، `email`، `phone` (موبايل مصري)، `national_id` (14 رقم). خصائص الحقل: `key`، `label`، `type`،
`required`، `options[{value,label}]`، `placeholder`، `hint`، `min`، `max`، `maxLength`، `defaultValue`، `group`
(الحقول بنفس `group` تتجمع تحت عنوان واحد).

**مسار الإنشاء في الـ backend:** تحقق من القيم بنفس القواعد ← ملء `body` ← Gemini يكمل ويراجع مع RAG من
`legalBasis` ← مدقق الاستشهادات يحذف أي مادة غير موجودة في المصادر ← خصم الرصيد بعد النجاح فقط ←
حفظ في `/contracts/{officeId}/{id}` مع `reviewNotes`. التصدير Word يتم في الدالة.

**أبعتلي النماذج بأي شكل** (Word/PDF/نص) وأنا أحولها للـ JSON ده، أو املاها مباشرة على نفس الشكل.

### صحف الدعاوى والإعلانات (`content/templates/`، تصنيف `COURT_PAPERS`)
نفس الشكل مع إضافات اختيارية يدعمها الـ backend:
- `defaultValue: "............"` للحقول التي يملؤها المحضر (يوم/تاريخ الإعلان، مخاطبًا مع) فتُطبع نقاطًا لو تُركت فارغة.
- `showIf: { field, in: [...] }` لإظهار الحقل حسب اختيار آخر (التطبيق الحالي يعرضها كلها كحقول اختيارية مجمّعة).
- `blocks: { <selectKey>: { <optionValue>: "نص به {{متغيرات}}" } }` مع `{{block:<selectKey>}}` في `body` — يُستبدل بالفقرة
  المطابقة للاختيار (مثال: صيغة المعلن إليه في المادة 13 مرافعات).
- `fixedQuotes: true` النصوص القانونية المقتبسة جزء ثابت من النموذج (لا يعيد الـ AI صياغتها ولا يحذفها مدقق الاستشهادات).
- `source`: مصدر النموذج. المولّد: `content/templates/_build.py`.
القوالب تُكتب في `/contractTemplates/{id}` وتُضاف كمقطع RAG (`/library/chunks/form_{id}`) ليستعين بها الـ AI في الأسئلة.

### الكتب والأبحاث (`content/books/`)
`python3 backend/tools/ingest/books.py` ← `{meta, pages[{page,text}]}` ← الاستيراد يكتب `/library/items/{id}` (type `BOOK`)
و`/library/pages/{id}/{n}` ومقاطع RAG بالصفحة (الاستشهاد برقم الصفحة). أي كتاب أو بحث منشور لا يُنشر (`published:false`)
قبل تأكيد حق الاستخدام في `meta.license`.

## 2) الكتب والمراجع → `/library/items` + Cloud Storage

لكل كتاب: ملف PDF + ملف بيانات (`content/samples/book.example.json`).

- `license` إلزامي: `OWNED` / `PERMISSION` / `PUBLIC_DOMAIN` — ارفع بس الكتب اللي عندك حق نشرها
  على المنصة، لأن المستخدم بيقرأ صفحاتها ويتم الاستشهاد منها.
- الـ PDF لازم يكون نصي (مش صور)، أو هيمر على OCR في الـ backend (أبطأ وأقل دقة في العربي).
- `firstContentPage` لتجاهل الغلاف والفهرس في الفهرسة.

**مسار الاستيراد:** رفع PDF إلى Storage (`books/…`) ← استخراج نص كل صفحة إلى `/library/pages/{id}/{n}` ←
تقسيم إلى مقاطع (~800–1200 حرف مع تداخل) ← embeddings بنموذج الـ embedding المحدد ←
`/library/chunks/{id}/{chunkId}` ← يظهر في المكتبة والبحث والـ RAG، والاستشهاد يرجع لرقم الصفحة.

القوانين والأحكام لها شكل خاص بالمواد (`content/samples/law.example.json`) — كل مادة سجل مستقل عشان
الاستشهاد يكون بـ "المادة X من القانون Y" بحالتها (سارية/معدلة/ملغاة).

### خط القوانين (مستخدم فعليًا في الدفعة الأولى)
1. ضع ملفات PDF في `backend/tools/ingest/src/` وسجّل كل تشريع في `backend/tools/ingest/registry.py`.
2. `python3 backend/tools/ingest/ingest.py` ← ينتج `content/laws/<id>.json` + `index.json` + `REVIEW.md`
   (تفاصيل المراحل وقاعدة الجودة في `backend/tools/ingest/README.md`).
3. راجع `content/laws/REVIEW.md` (المواد الناقصة، الحالات المقترحة، الملفات التي تحتاج نسخة أفضل).
4. الاستيراد:
   ```bash
   cd backend/tools && npm i
   node import-library.mjs --dry-run            # يعرض العدد والحجم بدون كتابة
   node import-library.mjs                      # يكتب /library/* في Realtime Database
   node import-library.mjs --embed              # + embeddings للبحث الدلالي (يحتاج GEMINI_API_KEY)
   node import-library.mjs --only law-14-2025   # تشريع واحد
   ```
   المتغيرات من `backend/.env`: `FIREBASE_DATABASE_URL`، `FIREBASE_SERVICE_ACCOUNT_BASE64` (أو
   `GOOGLE_APPLICATION_CREDENTIALS`)، `GEMINI_API_KEY`، `EMBEDDING_MODEL`، `EMBEDDING_DIMENSIONS`.

**قاعدة الجودة:** نص رقمي سليم أو تفريغ يدوي ← منشور وموثّق. OCR آلي ← مسودة غير منشورة لا يستشهد بها
الـ AI. الحالة (سارٍ/معدّل/ملغى) لا تُكتب إلا إذا كانت مؤكدة؛ غير ذلك تظهر كـ"مقترحة" في `REVIEW.md` لاعتماد الأدمن.

**أفضل مصدر للكتب والقوانين القادمة:** Word أو PDF نصي (يمكن تحديد النص فيه). الـ PDF المصوّر يمر بالـ OCR
ويحتاج مراجعة بشرية قبل النشر.

## 3) نماذج الذكاء الاصطناعي → `/config/ai`

المثال: `content/samples/ai-config.example.json`.

- `models`: ثلاث خانات منطقية `fast` / `pro` / `embedding`. اكتب معرّفات Gemini الفعلية من Google AI Studio
  وقت الإعداد. تغيير الموديل من الأدمن لا يحتاج تحديث التطبيق.
- `operations`: لكل عملية الموديل والتكلفة وهل تستهلك الاستخدامات المجانية والحد اليومي والتفعيل.
  أكواد العمليات ثابتة ويستخدمها التطبيق: `QUESTION`، `RESEARCH`، `DEEP_RESEARCH`، `MEMO`، `DEBATE`،
  `DEBATE_ANALYSIS`، `COMPARE`، `DOCUMENT`، `CONTRACT_GENERATE`، وأنواع تحليل القضايا `CASE_*`
  (تُعرض في التطبيق من `GET /cases/meta → analysisTypes`).
- **تغيير الـ embedding model بعد الفهرسة يتطلب إعادة فهرسة كل المكتبة** (الأبعاد والمسافات بتختلف).
- مفتاح Gemini في `.env` داخل الدالة فقط (`GEMINI_API_KEY`).
