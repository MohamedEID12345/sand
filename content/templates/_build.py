"""Builds the court-paper templates (صحف وإعلانات) from the uploaded forms (البوابة القانونية للتشريعات المصرية).
Run: python3 _build.py  → one JSON per template (format: docs/CONTENT_IMPORT.md §1)."""
import json

DOTS = '............'
BAILIFF = [  # filled by the bailiff at service time → optional, printed as dots when empty
    dict(key='serviceDay', label='يوم الإعلان', type='text', required=False, defaultValue=DOTS, group='بيانات المحضر (تُترك للمحضر)'),
    dict(key='serviceDate', label='تاريخ الإعلان', type='date', required=False, defaultValue=DOTS, group='بيانات المحضر (تُترك للمحضر)'),
    dict(key='bailiffName', label='اسم المحضر', type='text', required=False, defaultValue=DOTS, group='بيانات المحضر (تُترك للمحضر)'),
    dict(key='servedWith', label='مخاطبًا مع', type='text', required=False, defaultValue=DOTS, group='بيانات المحضر (تُترك للمحضر)'),
]
PARTIES = [
    dict(key='requesterName', label='اسم الطالب', type='text', required=True, group='الطالب', maxLength=150),
    dict(key='requesterAddress', label='عنوان الطالب', type='text', required=True, group='الطالب'),
    dict(key='lawyerName', label='اسم المحامي', type='text', required=True, group='المحامي (المحل المختار)'),
    dict(key='lawyerOfficeAddress', label='عنوان مكتب المحامي', type='text', required=True, group='المحامي (المحل المختار)'),
    dict(key='bailiffCourt', label='محكمة المحضرين (الجزئية)', type='text', required=True, group='المحضرين', placeholder='مثال: الزقازيق'),
]
RESP = [
    dict(key='respondentName', label='اسم المعلن إليه', type='text', required=True, group='المعلن إليه'),
    dict(key='respondentAddress', label='عنوان المعلن إليه', type='text', required=True, group='المعلن إليه'),
    dict(key='respondentDistrict', label='القسم / المركز', type='text', required=True, group='المعلن إليه'),
    dict(key='respondentGovernorate', label='المحافظة', type='text', required=True, group='المعلن إليه'),
]
CASE = [
    dict(key='caseNumber', label='رقم الدعوى', type='text', required=True, group='الدعوى'),
    dict(key='caseYear', label='سنة الدعوى', type='number', required=True, min=1950, max=2100, group='الدعوى'),
    dict(key='courtName', label='المحكمة', type='text', required=True, group='الدعوى'),
    dict(key='circuit', label='الدائرة', type='text', required=True, group='الدعوى'),
    dict(key='courtAddress', label='مقر المحكمة', type='text', required=True, group='الدعوى'),
    dict(key='originalRequests', label='الطلبات الواردة بختام صحيفة الدعوى', type='textarea', required=True, group='الدعوى'),
]
HEARING = [
    dict(key='hearingDay', label='يوم الجلسة', type='text', required=True, group='الجلسة', placeholder='مثال: الأحد'),
    dict(key='hearingDate', label='تاريخ الجلسة', type='date', required=True, group='الجلسة'),
]
HEAD = ('إنه في يوم {{serviceDay}} الموافق {{serviceDate}}\n'
        'بناءً على طلب السيد/ {{requesterName}} المقيم في {{requesterAddress}}\n'
        'ومحله المختار مكتب الأستاذ/ {{lawyerName}} المحامي الكائن في {{lawyerOfficeAddress}}\n'
        'أنا {{bailiffName}} محضر محكمة {{bailiffCourt}} الجزئية قد انتقلت في التاريخ المذكور أعلاه وأعلنت:\n')
RESP_LINE = ('السيد/ {{respondentName}} المقيم في {{respondentAddress}} قسم {{respondentDistrict}} محافظة {{respondentGovernorate}}\n'
             'مخاطبًا مع/ {{servedWith}}\n')
SIDE = '\n\n— الموضوع: {subject} — بناءً على طلب الطالب وتحت مسئوليته — وكيل الطالب: أ/ {{{{lawyerName}}}} المحامي'
COMMON_INSTR = ('املأ القيم كما أدخلها المستخدم حرفيًا ولا تغيّر النصوص القانونية المقتبسة داخل النموذج. '
                'الحقول الفارغة الخاصة بالمحضر تبقى نقاطًا. لا تضف مواد أو أحكامًا غير موجودة في المصادر المسترجعة؛ '
                'إن طُلبت صياغة الأسباب فاكتبها من وقائع المستخدم فقط مع الاستشهاد بالمكتبة. أرجع ملاحظات مراجعة مختصرة '
                '(مواعيد الإعلان، الاختصاص، المستندات المطلوبة).')
SOURCE = 'البوابة القانونية للتشريعات المصرية — مركز المعلومات ودعم اتخاذ القرار (elpai.idsc.gov.eg)'

