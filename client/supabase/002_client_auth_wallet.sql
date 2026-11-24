-- بوصلة التطبيقات — إضافات العميل (شغّل بعد 001_bosla_app_schema.sql)
-- 1) ربط المحفظة بحساب المستخدم (Supabase Auth)
-- 2) دالة ensure_wallet: تنشئ المحفظة تلقائياً عند أول دخول (رقم BOS- متسلسل + 20 نقطة هدية)
-- 3) تضييق RLS: كل مستخدم يرى محفظته وحركاته وبناءاته فقط، والأدمن يرى الكل

alter table public.profiles
  add column if not exists user_id uuid references auth.users(id);
create unique index if not exists profiles_user_id_idx on public.profiles (user_id);

create or replace function public.ensure_wallet() returns public.profiles
language plpgsql security definer as $$
declare
  v_user uuid := auth.uid();
  v_profile public.profiles;
  v_num int;
begin
  if v_user is null then
    raise exception 'not authenticated';
  end if;
  select * into v_profile from public.profiles where user_id = v_user;
  if found then
    return v_profile;
  end if;
  -- ملاحظة: تسجيلان متزامنان قد يحسبا الرقم نفسه؛ قيد التفرد سيرفض أحدهما (يعيد المحاولة من التطبيق)
  select coalesce(max((regexp_replace(wallet_number, '[^0-9]', '', 'g'))::int), 2000) + 1
    into v_num
    from public.profiles
    where wallet_number ~ '^BOS-[0-9]+$';
  insert into public.profiles (wallet_number, user_id, points_balance)
  values ('BOS-' || v_num, v_user, 20)
  returning * into v_profile;
  insert into public.points_transactions (wallet_number, amount, type, reason)
  values (v_profile.wallet_number, 20, 'credit', 'welcome gift');
  return v_profile;
end;
$$;
grant execute on function public.ensure_wallet() to authenticated;

-- المحافظ: قراءة لصاحبها أو الأدمن؛ كتابة للأدمن فقط (الرصيد عبر الدوال)
drop policy if exists "profiles_read_all" on public.profiles;
create policy "profiles_read_own_or_admin" on public.profiles for select using (
  auth.uid() = user_id
  or exists (select 1 from public.admin_users where email = (auth.jwt() ->> 'email'))
);
drop policy if exists "profiles_admin_all" on public.profiles;
create policy "profiles_admin_write" on public.profiles for all to authenticated using (
  exists (select 1 from public.admin_users where email = (auth.jwt() ->> 'email'))
) with check (
  exists (select 1 from public.admin_users where email = (auth.jwt() ->> 'email'))
);
drop policy if exists "profiles_anon_insert" on public.profiles;

-- سجل النقاط: قراءة لصاحب المحفظة أو الأدمن
drop policy if exists "transactions_admin_read" on public.points_transactions;
create policy "transactions_read_own_or_admin" on public.points_transactions for select using (
  wallet_number in (select wallet_number from public.profiles where user_id = auth.uid())
  or exists (select 1 from public.admin_users where email = (auth.jwt() ->> 'email'))
);

-- البناءات: قراءة لصاحبها أو الأدمن؛ كتابة للأدمن فقط
-- (الـ Edge Function تستخدم service_role فتتجاوز RLS عند إنشاء البناءات)
drop policy if exists "builds_anon_insert" on public.builds;
drop policy if exists "builds_read_all" on public.builds;
create policy "builds_read_own_or_admin" on public.builds for select using (
  wallet_number in (select wallet_number from public.profiles where user_id = auth.uid())
  or exists (select 1 from public.admin_users where email = (auth.jwt() ->> 'email'))
);
drop policy if exists "builds_admin_all" on public.builds;
create policy "builds_admin_write" on public.builds for all to authenticated using (
  exists (select 1 from public.admin_users where email = (auth.jwt() ->> 'email'))
) with check (
  exists (select 1 from public.admin_users where email = (auth.jwt() ->> 'email'))
);
