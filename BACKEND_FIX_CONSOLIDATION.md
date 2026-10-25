# تقرير توحيد إصلاحات ALI STORE Backend

**تاريخ الفحص:** 2026-10-25  
**المستودع:** `aboodSeyam1/ALI-STORE-BACKEND`  
**مرشح الإصدار:** `integration/release-candidate-20261025`  
**فرع العمل:** `integration/verified-consolidation-20261025`  
**الحالة:** Draft / غير معتمد للإنتاج

> هذا التقرير يوثق ما أمكن التحقق منه من GitHub ومحتوى الشيفرة. لا يدّعي نجاح Maven أو الاختبارات؛ تشغيل CI الأحدث انتهى بالفشل قبل إظهار خطوات تنفيذ أو سجل قابل للاسترجاع.

## 1. نقطة البداية وحماية الفروع

- SHA مرشح الإصدار عند بدء العمل: `82251c4da1a8ecb7d7566c52bed02696ecfca939`.
- SHA فرع `main` عند الفحص: `9fa5a7a146b23d8f5c28f994c95cda24051cac57`.
- أنشئ فرع العمل المستقل من مرشح الإصدار، ولم تُعد كتابة تاريخ أي فرع أو تُحذف فروع الإصلاح.
- قبل إضافة هذا التقرير، كان SHA الشيفرة المراجعة `f696671d8f251d4b2449b546cbb8fba4d2f8ae80`. إضافة هذا التقرير تتم في commit منفصل على فرع العمل.
- لم تُدفع تغييرات إلى `main`، ولم يحدث دمج تلقائي، ولم تُشغّل migrations على قاعدة بيانات.
- لا يوجد checkout محلي للمستودع في بيئة التنفيذ الحالية؛ لذلك لم يكن ممكنًا فحص تغييرات Git المحلية السابقة على جهاز المستخدم.

## 2. مقارنة الفروع

| الفرع | SHA الرأس الذي جرى التحقق منه | مقارنة بمرشح الإصدار | القرار |
|---|---|---|---|
| `integration/release-candidate-20261025` | `82251c4da1a8ecb7d7566c52bed02696ecfca939` | المرجع الأساسي | بُني عليه فرع العمل |
| `main` | `9fa5a7a146b23d8f5c28f994c95cda24051cac57` | أقدم بـ170 commit، ولا commits فريدة متقدمة عليه في المقارنة | لم يُعدّل |
| `fix/backend-hardening-20261019` | `8c59dc20ab0d1ff029710e904a8bd07f146cd568` | خلف المرشح بـ107 commits، ولا commits فريدة متقدمة | لا حاجة لنقل commits من هذا الفرع |
| `fix/backend-invariant-hardening-20261020` | `61029b5258cccc62fe010ce12f64acf8fa6cf951` | متفرع: 5 commits متقدمة و170 خلف المرشح | لم يُدمج الفرع كاملًا؛ راجع التفسير أدناه |
| `fix/auth-session-csrf-contracts-20261020` | `33feac3ff8edd8bf85320380bc5078cc4b46cda8` | متفرع: 2 commits متقدمة و29 خلف المرشح | أُعيد تطبيق الإصلاحات المحددة التي كانت لازمة، مع تجنب نقل تغييرات تنسيق غير لازمة |
| `fix/cross-origin-csrf-response-header-20261021` | `bc70f69c5109b90ffe83456cf6f68ffa14e9227a` | خلف المرشح بـ24 commit، دون commits فريدة متقدمة | التغيير موجود في تاريخ المرشح؛ إعدادات CORS الحالية تعرض `X-XSRF-TOKEN` |
| `integration/verified-hardening-20261020` | `53583416487ca1cba7d8b9aabac116134e204560` | خلف المرشح بـ86 commit، دون commits فريدة متقدمة | لم يُنقل شيء منه على نحو أعمى |
| `integration/verified-consolidation-20261025` | SHA الشيفرة قبل هذا التقرير: `f696671d8f251d4b2449b546cbb8fba4d2f8ae80` | متقدم على مرشح الإصدار بـ7 commits، ومتطابق معه عند نقطة الدمج | جميع التعديلات الجديدة معزولة في هذا الفرع |

### قرار فرع invariant