T = []
T.append(dict(
    id='notice-incidental-claims', name='إعلان بالطلبات العارضة (الإضافية)', sort=10,
    description='إعلان الخصم بطلب عارض أُضيف للدعوى بعد تأجيلها لهذا الغرض (المادتان 123 و124 مرافعات).',
    legalBasis=['قانون المرافعات المدنية والتجارية — المادتان 123 و124'],
    fields=BAILIFF + PARTIES + RESP + CASE + [
        dict(key='adjournHearingDate', label='تاريخ الجلسة التي قررت التأجيل', type='date', required=True, group='الطلب العارض'),
        dict(key='incidentalRequest', label='الطلب العارض (الإضافي)', type='textarea', required=True, group='الطلب العارض'),
    ] + HEARING,
    body=HEAD + RESP_LINE +
    'وأعلنته بالآتي\n'
    'أقام الطالب ضد المعلن إليه الدعوى رقم {{caseNumber}} لسنة {{caseYear}} أمام محكمة {{courtName}} الدائرة ({{circuit}}) طالبًا في ختام صحيفة افتتاحها الحكم له بـ {{originalRequests}}.\n'
    'وبجلسة {{adjournHearingDate}} قررت المحكمة تأجيل نظر الدعوى إلى جلسة {{hearingDate}} بناءً على طلب المدعي وذلك لإضافة طلب عارض في الدعوى وهو:\n{{incidentalRequest}}\n'
    'وحيث تنص المادة ١٢٣ من قانون المرافعات على أنه:\n'
    '"تقدم الطلبات العارضة من المدعى أو من المدعى عليه إلى المحكمة بالإجراءات المعتادة لرفع الدعوى قبل يوم الجلسة أو بطلب يقدم شفاهة في الجلسة في حضور الخصم ويثبت في محضرها ولا تقبل الطلبات العارضة بعد إقفال باب المرافعة."\n'
    'وكذلك تنص المادة ١٢٤/١ من قانون المرافعات على أنه:\n'
    '"للمدعى أن يقدم من الطلبات العارضة:\n١ - ما يتضمن تصحيح الطلب الأصلي أو تعديل موضوعه لمواجهة ظروف طرأت أو تبينت بعد رفع الدعوى.\n٢ - ما يكون مكملًا للطلب الأصلي أو مترتبًا عليه أو متصلًا به اتصالًا لا يقبل التجزئة.\n'
    '٣ - ما يتضمن إضافة أو تغييرًا في سبب الدعوى مع بقاء موضوع الطلب الأصلي على حاله.\n٤ - طلب الأمر بإجراء تحفظي أو وقتي.\n'
    '٥ - ما تأذن المحكمة بتقديمه مما يكون مرتبطًا بالطلب الأصلي."\n'
    'وحيث يهم الطالب تنفيذ قرار المحكمة.\n\nبناءً عليه\n'
    'أنا المحضر سالف الذكر قد انتقلت في التاريخ المذكور أعلاه إلى حيث المعلن إليه وسلمته صورة من هذا الإعلان وكلفته بالحضور أمام محكمة {{courtName}} الكائن مقرها في {{courtAddress}} أمام الدائرة ({{circuit}}) وذلك بجلستها العلنية التي ستنعقد في تمام الساعة التاسعة وما بعدها من صباح يوم {{hearingDay}} الموافق {{hearingDate}} وذلك لسماع الحكم بـ {{incidentalRequest}} كطلب عارض (إضافي) وسماع الحكم بالطلبات الواردة بأصل صحيفة الدعوى، مع حفظ كافة الحقوق القانونية الأخرى للطالب.\n'
    'ولأجل/\nالسند القانوني: المادتان ١٢٣، ١٢٤ من قانون المرافعات.' + SIDE.format(subject='إعلان بالطلبات الإضافية (العارضة)'),
    notes='نص البند (1) من المادة 124 مأخوذ من نموذج "إعلان بتصحيح شكل الدعوى" من نفس المصدر.'))
T.append(dict(
    id='notice-case-reinstatement', name='إعلان بإعادة الدعوى إلى الرول', sort=20,
    description='إعلان المدعى عليه بإعادة الدعوى للرول بعد شطبها وحضور المدعي قبل انتهاء الجلسة (المادتان 82 و86 مرافعات).',
    legalBasis=['قانون المرافعات المدنية والتجارية — المادتان 82 و86'],
    fields=BAILIFF + PARTIES + RESP + CASE + [
        dict(key='strikeOffHearingDate', label='تاريخ الجلسة التي تقرر فيها الشطب', type='date', required=True, group='الشطب'),
    ] + HEARING,
    body=HEAD + RESP_LINE +
    'وأعلنته بالآتي\n'
    'أقام الطالب ضد المعلن إليه الدعوى رقم {{caseNumber}} لسنة {{caseYear}} أمام محكمة {{courtName}} الدائرة ({{circuit}}) طالبًا في ختام صحيفة افتتاحها الحكم له بـ {{originalRequests}}.\n'
    'وتداولت الدعوى بالجلسات وبجلسة {{strikeOffHearingDate}} تخلف المدعي عن الحضور فقررت المحكمة شطب الدعوى إعمالًا لنص المادة ٨٢ من قانون المرافعات، إلا أن المدعي حضر قبل رفع الجلسة وقدم طلبًا للمحكمة بإعادة الدعوى إلى الرول استنادًا إلى حكم المادة ٨٦ من قانون المرافعات والتي تنص على أنه:\n'
    '"إذا حضر الخصم الغائب قبل انتهاء الجلسة اعتبر كل حكم صدر عليه فيها كأن لم يكن."\n'
    'لذلك قررت المحكمة تأجيل نظر الدعوى لجلسة {{hearingDate}} وكلفت الطالب بإعلان المدعى عليه بإعادة الدعوى إلى الرول.\n'
    'وحيث يهم الطالب تنفيذ قرار المحكمة.\n\nبناءً عليه\n'
    'أنا المحضر سالف الذكر قد انتقلت في التاريخ المذكور أعلاه إلى حيث المعلن إليه وسلمته صورة من هذا الإعلان وكلفته بالحضور أمام محكمة {{courtName}} الكائن مقرها في {{courtAddress}} أمام الدائرة ({{circuit}}) وذلك بجلستها العلنية التي ستنعقد في تمام الساعة التاسعة وما بعدها من صباح يوم {{hearingDay}} الموافق {{hearingDate}} وذلك للسير في الدعوى ولسماع الحكم بالطلبات الواردة بأصل صحيفة الدعوى، مع حفظ كافة الحقوق القانونية الأخرى للطالب.\n'
    'ولأجل/\nالسند القانوني: المادة ٨٦ من قانون المرافعات.' + SIDE.format(subject='إعلان بإعادة الدعوى إلى الرول')))

