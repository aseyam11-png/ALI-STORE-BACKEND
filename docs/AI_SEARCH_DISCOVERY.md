# ALI STORE — Smart Search 2.0 / Ask Ali / AI Discovery

## Smart Search 2.0

- `GET /api/v1/search/suggestions?q=<query>&limit=<1..12>`
- يبحث في الاسم وSKU والوصف والعلامة والقسم.
- يدعم bounded typo recovery في الاقتراحات فقط.
- يطبق حدًا أعلى لطول query قدره 120 حرفًا.
- لا يعرض حقول المورد الداخلية.

## Ask Ali وAI Discovery

- `POST /api/v1/ai/ask`
- `POST /api/v1/ai/discover`
- body: `{ "prompt": "..." }`
- الحد الأقصى للطلب 1200 حرف.
- النتائج مقيدة بمنتجات الكتالوج التي أعادها النظام.
- اختيار النموذج لمنتجات خارج السياق لا يضيف تلك المنتجات إلى response.
- عند عدم تهيئة المزود يرجع النظام `503 Service Unavailable` بدل إجابة وهمية.

## Provider configuration

```env
AI_ENABLED=true
AI_BASE_URL=https://<provider>/v1
AI_API_KEY=<secret-managed-outside-git>
AI_MODEL=<model>
AI_TIMEOUT_MS=8000
AI_MAX_INPUT_CHARS=1200
AI_MAX_CONTEXT_PRODUCTS=8
```

القيمة الافتراضية `AI_ENABLED=false`، ولا يجب تخزين `AI_API_KEY` في المستودع أو في ملفات commit.

## Security boundary

الـAI يستلم customer-safe catalog context فقط. حقول `supplierCost` و`supplierSku` و`supplierId` ليست جزءًا من context أو response الخاص بـAI. نقاط AI والاقتراحات العامة محمية بـrate limiting.