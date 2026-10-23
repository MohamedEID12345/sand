# سند (Sand) — Android

- `applicationId com.eid.sand` ، الـ namespace في الكود `com.legalguide.app` — minSdk 24 / target 36 — Java 17، ViewBinding، Material 3، Retrofit/OkHttp/Gson، Markwon، WorkManager.
- عنوان الـ API: `LG_API_BASE_URL_DEBUG` / `LG_API_BASE_URL` (الافتراضي للنسختين: `https://sand-api.vercel.app/api/v1/`)؛ التطبيق لا يتصل بـ Supabase مباشرة ولا يحمل أي مفاتيح.
- واجهة عربية RTL بالكامل (`locales_config` = ar).
- البناء: `./gradlew assembleDebug` — الاختبارات: `./gradlew testDebugUnitTest` (17 اختبار).
- عقد المكتبة مع الـ backend: `docs/API_LIBRARY.md`.