BLOCKS = {
    'MINISTER': 'السيد/ وزير {{ministerTitle}} بصفته، ويعلن سيادته بالإدارة القانونية بهيئة قضايا الدولة الكائنة في {{stateLitigationAddress}}\nمخاطبًا مع/ {{servedWith}}\nالسند القانوني: المادة (١٣) البند (١) من قانون المرافعات.',
    'COMPANY': 'السيد/ {{companyRepName}} بصفته رئيس مجلس إدارة شركة/ {{companyName}} ويعلن سيادته في مركز إدارة الشركة الكائن في {{companyHqAddress}}\nمخاطبًا مع/ {{servedWith}}\nالسند القانوني: المادة (١٣) البند (٣، ٤) من قانون المرافعات.',
    'FOREIGN_COMPANY': 'السيد/ {{foreignAgentName}} بصفته وكيل شركة {{foreignCompanyName}} ويعلن سيادته في مركز إدارة الشركة في مصر أو الممثل القانوني للشركة/ الكائن في {{foreignCompanyEgyptAddress}}\nمخاطبًا مع/ {{servedWith}}\nالسند القانوني: المادة (١٣) البند (٥) من قانون المرافعات.',
    'ARMED_FORCES': 'السيد/ {{soldierName}} ويعلن بوحدته العسكرية ومقرها {{militaryUnitAddress}} ويعلن في مواجهة السيد وكيل نيابة/ {{prosecutionName}} ويعلن سيادته بمقر عمله بمحكمة/ {{prosecutionCourt}} وذلك لقيام سيادته بتسليم هذا الإعلان إلى الإدارة المختصة بالقوات المسلحة\nمخاطبًا مع/ {{servedWith}}\nالسند القانوني: المادة (١٣) البند (٦) من قانون المرافعات.',
    'PRISONER': 'السيد/ مأمور سجن {{prisonName}} الكائن في {{prisonAddress}} وذلك لتسليمها هذا الإعلان إلى المسجون/ {{prisonerName}}\nمخاطبًا مع/ {{servedWith}}\nوفي حالة الجنايات يعلن القيم المعين عليه من قبل المحكمة.\nالسند القانوني: المادة (١٣) البند (٧) من قانون المرافعات.',
    'ABROAD_KNOWN': 'السيد وكيل نيابة/ {{prosecutionName}} ويعلن سيادته بمقر عمله بمحكمة/ {{prosecutionCourt}} وذلك لقيام سيادته بتسليم هذا الإعلان إلى السيد/ {{abroadName}} المقيم في دولة {{abroadCountry}} وعنوانه بها هو/ {{abroadAddress}} على أن يكون الإعلان بواسطة وزارة الخارجية بالطرق الدبلوماسية\nمخاطبًا مع/ {{servedWith}}\nالسند القانوني: المادة (١٣) البند (٩) من قانون المرافعات.',
    'ABROAD_UNKNOWN': 'السيد/ {{respondentName}} وآخر موطن معلوم له هو {{lastKnownAddress}} ويعلن في مواجهة النيابة العامة والكائن مقرها في {{publicProsecutionAddress}}\nمخاطبًا مع/ {{servedWith}}\nالسند القانوني: المادة (١٣) البند (١٠) من قانون المرافعات.',
}
OPT = [('MINISTER', 'أحد الوزراء / جهة حكومية'), ('COMPANY', 'شركة تجارية أو شخص اعتباري'), ('FOREIGN_COMPANY', 'شركة أجنبية لها فرع أو وكيل في مصر'),
       ('ARMED_FORCES', 'أحد أفراد القوات المسلحة'), ('PRISONER', 'مسجون'), ('ABROAD_KNOWN', 'مقيم بالخارج له موطن معلوم'),
       ('ABROAD_UNKNOWN', 'ليس له موطن معلوم')]
def f(key, label, show, **kw):
    return dict(key=key, label=label, type=kw.pop('type', 'text'), required=False, group='بيانات المعلن إليه', showIf={'field': 'addresseeType', 'in': show}, **kw)
T.append(dict(
    id='notice-special-addressee', name='إعلان على يد محضر لجهة خاصة (المادة 13 مرافعات)', sort=30,
    description='صيغة ورقة إعلان لوزير، أو شركة، أو شركة أجنبية، أو فرد بالقوات المسلحة، أو مسجون، أو مقيم بالخارج، أو من ليس له موطن معلوم — مع السند القانوني لكل حالة.',
    legalBasis=['قانون المرافعات المدنية والتجارية — المادة 13 (البنود 1، 3، 4، 5، 6، 7، 9، 10)'],
    fields=BAILIFF + PARTIES + [
        dict(key='addresseeType', label='المعلن إليه', type='select', required=True, group='بيانات المعلن إليه',
             options=[dict(value=v, label=l) for v, l in OPT]),
        f('ministerTitle', 'الوزارة (وزير ...)', ['MINISTER']), f('stateLitigationAddress', 'عنوان هيئة قضايا الدولة', ['MINISTER']),
        f('companyRepName', 'اسم رئيس مجلس الإدارة', ['COMPANY']), f('companyName', 'اسم الشركة', ['COMPANY']), f('companyHqAddress', 'عنوان مركز إدارة الشركة', ['COMPANY']),
        f('foreignAgentName', 'اسم وكيل الشركة / الممثل القانوني', ['FOREIGN_COMPANY']), f('foreignCompanyName', 'اسم الشركة الأجنبية', ['FOREIGN_COMPANY']),
        f('foreignCompanyEgyptAddress', 'عنوان مركز الإدارة في مصر', ['FOREIGN_COMPANY']),
        f('soldierName', 'اسم فرد القوات المسلحة', ['ARMED_FORCES']), f('militaryUnitAddress', 'الوحدة العسكرية ومقرها', ['ARMED_FORCES']),
        f('prosecutionName', 'النيابة المختصة', ['ARMED_FORCES', 'ABROAD_KNOWN']), f('prosecutionCourt', 'المحكمة التي بها مقر النيابة', ['ARMED_FORCES', 'ABROAD_KNOWN']),
        f('prisonName', 'اسم السجن', ['PRISONER']), f('prisonAddress', 'عنوان السجن', ['PRISONER']), f('prisonerName', 'اسم المسجون', ['PRISONER']),
        f('abroadName', 'اسم المعلن إليه', ['ABROAD_KNOWN']), f('abroadCountry', 'الدولة', ['ABROAD_KNOWN']), f('abroadAddress', 'العنوان بالخارج', ['ABROAD_KNOWN']),
        f('respondentName', 'اسم المعلن إليه', ['ABROAD_UNKNOWN']), f('lastKnownAddress', 'آخر موطن معلوم', ['ABROAD_UNKNOWN']),
        f('publicProsecutionAddress', 'مقر النيابة العامة', ['ABROAD_UNKNOWN']),
        dict(key='noticeBody', label='موضوع الإعلان (وأعلنته بالآتي)', type='textarea', required=True, group='الموضوع'),
    ],
    blocks={'addresseeType': BLOCKS},
    body=HEAD + '{{block:addresseeType}}\n\nوأعلنته بالآتي\n{{noticeBody}}\n\nولأجل/' + SIDE.format(subject='إعلان'),
    notes='النموذج الأصلي يعرض صيغ "المعلن إليه" فقط؛ موضوع الإعلان يكتبه المستخدم.'))
