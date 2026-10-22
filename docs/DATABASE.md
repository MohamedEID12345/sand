> **التخزين الحالي: Supabase.** الشجرة الموصوفة هنا مخزّنة في جدول Postgres واحد `public.lg_nodes(path, value jsonb, ver)` — كل صف = مستند على مسار ثابت العمق لكل جذر (`users/<uid>`، `library/articles/<lawId>`، `officeData/<office>/<kind>/<id>` …). التحويل في `backend/src/supabaseDb.ts`، والمخطط في `supabase/schema.sql`. الملفات في Supabase Storage (`sand-files`). الوصول من الـ API فقط بالمفتاح السري؛ RLS مفعّل بدون سياسات.

# قاعدة البيانات — شجرة المسارات (مخزّنة في Supabase Postgres)


| | |
|---|---|
| Project | `https://yjqmqxpfgcbvxdbwddyf.supabase.co` |
| Table | `public.lg_nodes (path text collate "C" primary key, value jsonb, ver bigint, updated_at)` |
| Writes | `public.lg_apply(ops jsonb)` — atomic batch (put / del / deltree / cas with version check) |
| Files | Storage bucket `sand-files` (private, signed URLs) |
| Who connects | **Backend only** (`SUPABASE_SECRET_KEY`); RLS on with no policies → the publishable key gets nothing |
| Schema | `supabase/schema.sql` (run once in SQL Editor) |

## Why the Android app does not connect to the database

The app is a thin client over the REST API (`docs/API_CONTRACT.md`). Credits, free uses, subscriptions,
payments, roles and admin settings all live in this database. If the app could write to it directly,
anyone could decompile the APK and give themselves credits or mark an order as paid. So:

```
Android app ──HTTPS/JWT──▶ Node/TS backend ──secret key──▶ Supabase Postgres (lg_nodes)
                                          └─────────────▶ Supabase Storage, Gemini API, Fawry
```

## How the tree maps to rows

Each row is one document at a fixed depth per root (default 2): `users/<uid>`, `wallets/<uid>`,
depth 3 for `library/*/<id>`, `userIndex/email/<key>`, `notifications/<uid>/<id>`, `tickets/<uid>/<id>` …,
depth 4 for `officeData/<office>/<kind>/<id>` (map in `backend/src/supabaseDb.ts`).
Reading above that depth assembles rows (prefix range query); writing below it patches the row JSON
with an optimistic version check (`cas`), so multi-path updates and wallet/order transactions stay atomic.

## Data tree

All IDs are backend-generated push IDs unless stated. Timestamps are ISO-8601 UTC strings.
Tenant data is scoped by `officeId` (a solo lawyer gets a personal office on registration).

```
/config
  /app                      AppConfig (maintenance, min version, support links, free trial numbers…)
  /features                 { "ai": true, "ai.question": true, …, "contracts": true, "case_analysis": true }
  /ai/models                model registry (see CONTENT_IMPORT.md)
  /ai/operations/{OP}       { model, credits, usesFreeService, dailyLimit, enabled, label }
  /payment                  { provider: "FAWRY", methods: {...}, bank: BankDetails }
  /caseMeta                 statuses, priorities, documentTypes, hearingStatuses, deadlineEvents, analysisTypes
  /roles/{ROLE}             { label, permissions: [ "cases.create", … ] }   ROLE ∈ OWNER…ACCOUNTANT
  /caseTypes/{id}           { name, fields: [FormField], active, sort }

/users/{uid}                { name, email, phone, governorate, status, officeId, officeRole,
                              professional{syndicateNumber,degree,specialization}, createdAt }
/index/usersByEmail/{sha256(email)}  uid
/index/usersByPhone/{phone}          uid
/private/credentials/{uid}           { passwordHash (argon2id), updatedAt }
/private/sessions/{uid}/{sessionId}  { refreshHash, deviceId, deviceName, createdAt, lastUsedAt, revokedAt }
/private/passwordResets/{uid}        { codeHash, expiresAt, attempts }

/offices/{officeId}                  { name, ownerId, seatsMax, createdAt }
/officeMembers/{officeId}/{uid}      { role, status: ACTIVE|INVITED|REMOVED, joinedAt }
/invitations/{officeId}/{inviteId}   { email, role, tokenHash, expiresAt, status }

/wallets/{uid}                       { totalCredits, usedCredits, bonusCredits, purchasedCredits,
                                       freeCredits, freeServiceUses }        ← changed ONLY in transactions
/creditLedger/{uid}/{txId}           { type, amount, balanceAfter, operation, refType, refId,
                                       description, idempotencyKey, createdAt }
/aiUsage/{uid}/{yyyy-mm-dd}          { count, credits }                      ← daily limits

/plans/{planId}                      Plan (admin)
/creditPacks/{packId}                CreditPack (admin)
/promoCodes/{CODE}                   { discountType, discountValue, bonusCredits, maxUses, usedCount,
                                       validFrom, validTo, appliesTo, active }
/promoRedemptions/{CODE}/{uid}       { orderId, at }
/subscriptions/{uid}                 { planId, status, startAt, endAt }
/orders/{orderId}                    { uid, itemType, itemId, itemName, amount, discount, total, currency,
                                       status, provider, providerRef, referenceNumber, paymentMethod,
                                       promoCode, createdAt, paidAt, failureReason, creditsGranted,
                                       bankProof{fileKey,note,uploadedAt} }
/ordersByUser/{uid}/{orderId}        createdAt
/paymentEvents/{providerEventId}     { orderId, status, receivedAt, processedAt }   ← webhook idempotency
/invoices/{uid}/{invoiceId}          Invoice

/clients/{officeId}/{clientId}       Client (+ createdBy, updatedAt)
/cases/{officeId}/{caseId}           LegalCase (+ createdBy, updatedAt, nextHearingAt)
/hearings/{officeId}/{hearingId}     Hearing (+ caseId)
/caseDocuments/{officeId}/{caseId}/{docId}   CaseDocument (+ fileKey in Storage, extractedText ref)
/caseNotes/{officeId}/{caseId}/{noteId}
/caseAnalyses/{officeId}/{caseId}/{analysisId}
/deadlineRules/{ruleId}              { event, label, days, dayType: CALENDAR|WORKING, legalSource, notes, active }
/contracts/{officeId}/{contractId}   Contract (+ values, templateVersion)

/aiChats/{uid}/{chatId}              AiChat
/aiMessages/{chatId}/{messageId}     AiMessage (+ clientMessageId for idempotent retries)

/library/categories/{id}             { name, sort }
/library/items/{id}                  LAW:  { type:"LAW", instrument: LAW|DECREE_LAW|DECREE, number, year, title, subtitle,
                                             categoryId, status: IN_FORCE|AMENDED|REPEALED|null, statusNote,
                                             amends:[lawId], amendedBy:[lawId], gazette, issueDate,
                                             textQuality: TEXT_LAYER|TRANSCRIBED|OCR, verified, articleCount,
                                             notes, sourceFile, searchKey, updatedAt }
                                     BOOK/JUDGMENT/PRINCIPLE: { type, title, subtitle, categoryId, pageCount,
                                             fileKey, license, verified, searchKey, updatedAt }
/library/articles/{lawId}/{key}      { order, part: "issuance"|"main", number, suffix, label, heading,
                                       text, page, amendmentNote }
                                     key = "147", "21-مكرر", issuance articles "i1", "i2"…
/library/pages/{itemId}/{page}       plain text of a book/judgment page (reader + citations)
/library/chunks/{itemId}/{chunkId}   { articleKey | page, text, embedding: base64(float32[768]) }   ← RAG index
/library/index/meta                  { version, updatedAt, lawCount, articleCount, chunkCount }
/library/index/embedding             { model, dimensions, updatedAt }
/legalUpdates/{id}                   LegalUpdate
/contractTemplates/{id}              template (see CONTENT_IMPORT.md)

/notifications/{uid}/{notifId}       AppNotification
/tickets/{ticketId}                  Ticket (+ uid)
/ticketMessages/{ticketId}/{msgId}   TicketMessage
/deletionRequests/{uid}              { reason, requestedAt, status }
/auditLogs/{yyyy-mm}/{logId}         { actorId, action, target, meta, ip, createdAt }
```

