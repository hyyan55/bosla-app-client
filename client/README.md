# بوصلة التطبيقات — تطبيق العميل (Android)

تطبيق الأندرويد الذي يستخدمه العملاء: تسجيل دخول (المحفظة تُنشأ تلقائياً)،
وصف التطبيق بالبرومبت، تقدير النقاط قبل البناء، المحفظة والشحن عبر واتساب،
متابعة البناء، وتنزيل APK.

## الإعداد قبل أول بناء
1. أنشئ مشروع Supabase باسم `bosla-app` (الخطة المجانية) وشغّل ملفي SQL بالترتيب في SQL Editor:
   - `supabase/001_bosla_app_schema.sql` (الجداول + RLS + دالتا الشحن والخصم)
   - `supabase/002_client_auth_wallet.sql` (ربط المحفظة بحساب المستخدم + دالة `ensure_wallet`)
2. في Supabase: Authentication ← Settings ← أوقف «Confirm email» حتى يعمل تسجيل الدخول فوراً من التطبيق.
3. أنشئ مستخدم الأدمن (hyyanmohamed55@gmail.com) في Authentication — للوحة تحكم الأدمن.
4. انسخ Project URL والـ anon key من Settings ← API إلى `SupabaseClient.kt` (الحقلان `supabaseUrl` و `anonKey` في أعلى الملف).

## البناء
- من داخل المستودع: `gh workflow run "Build Client APK"`، أو زر Run workflow في تبويب Actions.
- الـ APK يظهر كـ artifact باسم `app-debug`.

## ما لم يُبنَ بعد
- Edge Function `start-build` (توليد الكود بـ Gemini + دفع إلى GitHub + تشغيل البناء + الخصم): التطبيق يستدعيها من `{supabaseUrl}/functions/v1/start-build`. إلى أن تُبنى، زر «ابنِ التطبيق» سيعرض خطأ.
- إعادة ربط لوحة تحكم الأدمن بقاعدة Supabase نفسها حتى تتشارك اللوحة والتطبيق البيانات (اللوحة حالياً على قاعدة مدمجة خاصة بها).
