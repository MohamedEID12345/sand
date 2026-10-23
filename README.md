# سند — Sand

منصة SaaS للمحامين المصريين: مساعد ذكاء اصطناعي مستند إلى مكتبة قانونية، صياغة العقود والصحف، القضايا والجلسات والعملاء والفريق،
محفظة رصيد واشتراكات عبر فوري، ولوحة تحكم للمشرفين وبوابة للمكاتب.

| المسار | المحتوى |
|---|---|
| `apps/android/` | تطبيق Android (Java) — `com.eid.sand` — عميل REST للـ API |
| `backend/` | API (Node 22/TS/Express) + لوحة التحكم `/admin` وبوابة `/office` — البيانات في Supabase |
| `supabase/schema.sql` | مخطط قاعدة البيانات (يُشغَّل مرة في SQL Editor) |
| `backend/vercel.json` | نشر الـ API على Vercel (Root Directory = `backend`) |
| `backend/tools/ingest/` | تحويل ملفات القوانين PDF (فك الخطوط، OCR، تقسيم المواد) |
| `content/` | 103 تشريعًا منشورًا + 115 نموذجًا |
| `docs/SETUP_AR.md` | **التشغيل الكامل خطوة بخطوة** |

```
Android ──HTTPS/JWT──▶ API (Vercel/جهازك) ──secret key──▶ Supabase Postgres + Storage
                                          └──────────────▶ Gemini/OpenAI/Groq/OpenRouter، فوري
```

## باختصار
1. Supabase ← SQL Editor ← شغّل `supabase/schema.sql`.
2. `cd backend && npm install && npm run test:supabase && npm run setup`
3. `npm run dev` ← http://localhost:4000/admin/ (أو انشر على Vercel — `docs/SETUP_AR.md` الجزء 5).
4. افتح `apps/android` في Android Studio ← Run.