الـ commits الخمسة الفريدة تتناول تثبيت عدّاد محاولات OTP وقفل الدخول، وCSRF والتحقق من حالة الحساب، وتعريفات حجز المخزون. لم تُنقل أنواع الحجز منه كما هي لأن ذلك الفرع عرّف الحجز باستخدام `order_id/product_id`، بينما النموذج الحالي في مرشح الإصدار مربوط بسطر الطلب `order_item_id` ومهاجرته `V18__hardening_invariants.sql`. توجد بالفعل آلية حجز مملوك لسطر الطلب في `InventoryService` والكيان الحالي؛ إدخال نموذج موازٍ كان سيخلق تعارضًا في نموذج البيانات بدل أن يصلحها.

### قرار فرع المصادقة

من commit `33feac3` أُعيد تطبيق التصحيحات ذات الصلة: استيراد `JwtException` و`AccountVersionAware`، إصلاح محارف Unicode، إصلاح المرور على مفاتيح `Map`، معالجة حالة persistence context لعناوين الشحن، accessor الخاص بـ Java record، bootstrap CSRF لاختبارات AI، وقراءات اختبار OTP غير القافلة. أُضيفت أيضًا عناوين IP اختبارية منفصلة لطلبات محددة لتقليل تداخل محدد المعدل بين سيناريوهات الاختبار.

## 3. الإصلاحات التي أُدخلت

- **البحث والأرقام العربية:** استُبدلت محارف Unicode الممثلة بصورة غير صالحة/ملتبسة بحدود رقمية صريحة للمحارف العربية-الهندية والفارسية والتطويل والتشكيل. أضيف اختبار تطبيع الرقمين `٧٧` و`۷۷` إلى `77`، مع إبقاء اختبار المدخل الزائد عن الحد.
- **JWT والجلسات:** أُصلح مرجع `JwtException` والاستيراد الناقص لـ `AccountVersionAware`. صار الحساب الحالي الذي يحمل نسخة مصادقة يرفض principal قديمًا لا يستطيع إثبات النسخة، بدل اعتبار غياب النسخة دليل توافق. أضيف اختبار انحدار لحالات نسخة الحساب المفقودة أو المتغيرة أو المطابقة.
- **البحث المرئي والصور:** استُبدل `Map.keys()` غير الصالح مع `Map` بـ `keySet()`. جرى تسلسل تعديل ذاكرة التخزين المؤقت المحدودة لمنع سباق بين الإخلاء والإضافة. بقي المُنشئ القديم للتوافق مع المصدر لكنه موسوم Deprecated؛ البحث الفعلي عبره يفشل برسالة واضحة إذا لم تُحقن خدمة تحميل المرشحين بدل أن ينتهي إلى `NullPointerException`. اختبار `minSimilarity=60` يتطابق مع القيمة الافتراضية المحددة في `VisualSearchProperties` ومجال التحقق 0–100؛ لم تُغيّر العتبة الإنتاجية.
- **عقود الاختبارات وCSRF:** أُصلح الوصول إلى مكوّن record في `AiProductResponse`، وأضيف استيراد `assertEquals` إلى اختبارات CSRF وCORS. تستخدم اختبارات AI تدفق CSRF الفعلي قبل اختبار حالة الخدمة، كي لا تحجب نتيجة CSRF النتيجة المقصودة. أُضيف مسار غير قافل لقراءة حالة تحدي OTP في تأكيدات الاختبارات؛ بقي `findForVerification` القافل لمسار التحقق نفسه.
- **ظهور المنتجات العامة:** وُحّدت شروط الظهور بين بحث المنتجات وتفاصيل المنتج عبر ID أو slug: المنتج نشط، الفئة نشطة، المورد نشط ومسموح له بالتنفيذ، ووجود SKU مورد غير فارغ وتكلفة مورد موجبة. أُضيف اختبار تكاملي يحجب المنتج من البحث والتفاصيل عند تعطيل المورد أو الفئة أو نقص خريطة تنفيذ المورد.
- **عناوين الشحن:** أضيف `flushAutomatically=true` و`clearAutomatically=true` إلى تحديث JPQL المجمع الذي يمسح علامة العنوان الافتراضي، لتفادي حالة كيان قديمة داخل persistence context.
- **ملاءمة الاختبارات:** حدّثت fixtures الكتالوج لتمثيل مورد نشط وخريطة المورد المطلوبة، وأُضيف CSRF إلى اختبار البحث المرئي الذي هدفه قياس رفض المصادقة لا رفض CSRF.

## 4. الملفات المعدلة