## Consistency rules the backend must follow

- **Credits:** every charge/refund is one RTDB `transaction()` on `/wallets/{uid}` plus a ledger entry
  keyed by an idempotency key (`clientMessageId`, `orderId`, analysis id). Charge **after** the AI call
  succeeds; a failed AI call charges nothing.
- **Payments:** an order becomes `PAID` only from the Fawry webhook (signature verified) or the
  server-side status query in `POST /orders/{id}/verify`. The webhook writes `/paymentEvents/{id}`
  first; a duplicate event is ignored. Granting credits/subscription happens once per order
  (guarded by the order status transition inside a transaction).
- **Tenancy:** every case/client/document read checks `officeMembers/{officeId}/{uid}` and the role's
  permissions. Never trust an `officeId` sent by the client.
- **Library writes** happen only through `backend/tools/import-library.mjs` or admin endpoints; a law is
  replaced as a whole (items + articles + chunks) so partial re-imports never leave stale articles.
  Only items with `verified` sources are cited by the AI; OCR drafts are never imported (`published:false`).
- **Article ids in the API** are `{lawId}:{key}` (e.g. `law-131-1948:147`). The citation validator checks
  that every cited `{lawId}:{key}` exists under `/library/articles` before an answer is returned.
- **Search:** RTDB has no full-text search. The backend keeps an in-memory index (cases/clients per
  office, library titles) rebuilt on start and updated on writes; library RAG uses the stored
  embeddings (cosine similarity in memory, vectors decoded from base64 float32 once at startup and
  reloaded when `/library/index/meta/version` changes); without embeddings it falls back to keyword
  search over `searchKey`-normalized article text. If the library grows beyond a few hundred thousand chunks,
  move vectors to a dedicated vector store without changing the API.


## إضافات الـ backend (الإدارة والتشغيل)
```
/admins/{uid}                         { role: SUPER|CONTENT|SUPPORT|FINANCE, grantedBy, at }
/adminAudit/{id}                      { uid, role, action, target, data, at }
/officeInvitations/{id}               { officeId, email, role, invitedBy, status: PENDING|ACCEPTED, createdAt }
/supportQueue/{ticketId}              { uid, subject, category, status, updatedAt }     ← فهرس تذاكر الدعم للوحة
/catalog/plans|creditPacks/{id}       { name, price, currency, credits, bonusCredits, durationDays, limits, features, sort, active }
/catalog/promoCodes/{CODE}            { discountType: PERCENT|AMOUNT, discountValue, bonusCredits, maxUses, uses, expiresAt, active }
/config/app | ai | payments | caseMeta | deadlineRules
/stats/aiUsage/{yyyy-mm-dd}/{OP}      عدّاد العمليات
/stats/revenue/{yyyy-mm}/{orderId}    قيمة الطلب المدفوع
/aiIdem/{uid}/{clientMessageId}       منع تكرار إرسال رسالة المساعد
/payments/webhookEvents/{sha256}      منع تكرار معالجة إشعار فوري
/passwordResets/{uid}, /refreshTokens/{id}, /deletionRequests/{uid}
```
