import { get } from '../db';

const GOVERNORATES = ['القاهرة', 'الجيزة', 'الإسكندرية', 'الدقهلية', 'البحر الأحمر', 'البحيرة', 'الفيوم', 'الغربية', 'الإسماعيلية',
  'المنوفية', 'المنيا', 'القليوبية', 'الوادي الجديد', 'السويس', 'أسوان', 'أسيوط', 'بني سويف', 'بورسعيد', 'دمياط', 'الشرقية',
  'جنوب سيناء', 'كفر الشيخ', 'مطروح', 'الأقصر', 'قنا', 'شمال سيناء', 'سوهاج'];
export const DEFAULT_APP = {
  platformName: 'سند', maintenanceMode: false, maintenanceMessage: '', registrationOpen: true,
  minAndroidVersionCode: 1, updateUrl: '', supportWhatsapp: '', supportEmail: '', supportPhone: '', termsUrl: '', privacyUrl: '', websiteUrl: '',
  aiDisclaimer: 'إجابات المساعد استرشادية ومبنية على مصادر المكتبة؛ راجع النصوص الرسمية قبل الاعتماد عليها.',
  freeServiceUses: 3, freeCredits: 20, maxUploadMb: 15, governorates: GOVERNORATES,
  degrees: [{ value: 'ABTEDAI', label: 'ابتدائي' }, { value: 'ISTEANAF', label: 'استئناف' }, { value: 'NAQD', label: 'نقض' }, { value: 'TRAINEE', label: 'تحت التمرين' }],
  features: { ai: true, 'ai.QUESTION': true, 'ai.RESEARCH': true, 'ai.DEEP_RESEARCH': true, 'ai.MEMO': true, 'ai.DEBATE': true, 'ai.COMPARE': true,
    'ai.DOCUMENT': true, contracts: true, case_analysis: true, payments: true } as Record<string, boolean>,
};
export const DEFAULT_OPERATIONS: Record<string, { label: string; credits: number; usesFreeUse: boolean }> = {
  QUESTION: { label: 'سؤال قانوني', credits: 1, usesFreeUse: true }, RESEARCH: { label: 'بحث قانوني', credits: 3, usesFreeUse: true },
  DEEP_RESEARCH: { label: 'بحث معمق', credits: 6, usesFreeUse: false }, MEMO: { label: 'مذكرة قانونية', credits: 4, usesFreeUse: false },
  DEBATE: { label: 'مناظرة', credits: 4, usesFreeUse: false }, DEBATE_ANALYSIS: { label: 'تحليل مناظرة', credits: 2, usesFreeUse: false },
  COMPARE: { label: 'مقارنة نصوص', credits: 3, usesFreeUse: false }, DOCUMENT: { label: 'تحليل مستند', credits: 4, usesFreeUse: false },
  CONTRACT_GENERATE: { label: 'توليد عقد/صحيفة', credits: 1, usesFreeUse: true }, CASE_SUMMARY: { label: 'ملخص قضية', credits: 3, usesFreeUse: false },
  CASE_DEFENSE: { label: 'خطة دفاع', credits: 4, usesFreeUse: false },
};

let cache: { at: number; app: any; ai: any } = { at: 0, app: null, ai: null };
export function invalidateConfig() { cache.at = 0; }
async function load() {
  if (Date.now() - cache.at < 60_000 && cache.app) return cache;
  const [app, ai] = await Promise.all([get<any>('config/app'), get<any>('config/ai')]);
  cache = { at: Date.now(), app: { ...DEFAULT_APP, ...(app || {}), features: { ...DEFAULT_APP.features, ...(app?.features || {}) } },
    ai: { models: ai?.models || {}, operations: { ...DEFAULT_OPERATIONS, ...(ai?.operations || {}) } } };
  return cache;
}
export const appConfig = async () => (await load()).app;
export const aiConfig = async () => (await load()).ai as { models: Record<string, any>; operations: Record<string, any> };