### كود الإنتاج
- `src/main/java/com/store/auth/repository/EmailOtpChallengeRepository.java` — قراءة حالة OTP من دون قفل في فحوص الحالة.
- `src/main/java/com/store/auth/service/JwtService.java` — استيراد JWT الاستثنائي الصحيح.
- `src/main/java/com/store/common/security/JwtAuthenticationFilter.java` — استيراد وفحص نسخة الحساب للـ principal السابق.
- `src/main/java/com/store/product/repository/ProductSpecifications.java` — شروط أهلية المورد للظهور العام.
- `src/main/java/com/store/product/service/ProductService.java` — توحيد شروط ظهور تفاصيل المنتج.
- `src/main/java/com/store/search/service/SearchService.java` — تصحيح تحويل Unicode للأرقام والتطبيع.
- `src/main/java/com/store/shipping/repository/ShippingAddressRepository.java` — مزامنة حالة persistence context بعد التحديث المجمع.
- `src/main/java/com/store/visualsearch/service/VisualSearchService.java` — إصلاح المرور على المفاتيح وحماية تحديثات cache والتحقق من حقن الخدمة.

### الاختبارات
- `src/test/java/com/store/ai/AiControllerIntegrationTest.java`
- `src/test/java/com/store/ai/AskAliServiceTest.java`
- `src/test/java/com/store/auth/AuthSecurityLegacyRegressionIntegrationTest.java`
- `src/test/java/com/store/auth/AuthSecurityRegressionIntegrationTest.java`
- `src/test/java/com/store/common/security/CookieCsrfProtectionTest.java`
- `src/test/java/com/store/common/security/JwtAuthenticationFilterSessionVersionTest.java`
- `src/test/java/com/store/common/security/SecurityCorsContractTest.java`
- `src/test/java/com/store/foundation/CatalogSecurityTest.java`
- `src/test/java/com/store/foundation/FoundationIntegrationTest.java`
- `src/test/java/com/store/search/SearchServiceTest.java`
- `src/test/java/com/store/visualsearch/service/SecureImageFetcherTest.java`
- `src/test/java/com/store/visualsearch/service/VisualSearchServiceTest.java`

لم تتغير ملفات `pom.xml` أو ملفات migrations في هذه المجموعة.

## 5. نتائج التحقق الفعلية

| الفحص | النتيجة | الدليل / القيد |
|---|---|---|
| وجود الفروع ورؤوسها ومقارنة التاريخ | اكتمل | GitHub branch/compare API؛ تفاصيلها في القسم 2 |
| فحص الملفات المعدلة نصيًا | 20/20 اجتازت الفحص النصي | لم يظهر تكرار imports أو مسافات لاحقة أو غياب سطر نهائي أو نمط شائع لمفتاح/رمز سري |
| `git diff --check` | لم يُنفذ حرفيًا | لا يوجد checkout محلي للمستودع؛ فحص النصوص لا يعادل الأمر |
| `./mvnw -version` | لم يُنفذ | لا يوجد مجلد مستودع محلي قابل للتشغيل من هذه البيئة؛ Maven CLI غير مثبت |
| `./mvnw -DskipTests compile` | لم يُنفذ | لا يمكن تشغيل Maven محليًا في البيئة الحالية |
| `./mvnw test-compile` | لم يُنفذ | لا يمكن تشغيل Maven محليًا في البيئة الحالية |
| الاختبارات المستهدفة | لم تُنفذ محليًا | لا تُسجل أي مجموعة على أنها ناجحة |
| `./mvnw clean verify` | لم يُنفذ | لا يمكن ادعاء نجاح التحقق الكامل |
| GitHub Actions على مرشح الإصدار | فشل | Run `40140574238` على SHA `82251c4d...`: مهمة Maven فشلت، ومهمة PostgreSQL تخطّاها الاعتماد |
| GitHub Actions على فرع العمل | فشل / غير قابل للتشخيص من السجل المتاح | آخر تشغيل مرئي وقت كتابة التقرير هو `40192661153` على SHA `f696671d...`; انتهت مهمة Maven خلال نحو ثانيتين، وكانت قائمة الخطوات فارغة، وتخُطّيت مهمة PostgreSQL |
| إعادة محاولة CI | فشلت أيضًا | إعادة تشغيل `40191350077` لم تنتج خطوات أو سجلًا متاحًا |
| سجلات وأسباب CI | غير متاحة | محاولة تنزيل السجل أعادت `404 BlobNotFound`; لم تتوفر رسالة تثبت إن كان السبب runner/بيئة أم شيفرة |
| PostgreSQL concurrency suite | لم تُنفذ | تخطّاها GitHub Actions لأن مهمة Maven المطلوبة فشلت |
| مراجعة migrations | مراجعة ساكنة لـ V1–V20 | قرئت الملفات كاملة؛ لم تُنفذ Flyway ولا اختبارات قاعدة البيانات |
| Graphify | غير منفذ | توثيقه وملفات bootstrap موجودة بالمستودع، لكن CLI غير متاح في PATH. لم يُثبت أي برنامج خارجي دون إذن |
| حالة Pull Request | Draft مفتوح | PR #50؛ لم يُدمج ولم يُدمج تلقائيًا إلى `main` |

