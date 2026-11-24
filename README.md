# بوصلة التطبيقات — تطبيق العميل (Android)

تطبيق الأندرويد الذي يستخدمه العملاء: تسجيل دخول (المحفظة تُنشأ تلقائياً)،
وصف التطبيق بالبرومبت، تقدير النقاط قبل البناء، المحفظة والشحن عبر واتساب،
متابعة البناء، وتنزيل APK.

## الإعداد قبل أول بناء
1. أنشئ مشروع Supabase باسم `bosla-app` (الخطة المجانية) وشغّل ملفات SQL الثلاثة بالترتيب في SQL Editor:
   - `supabase/001_bosla_app_schema.sql` (الجداول + RLS + دالتا الشحن والخصم)
   - `supabase/002_client_auth_wallet.sql` (ربط المحفظة بحساب المستخدم + دالة `ensure_wallet`)
   - `supabase/003_username.sql` (عمود `username` + `ensure_wallet` تقرأ الاسم واسم المستخدم من بيانات الحساب)
2. في Supabase: Authentication ← URL Configuration: أضف الرابطين `boslaapp://verified` و `boslaapp://reset-password` إلى Redirect URLs **واضغط Save** — بدون الحفظ يرجع Supabase إلى Site URL فيفشل رابط التفعيل. وغيّر **Site URL** من `http://localhost:3000` (الافتراضي) إلى `https://boslaacademy.com` ثم احفظ.
3. أنشئ مستخدم الأدمن (hyyanmohamed55@gmail.com) في Authentication — للوحة تحكم الأدمن.
4. القيم مضمّنة بالفعل في `SupabaseClient.kt` (مشروع `latsgfvpgrcpfnhtdfij` — تم التحقق 2026-11-24).

## المصادقة (Supabase Auth — بلا Firebase، بقرار حيان 2026-11-24)
- **التحقق من الإيميل:** مفعّل افتراضياً (Confirm email) — **مفروض عند إنشاء الحساب**: بعد إنشاء الحساب يبقى المستخدم في **صفحة تأكيد الإيميل** (v3) مع زر «إعادة إرسال رابط التفعيل» (عدّاد 60 ثانية)؛ رابط التفعيل يعيد المستخدم إلى التطبيق عبر `boslaapp://verified` فيفتح على شاشة الدخول برسالة «تم تفعيل بريدك»، والدخول مرفوض قبل التفعيل برسالة واضحة. لا توقفه. **تنبيه:** خدمة الإيميل المدمجة في Supabase ترسل إيميلين فقط في الساعة لكل المشروع — عند بلوغ الحد تُسقط الإيميلات (429)؛ الحل الدائم للمستخدمين الحقيقيين SMTP مخصص (لم يُنفذ بعد، يحتاج قرار حيان).
- **إعادة تعيين كلمة السر:** زر «نسيت كلمة المرور؟» في شاشة الدخول يرسل رابطاً إلى البريد؛ الرابط يفتح التطبيق عبر `boslaapp://reset-password` على شاشة كلمة جديدة. **خطوة لوحة التحكم:** في Supabase أضف الرابطين إلى Authentication ← URL Configuration ← Redirect URLs: `boslaapp://reset-password` و `boslaapp://verified`، واضغط **Save**، وغيّر Site URL من `http://localhost:3000` إلى `https://boslaacademy.com` — بدون ذلك يفتح رابط التفعيل صفحة `localhost:3000` ويفشل (حصل 2026-11-24).
- من داخل المستودع: `gh workflow run "Build Client APK"`، أو زر Run workflow في تبويب Actions.
- الـ APK يظهر كـ artifact باسم `app-debug`.

## ما لم يُبنَ بعد
- Edge Function `start-build` (توليد الكود بـ Gemini + دفع إلى GitHub + تشغيل البناء + الخصم): التطبيق يستدعيها من `{supabaseUrl}/functions/v1/start-build`. إلى أن تُبنى، زر «ابنِ التطبيق» سيعرض خطأ.
- إعادة ربط لوحة تحكم الأدمن بقاعدة Supabase نفسها حتى تتشارك اللوحة والتطبيق البيانات (اللوحة حالياً على قاعدة مدمجة خاصة بها).