T.append(dict(
    id='execution-objection-civil', name='صحيفة إشكال في تنفيذ حكم مدني', sort=40,
    description='إشكال وقتي لوقف تنفيذ حكم مدني أمام قاضي التنفيذ / الدائرة المستعجلة مع اختصام محضر أول المحكمة (المادة 312 مرافعات).',
    legalBasis=['قانون المرافعات المدنية والتجارية — المادة 312'],
    fields=BAILIFF + PARTIES + RESP + [
        dict(key='servedWith2', label='مخاطبًا مع (المستشكل ضده الثاني)', type='text', required=False, defaultValue=DOTS, group='بيانات المحضر (تُترك للمحضر)'),
        dict(key='executionCourt', label='محضر أول محكمة', type='text', required=True, group='المستشكل ضده الثاني'),
        dict(key='caseNumber', label='رقم الدعوى / الحكم', type='text', required=True, group='الحكم المستشكل فيه'),
        dict(key='caseYear', label='السنة', type='number', required=True, min=1950, max=2100, group='الحكم المستشكل فيه'),
        dict(key='originalRequests', label='طلبات المستشكل ضده في دعواه', type='textarea', required=True, group='الحكم المستشكل فيه'),
        dict(key='judgmentDate', label='تاريخ الحكم', type='date', required=True, group='الحكم المستشكل فيه'),
        dict(key='judgmentOperative', label='منطوق الحكم', type='textarea', required=True, group='الحكم المستشكل فيه'),
        dict(key='grounds', label='أسباب الإشكال', type='textarea', required=True, group='الأسباب', hint='يمكن ترك صياغتها للذكاء الاصطناعي من وقائعك'),
        dict(key='courtName', label='المحكمة المختصة بالإشكال', type='text', required=True, group='الجلسة'),
        dict(key='courtAddress', label='مقر المحكمة', type='text', required=True, group='الجلسة'),
        dict(key='circuit', label='الدائرة (مستعجل)', type='text', required=True, group='الجلسة'),
    ] + HEARING,
    body=HEAD +
    '١) السيد/ {{respondentName}} المقيم في {{respondentAddress}} قسم {{respondentDistrict}} محافظة {{respondentGovernorate}}\nمخاطبًا مع/ {{servedWith}}\n'
    '٢) السيد/ محضر أول محكمة {{executionCourt}} بصفته\nمخاطبًا مع/ {{servedWith2}}\n'
    'وأعلنته بالآتي\n'
    'أقام المستشكل ضده الأول الدعوى رقم {{caseNumber}} لسنة {{caseYear}} مدني طالبًا في ختام صحيفة افتتاحها الحكم له بـ {{originalRequests}}.\n'
    'وبتاريخ {{judgmentDate}} قضت محكمة أول درجة بـ {{judgmentOperative}}.\n'
    'وحيث إن هذا الحكم قد جاء مخالفًا للواقع، وهو الأمر الذي يحق معه للطالب إعمالًا لنص المادة ٣١٢ من قانون المرافعات إقامة الإشكال الماثل لوقف تنفيذ الحكم وذلك للأسباب التالية:\n{{grounds}}\n'
    'غير أن الغرض من اختصام المستشكل ضده الثاني هو وقف تنفيذ الحكم رقم {{caseNumber}} لسنة {{caseYear}} مدني لحين الفصل في الإشكال.\n\nبناءً عليه\n'
    'أنا المحضر سالف الذكر قد انتقلت في التاريخ المذكور أعلاه إلى حيث المعلن إليه وسلمته صورة من أصل الصحيفة وكلفته بالحضور أمام محكمة {{courtName}} الكائن مقرها في {{courtAddress}} أمام الدائرة ({{circuit}}) مستعجل وذلك بجلستها العلنية التي ستنعقد في تمام الساعة التاسعة وما بعدها من صباح يوم {{hearingDay}} الموافق {{hearingDate}} وذلك ليسمع الحكم بـ:\n'
    'أولًا: بقبول الإشكال شكلًا.\nثانيًا: وفي الموضوع بوقف تنفيذ الحكم رقم {{caseNumber}} لسنة {{caseYear}} مدني، مع حفظ كافة الحقوق القانونية الأخرى للطالب.\n'
    'ولأجل/\nالسند القانوني: المادة ٣١٢ من قانون المرافعات.' + SIDE.format(subject='إشكال في تنفيذ الحكم الصادر في الدعوى رقم {{caseNumber}} لسنة {{caseYear}} مدني')))