## 6. ملاحظات المهاجرات والاعتمادات

تمت مراجعة ملفات `V1` إلى `V20` مع التركيز على ترتيب الجداول والأعمدة والقيود والفهارس وعلاقتها بالكيانات الحالية. تتوافق بنية حجز المخزون في `V18` مع `InventoryReservation.orderItem` ومفتاح `order_item_id`، كما تتوافق أعمدة نسخة الحساب والقفل في `V17` مع حقول المستخدم الحالية، وتضيف `V19` نسخة المصادقة إلى تحديات OTP.

**قيد نشر يجب التحقق منه:** تحتوي `V4` و`V5` على أوامر `DROP TABLE ... CASCADE` لجداول RFQ/quotation/seller/warehouse القديمة. لم تُعدّل هذه migrations، ولم تُشغّل. قبل تطبيقها على أي قاعدة بيانات مشتركة أو قديمة، يجب التحقق من تاريخ تطبيق Flyway وحاجة أي بيانات قديمة للحفظ؛ المراجعة الساكنة لا تثبت أن حذف البيانات آمن على بيئة منشورة.

## 7. المشكلات المفتوحة والخطوات اللازمة

1. **حاجز CI:** يجب الحصول على سجل أو annotations قابلة للقراءة أو Runner يعمل طبيعيًا، ثم إعادة تشغيل Maven. الفشل الحالي حدث قبل ظهور خطوات workflow؛ لذلك لا يصح نسبته إلى خطأ تجميع محدد ولا اعتباره نجاحًا.
2. **التحقق المحلي:** يلزم checkout مستقل ببيئة Java 17 وفق workflow المشروع وتشغيل `./mvnw -version`، ثم `./mvnw -DskipTests compile`، و`./mvnw test-compile`، والاختبارات المستهدفة، وأخيرًا `./mvnw clean verify` بلا تعطيل للاختبارات.
3. **اختبارات PostgreSQL/Flyway:** يجب تشغيل بوابة PostgreSQL الموثقة على قاعدة اختبار معزولة بعد نجاح بوابة Maven؛ لم يُثبت هذا الجزء حتى الآن.
4. **قرار الدمج:** يبقى PR مسودة ولا يُدمج قبل نجاح الاختبارات ومراجعة نتائجها. حالة عدم وجود تعارض نصي لا تعني وحدها سلامة منطق النظام.

## 8. الخلاصة

أُنشئ فرع تكامل مستقل وأُدخلت إصلاحات شيفرة واختبارات محددة بناءً على فروق الملفات والفروع. فحوص النصوص نجحت للملفات المعدلة، ومراجعة migrations كانت ساكنة فقط. **البناء والاختبارات غير مثبتة النجاح** لأن CI فشل قبل إظهار خطوات تنفيذ، ولا تتوفر بيئة Maven محلية في جلسة التنفيذ. لا يُعلن هذا الفرع جاهزًا للإنتاج إلى أن تتوفر سجلات CI ويكتمل `clean verify` واختبار PostgreSQL.


## ملحق تحقق بعد إضافة التقرير

بعد commit التقرير `fae3f4ce71317ceb9c81b2279510b56d9fed5fef`، أظهر GitHub Actions تشغيلًا آخر رقم `40192796156` على هذا SHA. انتهت مهمة `Maven unit/integration suite` بالفشل خلال نحو ثانيتين دون أي خطوات معروضة، وتخطّت مهمة `Real PostgreSQL concurrency suite`. محاولة تنزيل سجل المهمة لم تُمكّن من استرداده. لذلك يظل سبب الفشل غير مؤكّد؛ لا يثبت هذا التشغيل نجاح التجميع ولا يثبت أن خطأ Java هو سبب الفشل.
