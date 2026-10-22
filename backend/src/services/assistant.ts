import { generate, generateFull } from './gemini';
import { retrieve, Retrieved } from './library';

export const MODES: Record<string, { op: string; slot: 'fast' | 'pro'; k: number; instruction: string }> = {
  QUESTION: { op: 'QUESTION', slot: 'fast', k: 8, instruction: 'أجب عن سؤال المحامي إجابة مباشرة ومختصرة ثم اذكر السند.' },
  RESEARCH: { op: 'RESEARCH', slot: 'pro', k: 14, instruction: 'قدّم بحثًا قانونيًا منظمًا: المسألة، النصوص الحاكمة، التحليل، الخلاصة.' },
  DEEP_RESEARCH: { op: 'DEEP_RESEARCH', slot: 'pro', k: 20, instruction: 'بحث معمق: كل النصوص ذات الصلة، التعديلات، التعارضات المحتملة، والخلاصة مع درجة الثقة.' },
  MEMO: { op: 'MEMO', slot: 'pro', k: 12, instruction: 'اكتب مذكرة قانونية بالصياغة المصرية المعتادة (الوقائع، الدفاع، الطلبات) مستندة للنصوص المسترجعة فقط.' },
  DEBATE: { op: 'DEBATE', slot: 'pro', k: 12, instruction: 'اعرض حجج المدعي ثم حجج المدعى عليه بإنصاف، ثم نقاط القوة والضعف لكل طرف.' },
  COMPARE: { op: 'COMPARE', slot: 'pro', k: 14, instruction: 'قارن بين النصوص أو القوانين المسؤول عنها في جدول ثم خلاصة الفروق وأيها الواجب التطبيق.' },
  DOCUMENT: { op: 'DOCUMENT', slot: 'pro', k: 10, instruction: 'حلّل المستند المرفق: ملخص، المخاطر، المخالفات المحتملة للنصوص المسترجعة، والتوصيات.' },
  DEBATE_ANALYSIS: { op: 'DEBATE_ANALYSIS', slot: 'pro', k: 12, instruction: 'حلّل المناظرة السابقة في المحادثة وحدد الطرف الأرجح قانونًا ولماذا.' },
};
const SYSTEM = `أنت "سند"، مساعد قانوني للمحامين في مصر.
قواعد صارمة:
- اعتمد فقط على المصادر المرقمة [S1]، [S2]… المرفقة بالرسالة. لا تذكر أي رقم مادة أو قانون أو حكم غير موجود فيها.
- ضع إشارة المصدر بعد كل حكم قانوني بالصيغة [[S3]].
- إذا لم تكفِ المصادر فقل ذلك صراحة واقترح ما يلزم البحث عنه، دون اختلاق.
- نبّه إذا كان المصدر ملغى أو معدّلًا أو غير موثّق، وإذا كان نصًا مجمّعًا فاذكر وجوب مراجعة آخر تعديل.
- اكتب بالعربية الفصحى المهنية، بفقرات قصيرة وعناوين عند الحاجة (Markdown).`;
export const sourcesBlock = (src: Retrieved[]) =>
  src.map((s) => `[S${s.n}] ${s.title}${s.status ? ` — الحالة: ${s.status}` : ''}${s.verified ? '' : ' — (نص غير موثّق)'}\n${s.text}`).join('\n\n');

const WEB_SYSTEM = `أنت "سند"، مساعد قانوني للمحامين في مصر.
لم تجد مكتبة التطبيق نصوصًا كافية، فاستخدم البحث على الإنترنت مع إعطاء الأولوية للمصادر الرسمية المصرية
(الجريدة الرسمية، بوابة التشريعات، محكمة النقض، مجلس الدولة، الوزارات) ثم المصادر القانونية الموثوقة.
- اذكر رقم القانون والمادة كما وردا في المصدر فقط، ولا تختلق نصوصًا.
- ابدأ الإجابة بسطر: "⚠️ هذه الإجابة من بحث على الإنترنت وليست من مكتبة التطبيق الموثقة — راجع المصدر الرسمي."
- اكتب بالعربية الفصحى المهنية وبتنسيق Markdown.`;

/** Library first; when the library has no relevant text, a web-grounded answer (marked unverified) is returned instead. */
export async function answer(mode: string, question: string, history: { role: 'user' | 'model'; text: string }[], extra = '', allowWeb = true) {
  const m = MODES[mode] || MODES.QUESTION;
  const src = await retrieve(`${question} ${extra}`.slice(0, 2000), m.k);
  const strong = src.filter((s) => s.score >= MIN_SCORE);
  if (!strong.length && allowWeb) {
    const r = await generateFull({ slot: m.slot, system: WEB_SYSTEM, history, web: true,
      prompt: `${m.instruction}\n\n${extra ? `معلومات إضافية:\n${extra}\n\n` : ''}السؤال:\n${question}` });
    const seen = new Set<string>();
    const citations = r.web.filter((w) => !seen.has(w.url) && seen.add(w.url)).slice(0, 8).map((w, i) => ({ id: `w${i + 1}`, sourceType: null, sourceId: null,
      title: w.title, law: null, article: null, page: null, judgment: null, url: w.url, verified: false, excerpt: null }));
    const note = r.webUnavailable ? '⚠️ تعذّر البحث على الإنترنت الآن؛ هذه إجابة من المعرفة العامة للنموذج وليست من مكتبة التطبيق — تحقّق من النص الرسمي.\n\n' : '';
    return { content: note + r.text.trim(), citations, fromWeb: true };
  }
  const prompt = `${m.instruction}\n\n${extra ? `معلومات إضافية من المحامي/المستند:\n${extra}\n\n` : ''}السؤال:\n${question}\n\nالمصادر:\n${sourcesBlock(src)}`;
  return { ...withCitations(await generate({ slot: m.slot, system: SYSTEM, history, prompt }), src), fromWeb: false };
}
const MIN_SCORE = 1.5;
/** Keeps only citations that point at retrieved sources; markers become [n]. */
export function withCitations(raw: string, src: Retrieved[]) {
  const used = new Set<number>();
  const content = raw.replace(/\[\[S(\d+)\]\]/g, (_m, n) => { const i = Number(n); if (!src.find((s) => s.n === i)) return ''; used.add(i); return `[${i}]`; });
  const citations = src.filter((s) => used.has(s.n)).map((s) => ({ id: `c${s.n}`, sourceType: s.sourceType, sourceId: s.sourceId, title: s.title, law: s.law,
    article: s.article || null, page: s.page || null, judgment: null, url: null, verified: s.verified, excerpt: s.text.slice(0, 300) }));
  return { content: content.trim(), citations };
}