T.append(dict(
    id='appeal-final-judgment', name='صحيفة استئناف حكم (المادة 221 مرافعات)', sort=50,
    description='استئناف حكم صادر بصفة انتهائية من محكمة أول درجة لمخالفة قواعد الاختصاص المتعلقة بالنظام العام أو لبطلان الحكم أو الإجراءات.',
    legalBasis=['قانون المرافعات المدنية والتجارية — المادة 221'],
    fields=BAILIFF + PARTIES + RESP + [
        dict(key='caseNumber', label='رقم الدعوى المستأنف حكمها', type='text', required=True, group='الحكم المستأنف'),
        dict(key='caseYear', label='السنة', type='number', required=True, min=1950, max=2100, group='الحكم المستأنف'),
        dict(key='originalRequests', label='طلبات المستأنف ضده في دعواه', type='textarea', required=True, group='الحكم المستأنف'),
        dict(key='judgmentDate', label='تاريخ الحكم', type='date', required=True, group='الحكم المستأنف'),
        dict(key='judgmentOperative', label='منطوق الحكم', type='textarea', required=True, group='الحكم المستأنف'),
        dict(key='grounds', label='أسباب الاستئناف', type='textarea', required=True, group='الأسباب', hint='يمكن ترك صياغتها للذكاء الاصطناعي من وقائعك'),
        dict(key='appealRequests', label='المطلوب القضاء به مجددًا', type='textarea', required=True, group='الطلبات'),
        dict(key='appealCourt', label='المحكمة الاستئنافية', type='text', required=True, group='الجلسة'),
        dict(key='courtAddress', label='مقر المحكمة', type='text', required=True, group='الجلسة'),
        dict(key='circuit', label='الدائرة', type='text', required=True, group='الجلسة'),
    ] + HEARING,
    body=HEAD + RESP_LINE +
    'وأعلنته بالآتي\n'
    'أقام المستأنف ضده الدعوى رقم {{caseNumber}} لسنة {{caseYear}} طالبًا في ختام صحيفة افتتاحها الحكم له بـ {{originalRequests}}.\n'
    'وبتاريخ {{judgmentDate}} قضت محكمة أول درجة بـ {{judgmentOperative}}.\n'
    'وحيث إن هذا الحكم قد جاء مخالفًا للواقع والقانون وذلك للأسباب التالية:\n{{grounds}}\n'
    'وحيث تنص المادة ٢٢١ من قانون المرافعات على أنه:\n'
    '"يجوز استئناف الأحكام الصادرة بصفة انتهائية من محاكم الدرجة الأولى بسبب مخالفة قواعد الاختصاص المتعلقة بالنظام العام، أو وقوع بطلان في الحكم، أو بطلان في الإجراءات أثر في الحكم……"\n'
    'وهو ما يحق معه للطالب إقامة الاستئناف الماثل للأسباب السابق ذكرها بصدر الصحيفة والأسباب الأخرى التي سيبديها في المذكرات والمرافعات الشفاهية.\n\nبناءً عليه\n'
    'أنا المحضر سالف الذكر قد انتقلت في التاريخ المذكور أعلاه إلى حيث المعلن إليه وسلمته صورة من أصل الصحيفة وكلفته بالحضور أمام محكمة {{appealCourt}} الكائن مقرها في {{courtAddress}} أمام الدائرة ({{circuit}}) مستأنف وذلك بجلستها العلنية التي ستنعقد في تمام الساعة التاسعة وما بعدها من صباح يوم {{hearingDay}} الموافق {{hearingDate}} وذلك لسماع الحكم بـ:\n'
    'أولًا: بقبول الاستئناف شكلًا.\n'
    'ثانيًا: وفي الموضوع بإلغاء الحكم المستأنف رقم {{caseNumber}} لسنة {{caseYear}} واعتباره كأن لم يكن، والقضاء مجددًا بـ {{appealRequests}} وإلزام المستأنف ضده بالمصروفات ومقابل أتعاب المحاماة عن درجتي التقاضي، مع حفظ كافة الحقوق القانونية الأخرى للطالب.\n'
    'ولأجل/\nالسند القانوني: المادة ٢٢١ من قانون المرافعات.' + SIDE.format(subject='استئناف حكم'),
    notes='النموذج مبني على المادة 221 (استئناف الأحكام الانتهائية لأسباب محددة)؛ الاستئناف العادي له شروط ومواعيد أخرى — تُراجع قبل الاستخدام.'))

def court(id, name, sort, desc, basis, extra_fields, body_mid, subject, ending, notes=None):
    T.append(dict(id=id, name=name, sort=sort, description=desc, legalBasis=basis,
                  fields=BAILIFF + PARTIES + RESP + CASE + extra_fields + HEARING,
                  body=HEAD + RESP_LINE + 'وأعلنته بالآتي\n' + body_mid + '\nوحيث يهم الطالب تنفيذ قرار المحكمة.\n\nبناءً عليه\n'
                  'أنا المحضر سالف الذكر قد انتقلت في التاريخ المذكور أعلاه إلى حيث المعلن إليه وسلمته صورة من هذا الإعلان وكلفته بالحضور أمام محكمة {{courtName}} الكائن مقرها في {{courtAddress}} أمام الدائرة ({{circuit}}) وذلك بجلستها العلنية التي ستنعقد في تمام الساعة التاسعة وما بعدها من صباح يوم {{hearingDay}} الموافق {{hearingDate}} ' + ending + '\nولأجل/' + SIDE.format(subject=subject),
                  **({'notes': notes} if notes else {})))

ART123 = ('"تقدم الطلبات العارضة من المدعى أو من المدعى عليه إلى المحكمة بالإجراءات المعتادة لرفع الدعوى قبل يوم الجلسة أو بطلب يقدم شفاهة في الجلسة '
          'في حضور الخصم ويثبت في محضرها ولا تقبل الطلبات العارضة بعد إقفال باب المرافعة."')
