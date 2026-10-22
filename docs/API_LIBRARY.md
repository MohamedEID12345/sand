# Library API (contract between the backend and the Android app)

All responses use the common envelopes: lists are `Page<T> { items, nextCursor, total }`, errors are
`{ "error": { "code", "message", "details": { "fields": {…} } } }`. Arabic text is returned as stored
(display form); matching is done on `searchKey` (no tashkeel, unified alef/yaa/taa marbuta, ASCII digits).
Data comes from `/library/*` in RTDB (see `DATABASE.md`); only **published** items exist there.

## GET /library/categories
`Page<LibraryCategory>` → `{ id, name, count }`, ordered by `sort`. `count` = published items in the category.

## GET /library/search?q=&type=&categoryId=&cursor=
`Page<LibraryItem>`; page size 20; `type` ∈ `LAW | ARTICLE | JUDGMENT | PRINCIPLE | BOOK` (empty = all).

| type | id | title | subtitle | snippet | status |
|---|---|---|---|---|---|
| LAW | `law-131-1948` | `القانون المدني` | `القانون رقم 131 لسنة 1948 …` | — | law status or null |
| ARTICLE | `law-131-1948:147` | `مادة 147 — القانون المدني` | article `heading` (الكتاب/الباب/الفصل) | ~220 chars around the best match | the law's status |

Ranking: exact law number/year (`131 1948`, `131/1948`) and article references (`مادة 147 مدني`) first, then
keyword matches on article text, then semantic matches when embeddings exist. `categoryName` and
`effectiveDate` (= law `issueDate`) are filled for both types. An empty `q` with a `categoryId` lists the
category's laws by year.

## GET /library/source?type=LAW&id={lawId}&page={n}
`SourceView` for a whole law, paged by **30 articles** (`page` 1-based, `pageCount` = ceil(articles/30)).
- `title`, `subtitle` from the item; `status` (null → no badge); `verified` = source text verified
  (`TEXT_LAYER`/`TRANSCRIBED`).
- `meta` (KeyValue): رقم القانون، السنة، الجريدة الرسمية، تاريخ العمل، الحالة + ملاحظة الحالة، جودة النص، عدد المواد،
  ملاحظات المصدر.
- `body` (Markdown) for that page: on page 1 the preamble first, then for each article
  `#### {label}` + optional `*{heading}*` (only when it changes) + text; amendment notes as `> …`; the
  closing/signature after the last article.
- `sections`: the articles on this page as `LibraryItem { id: "{lawId}:{key}", type: "ARTICLE", title: label }`
  (tapping opens the article).
- `related`: laws in `amends` / `amendedBy` as `LibraryItem { type: "LAW" }`.

## GET /library/source?type=ARTICLE&id={lawId}:{key}
- `title` = `{label} — {law title}`, `subtitle` = law subtitle, `body` = article text (Markdown, paragraphs kept),
  `status` = law status, `verified` as above.
- `meta`: القانون، الموضع (heading)، حاشية التعديل (amendmentNote)، الصفحة في المصدر.
- `related`: previous and next article of the same law (`ARTICLE`), then amending/amended laws (`LAW`).
- `page`/`pageCount` null.

## Citations produced by the AI
`Citation { sourceType: "ARTICLE", sourceId: "{lawId}:{key}", title, law: "{law title} رقم {n} لسنة {y}",
article: "{label}", excerpt, verified }`. Before an answer is returned the backend checks every cited
`sourceId` exists under `/library/articles/{lawId}/{key}`; unknown citations are dropped or returned with
`verified: false` (the app shows them as unverified). Only published items are retrievable, so OCR drafts can
never be cited.

## Errors
`404 NOT_FOUND` (unknown id), `400 VALIDATION_ERROR` (bad type/page), `401/403` per auth rules. Library read
endpoints require a signed-in user; they do not consume credits.
