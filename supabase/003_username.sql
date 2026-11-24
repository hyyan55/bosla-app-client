-- بوصلة التطبيقات — اسم المستخدم (شغّل بعد 002_client_auth_wallet.sql)
-- 1) عمود username في جدول المحافظ
-- 2) دالة ensure_wallet تقرأ الاسم واسم المستخدم من بيانات الحساب
--    (user_metadata التي يرسلها التطبيق عند إنشاء الحساب) وتخزنهما في المحفظة

alter table public.profiles
  add column if not exists username text;

create or replace function public.ensure_wallet() returns public.profiles
language plpgsql security definer as $$
declare
  v_user uuid := auth.uid();
  v_profile public.profiles;
  v_num int;
  v_name text;
  v_username text;
begin
  if v_user is null then
    raise exception 'not authenticated';
  end if;
  select * into v_profile from public.profiles where user_id = v_user;
  if found then
    return v_profile;
  end if;
  -- الاسم واسم المستخدم من بيانات الحساب (أُرسلا من التطبيق عند التسجيل)
  select raw_user_meta_data ->> 'display_name', raw_user_meta_data ->> 'username'
    into v_name, v_username
    from auth.users where id = v_user;
  -- ملاحظة: تسجيلان متزامنان قد يحسبا الرقم نفسه؛ قيد التفرد سيرفض أحدهما (يعيد المحاولة من التطبيق)
  select coalesce(max((regexp_replace(wallet_number, '[^0-9]', '', 'g'))::int), 2000) + 1
    into v_num
    from public.profiles
    where wallet_number ~ '^BOS-[0-9]+$';
  insert into public.profiles (wallet_number, user_id, points_balance, display_name, username)
  values (
    'BOS-' || v_num, v_user, 20,
    nullif(trim(coalesce(v_name, '')), ''),
    nullif(trim(coalesce(v_username, '')), '')
  )
  returning * into v_profile;
  insert into public.points_transactions (wallet_number, amount, type, reason)
  values (v_profile.wallet_number, 20, 'credit', 'welcome gift');
  return v_profile;
end;
$$;
grant execute on function public.ensure_wallet() to authenticated;