CASE_OPEN = 'أقام الطالب ضد المعلن إليه الدعوى رقم {{caseNumber}} لسنة {{caseYear}} أمام محكمة {{courtName}} الدائرة ({{circuit}}) طالبًا في ختام صحيفة افتتاحها الحكم له بـ {{originalRequests}}.\n'
court('notice-claim-correction', 'إعلان بتصحيح شكل الدعوى', 15,
      'إعلان الخصم بتصحيح الطلب الأصلي أو تعديل موضوعه لظروف طرأت بعد رفع الدعوى (المادتان 123 و124/1 مرافعات).',
      ['قانون المرافعات المدنية والتجارية — المادتان 123 و124/1'],
      [dict(key='adjournHearingDate', label='تاريخ الجلسة التي قررت التأجيل', type='date', required=True, group='التصحيح'),
       dict(key='correctedRequest', label='الطلب بعد التصحيح / التعديل', type='textarea', required=True, group='التصحيح')],
      CASE_OPEN + 'وبجلسة {{adjournHearingDate}} قررت المحكمة تأجيل نظر الدعوى إلى جلسة {{hearingDate}} بناءً على طلب المدعي وذلك لتصحيح شكل الدعوى لحدوث أمر عارض يستلزم تصحيح الطلب الأصلي وهو:\n{{correctedRequest}}\n'
      'إعمالًا لنص المادة ١٢٣ من قانون المرافعات التي تنص على أنه:\n' + ART123 + '\nونص المادة ١٢٤/١ من قانون المرافعات التي تنص على أنه:\n'
      '"للمدعى أن يقدم من الطلبات العارضة:\n١ - ما يتضمن تصحيح الطلب الأصلي أو تعديل موضوعه لمواجهة ظروف طرأت أو تبينت بعد رفع الدعوى."',
      'إعلان بتصحيح شكل الدعوى',
      'وذلك لسماع الحكم بتصحيح شكل الدعوى بـ {{correctedRequest}} وسماع الحكم بالطلبات الواردة بأصل صحيفة الدعوى، مع حفظ كافة الحقوق القانونية الأخرى للطالب.\nالسند القانوني: المادتان ١٢٣، ١٢٤/١ من قانون المرافعات.')
court('notice-re-service', 'إعادة إعلان المدعى عليه (المادة 84/1 مرافعات)', 25,
      'إعادة إعلان المدعى عليه الذي تخلف عن الجلسة الأولى ولم يُعلن لشخصه، مع التنبيه بأن الحكم يعتبر حضوريًا.',
      ['قانون المرافعات المدنية والتجارية — المادة 84/1'],
      [dict(key='firstHearingDate', label='تاريخ الجلسة التي تخلف فيها المدعى عليه', type='date', required=True, group='إعادة الإعلان')],
      CASE_OPEN + 'وتحدد لنظر الدعوى جلسة {{firstHearingDate}}، وبتلك الجلسة قررت المحكمة التأجيل لإعادة إعلان المدعى عليه لجلسة {{hearingDate}} لتخلفه عن الحضور، إعمالًا لنص المادة ٨٤/١ من قانون المرافعات التي تنص على أنه:\n'
      '"إذا تخلف المدعى عليه وحده في الجلسة الأولى وكانت صحيفة الدعوى قد أعلنت لشخصه حكمت المحكمة في الدعوى فإذا لم يكن قد أعلن لشخصه كان على المحكمة في غير الدعاوى المستعجلة تأجيل نظر القضية إلى جلسة تالية يعلن المدعى بها الخصم الغائب ويعتبر الحكم في الدعوى في الحالتين حكمًا حضوريًا."',
      'إعادة إعلان',
      'وذلك لسماع الحكم بالطلبات الواردة بأصل صحيفة الدعوى، ونبهت عليه أنه في حالة عدم حضوره يعتبر الحكم حضوريًا في حقه، مع حفظ كافة الحقوق القانونية الأخرى للطالب.\nالسند القانوني: المادة ٨٤/١ من قانون المرافعات.')

T.append(dict(
    id='warning-marital-furniture-offer', name='إنذار عرض منقولات زوجية', sort=60,
    description='عرض الزوج منقولات الزوجية عرضًا قانونيًا على يد محضر لإبراء ذمته في جنحة تبديد، مع طلب الإيداع إن رُفض الاستلام (المادة 488 مرافعات).',
    legalBasis=['قانون المرافعات المدنية والتجارية — المادة 488'],
    fields=BAILIFF + PARTIES + [dict(key=k, label=l, type=t, required=True, group=g) for k, l, t, g in [
        ('respondentName', 'اسم الزوجة (المنذر إليها)', 'text', 'المنذر إليها'), ('respondentAddress', 'عنوان المنذر إليها', 'text', 'المنذر إليها'),
        ('respondentDistrict', 'القسم / المركز', 'text', 'المنذر إليها'), ('respondentGovernorate', 'المحافظة', 'text', 'المنذر إليها'),
        ('marriageDate', 'تاريخ وثيقة الزواج', 'date', 'الزواج والجنحة'), ('misdemeanorNumber', 'رقم الجنحة', 'text', 'الزواج والجنحة'),
        ('misdemeanorYear', 'سنة الجنحة', 'number', 'الزواج والجنحة'), ('misdemeanorCourt', 'جنح (القسم / المركز)', 'text', 'الزواج والجنحة'),
        ('misdemeanorHearingDate', 'الجلسة المحددة لنظر الجنحة', 'date', 'الزواج والجنحة'), ('adjournDate', 'الجلسة المؤجل إليها لإتمام العرض', 'date', 'الزواج والجنحة'),
        ('furnitureList', 'بيان المنقولات المعروضة', 'textarea', 'المنقولات'), ('offerDay', 'يوم العرض', 'text', 'ميعاد العرض'),
        ('offerDate', 'تاريخ العرض', 'date', 'ميعاد العرض'), ('offerTime', 'الساعة', 'text', 'ميعاد العرض'), ('policeStation', 'قسم الشرطة', 'text', 'ميعاد العرض')]],
    body=HEAD + 'السيدة/ {{respondentName}} المقيمة في {{respondentAddress}} قسم {{respondentDistrict}} محافظة {{respondentGovernorate}}\nمخاطبًا مع/ {{servedWith}}\nوأنذرتها بالآتي\n'
    'الطالب زوج للمنذر إليها بموجب وثيقة الزواج الشرعي المؤرخة {{marriageDate}}، إلا أن المنذر إليها قد تركت منزل الزوجية وهي لا تزال في عصمة الطالب.\n'
    'وقد أقامت المنذر إليها ضد الطالب الجنحة رقم {{misdemeanorNumber}} لسنة {{misdemeanorYear}} جنح {{misdemeanorCourt}} مدعية تبديده منقولات زوجية وقد تحدد لنظرها جلسة {{misdemeanorHearingDate}}، وبتلك الجلسة مثل الطالب وطلب التصريح له بعرض المنقولات عرضًا قانونيًا لإبراء ذمته فقررت المحكمة التأجيل لجلسة {{adjournDate}} لإتمام العرض القانوني.\n'
    'وحيث يهم الطالب تنفيذ قرار المحكمة وإبراء ذمته فإنه يعرض منقولات الزوجية على يد محضر وبيانها كالتالي:\n{{furnitureList}}\n'
    'وفي حالة رفض الاستلام سيضطر الطالب إلى عرض الأمر على قاضي الأمور المستعجلة للترخيص له بإيداع المنقولات في المكان الذي يحدده على أن تكون مصروفات النقل والتخزين على عاتق المنذر إليها، وذلك وفقًا للمادة ٤٨٨ من قانون المرافعات التي تنص على أنه:\n'
    '"وإذا كان المعروض شيئًا غير النقود جاز للمدين الذي عرضه أن يطلب من قاضي الأمور المستعجلة الترخيص في إيداعه بالمكان الذي يعينه القاضي إذا كان الشيء مما يمكن نقله."\n\nبناءً عليه\n'
    'أنا المحضر سالف الذكر قد انتقلت إلى حيث محل إقامة المنذر إليها وسلمتها صورة من هذا الإنذار للعلم بما جاء به ونفاذ مفعوله قانونًا، وعرضت عليها المنقولات المبينة بصدر هذا الإنذار عرضًا قانونيًا، وحددت لها ميعاد العرض الساعة {{offerTime}} من يوم {{offerDay}} الموافق {{offerDate}} أمام قسم شرطة {{policeStation}}، مع حفظ كافة الحقوق القانونية الأخرى للطالب.\n'
    'ولأجل/\nالسند القانوني: المادة ٤٨٨ من قانون المرافعات.' + SIDE.format(subject='إنذار عرض منقولات زوجية'),
    notes='في النموذج الأصلي "وإذا كان العروض"؛ صُححت إلى "المعروض".'))

TAX_TYPES = ['الضريبة على أرباح الأشخاص الاعتبارية', 'الضريبة على أرباح الأشخاص الطبيعيين', 'الضريبة على الأجور والمرتبات',
             'الدمغة ورسم تنمية الموارد المالية للدولة', 'الضريبة العامة على المبيعات / القيمة المضافة', 'الضريبة على العقارات المبنية',
             'الضريبة على مقابل دخول المسارح والملاهي']
def F(k, l, t='text', req=True, g='البيانات', **kw):
    d = dict(key=k, label=l, type=t, required=req, group=g, **kw)
    if not req and 'defaultValue' not in kw: d['defaultValue'] = DOTS
    return d
APPLICANT = [F('applicantName', 'اسم مقدم الطلب', g='مقدم الطلب'), F('applicantCapacity', 'الصفة', g='مقدم الطلب'),
             F('authorizationNo', 'التوكيل / التفويض', req=False, g='مقدم الطلب')]
MOF_HEAD = 'طلب\nإنهاء منازعة {kind} طبقًا للقانون رقم ١٥٢ لسنة ٢٠٢٦ بتجديد العمل بالقانون رقم ٧٩ لسنة ٢٠١٦ في شأن إنهاء المنازعات الضريبية\n\n'
TT = [dict(
    id='mof-tax-dispute-settlement-request', name='طلب إنهاء منازعة ضريبية (القانون 152 لسنة 2026)', sort=10,
    description='النموذج الرسمي المرفق بقرار وزير المالية 450 لسنة 2026 لطلب إحالة النزاع الضريبي إلى لجنة إنهاء المنازعات.',
    fields=[F('taxOffice', 'مأمورية / مركز الضرائب'), F('taxpayerName', 'اسم الممول / المكلف'), F('taxRegistrationNo', 'رقم التسجيل الضريبي'),
            F('activity', 'النشاط'), F('activityAddress', 'عنوان النشاط'), F('mailingAddress', 'عنوان المراسلة'), F('email', 'البريد الإلكتروني', 'email', False),
            F('phone', 'رقم الهاتف', 'phone'), F('taxTypes', 'نوع الضريبة', 'multiselect', options=[dict(value=str(i + 1), label=l) for i, l in enumerate(TAX_TYPES)]),
            F('taxPeriods', 'الفترة / الفترات الضريبية'), F('disputedBase', 'قيمة الوعاء المتنازع عليه', 'money'), F('disputedTax', 'قيمة الضريبة المتنازع عليها', 'money'),
            F('appealCommittee', 'لجنة الطعن', req=False), F('appealNo', 'رقم الطعن', req=False), F('court', 'المحكمة', req=False), F('caseNo', 'رقم الدعوى', req=False)] + APPLICANT,
    body=MOF_HEAD.format(kind='ضريبية') + 'السيد الأستاذ/ رئيس مأمورية/مركز {{taxOffice}}\nتحية طيبة وبعد،\nبرجاء التكرم بإحالة النزاع الموضح بياناته بعد إلى لجنة إنهاء المنازعات الضريبية المختصة.\n'
    'اسم الممول/المكلف/ {{taxpayerName}}\nرقم التسجيل الضريبي/ {{taxRegistrationNo}} — عنوان النشاط/ {{activityAddress}}\nالنشاط/ {{activity}}\nعنوان المراسلة/ {{mailingAddress}}\n'
    'البريد الإلكتروني/ {{email}} — رقم الهاتف/ {{phone}}\nنوع الضريبة: {{taxTypes}}\nالفترة/الفترات الضريبية/ {{taxPeriods}}\n'
    'قيمة الوعاء المتنازع عليه/ {{disputedBase}} — قيمة الضريبة المتنازع عليها/ {{disputedTax}}\nالجهة المنظور أمامها النزاع حاليًا:\n'
    'لجنة الطعن: {{appealCommittee}} — رقم الطعن/ {{appealNo}}\nمحكمة: {{court}} — رقم الدعوى/ {{caseNo}}\n\nمقدمه/\nالاسم/ {{applicantName}}\nالصفة/ {{applicantCapacity}}\nالتوكيل/التفويض/ {{authorizationNo}}'),
  dict(
    id='mof-customs-dispute-settlement-request', name='طلب إنهاء منازعة جمركية (القانون 152 لسنة 2026)', sort=20,
    description='النموذج الرسمي المرفق بقرار وزير المالية 450 لسنة 2026 لطلب إحالة النزاع الجمركي إلى لجنة إنهاء المنازعات الجمركية.',
    fields=[F('customsAdministration', 'الإدارة'), F('traderName', 'اسم المتعامل'), F('tradersRegNo', 'رقم المتعاملين'), F('activity', 'النشاط'),
            F('mailingAddress', 'عنوان المراسلة'), F('email', 'البريد الإلكتروني', 'email', False), F('phone', 'رقم الهاتف', 'phone'),
            F('customsData', 'البيانات الجمركية', 'textarea'), F('disputedDuties', 'قيمة الضرائب والرسوم الجمركية المتنازع عليها', 'money'),
            F('paidDuties', 'الضرائب والرسوم المسددة', req=False), F('paymentDate', 'تاريخ السداد', 'date', False), F('grounds', 'أسانيد الطلب بإيجاز', 'textarea'),
            F('customsOffice', 'الجمارك', req=False), F('grievanceNo', 'رقم التظلم', req=False), F('court', 'المحكمة', req=False), F('caseNo', 'رقم الدعوى', req=False),
            F('arbitration', 'لجان التحكيم', req=False)] + APPLICANT,
    body=MOF_HEAD.format(kind='جمركية') + 'السيد الأستاذ/ رئيس الإدارة {{customsAdministration}}\nتحية طيبة وبعد،\nبرجاء التكرم بإحالة النزاع الموضح بياناته بعد إلى لجنة إنهاء المنازعات الجمركية المختصة.\n'
    'اسم المتعامل/ {{traderName}}\nرقم المتعاملين/ {{tradersRegNo}} — النشاط/ {{activity}}\nعنوان المراسلة/ {{mailingAddress}}\nالبريد الإلكتروني/ {{email}} — رقم الهاتف/ {{phone}}\n'
    'البيانات الجمركية/ {{customsData}}\nقيمة الضرائب والرسوم الجمركية المتنازع عليها/ {{disputedDuties}}\nالضرائب والرسوم الجمركية المسددة/ {{paidDuties}} — تاريخ السداد/ {{paymentDate}}\n'
    'أسانيد الطلب بإيجاز/ {{grounds}}\nالجهة المنظور أمامها النزاع حاليًا:\nالجمارك: {{customsOffice}} — رقم التظلم/ {{grievanceNo}}\nمحكمة: {{court}} — رقم الدعوى/ {{caseNo}}\nلجان التحكيم: {{arbitration}}\n\n'
    'مقدمه/\nالاسم/ {{applicantName}}\nالصفة/ {{applicantCapacity}}\nالتوكيل/التفويض/ {{authorizationNo}}')]
for t in TT:
    t.update(legalBasis=['القانون رقم 79 لسنة 2016 في شأن إنهاء المنازعات الضريبية', 'القانون رقم 152 لسنة 2026', 'قرار وزير المالية رقم 450 لسنة 2026'],
             category='TAX_FORMS', categoryLabel='نماذج وطلبات ضريبية وجمركية', credits=1, active=True, version=1, fixedQuotes=True,
             source='قرار وزير المالية رقم 450 لسنة 2026 (النموذجان المرفقان به)',
             draftingInstructions='املأ النموذج بالبيانات كما أدخلها المستخدم. نبّه إلى أن العمل بالقانون 79 لسنة 2016 مجدد حتى 31 ديسمبر 2026 وأن الطلب يجوز تقديمه إلكترونيًا مجانًا.')

for t in T:
    t.update(category='COURT_PAPERS', categoryLabel='صحف الدعاوى والإعلانات', credits=1, active=True, version=1,
             fixedQuotes=True, source=SOURCE, draftingInstructions=COMMON_INSTR)
    pass
T += TT
for t in T:
    if t['id'] == 'notice-incidental-claims':
        t['version'] = 2
    keys = {f['key'] for f in t['fields']}
    import re
    used = set(re.findall(r'\{\{(\w+)\}\}', t['body'] + json.dumps(t.get('blocks', {}), ensure_ascii=False)))
    missing = used - keys - {'block'}
    assert not missing, (t['id'], missing)
    json.dump(t, open(f"{t['id']}.json", 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print(len(T), 'templates')
