-- بوصلة التطبيقات — مخطط قاعدة البيانات (Supabase)
-- شغّل هذا الملف كاملاً في SQL Editor داخل مشروع Supabase جديد.
-- الجداول: profiles (المحافظ)، points_transactions (سجل النقاط)،
-- builds (سجل البناءات)، settings (الإعدادات)، admin_users (الأدمن).
-- الدوال: credit_points (شحن)، debit_points (خصم) — تعملان داخل معاملة واحدة.

-- 1) المحافظ: كل مستخدم يُعرَّف برقم محفظته
create table if not exists public.profiles (
  id uuid primary key default gen_random_uuid(),
  wallet_number text not null unique,
  display_name text,
  points_balance int not null default 0 check (points_balance >= 0),
  created_at timestamptz not null default now()
);

-- 2) سجل النقاط (دفتر أستاذ): كل شحن وكل خصم يُسجَّل هنا
create table if not exists public.points_transactions (
  id uuid primary key default gen_random_uuid(),
  wallet_number text not null,
  amount int not null, -- موجب = شحن، سالب = خصم
  type text not null check (type in ('credit', 'debit')),
  reason text,
  admin_note text,
  created_at timestamptz not null default now()
);
create index if not exists points_transactions_wallet_idx
  on public.points_transactions (wallet_number, created_at desc);

-- 3) سجل البناءات
create table if not exists public.builds (
  id uuid primary key default gen_random_uuid(),
  wallet_number text not null,
  prompt text not null,
  status text not null default 'pending'
    check (status in ('pending', 'building', 'success', 'failed')),
  complexity text check (complexity in ('simple', 'medium', 'complex')),
  points_charged int not null default 0,
  apk_artifact_url text,
  created_at timestamptz not null default now(),
  completed_at timestamptz
);
create index if not exists builds_wallet_idx
  on public.builds (wallet_number, created_at desc);
create index if not exists builds_status_idx on public.builds (status);

-- 4) الإعدادات: سعر النقطة + نقاط كل مستوى تعقيد
create table if not exists public.settings (
  key text primary key,
  value text not null
);
insert into public.settings (key, value) values
  ('points_price_sdg', '100'),
  ('points_simple', '10'),
  ('points_medium', '25'),
  ('points_complex', '50')
on conflict (key) do nothing;

-- 5) الأدمن: البريد المسموح له بدخول لوحة التحكم
create table if not exists public.admin_users (
  id uuid primary key default gen_random_uuid(),
  email text not null unique,
  created_at timestamptz not null default now()
);
insert into public.admin_users (email) values ('hyyanmohamed55@gmail.com')
on conflict (email) do nothing;

-- 6) دالة الشحن: تنشئ المحفظة إن لم تكن موجودة، وتسجّل المعاملة، وتحدّث الرصيد
create or replace function public.credit_points(
  p_wallet text, p_amount int, p_note text default null
) returns int
language plpgsql security definer as $$
declare
  v_balance int;
begin
  if p_amount <= 0 then
    raise exception 'amount must be positive';
  end if;
  insert into public.profiles (wallet_number)
  values (p_wallet)
  on conflict (wallet_number) do nothing;
  insert into public.points_transactions (wallet_number, amount, type, reason, admin_note)
  values (p_wallet, p_amount, 'credit', 'manual credit by admin', p_note);
  update public.profiles
  set points_balance = points_balance + p_amount
  where wallet_number = p_wallet
  returning points_balance into v_balance;
  return v_balance;
end;
$$;

-- 7) دالة الخصم: ترفض إن كان الرصيد لا يكفي، وتسجّل المعاملة
create or replace function public.debit_points(
  p_wallet text, p_amount int, p_reason text default null
) returns int
language plpgsql security definer as $$
declare
  v_balance int;
begin
  if p_amount <= 0 then
    raise exception 'amount must be positive';
  end if;
  update public.profiles
  set points_balance = points_balance - p_amount
  where wallet_number = p_wallet and points_balance >= p_amount
  returning points_balance into v_balance;
  if v_balance is null then
    raise exception 'insufficient points';
  end if;
  insert into public.points_transactions (wallet_number, amount, type, reason)
  values (p_wallet, -p_amount, 'debit', p_reason);
  return v_balance;
end;
$$;

-- 8) RLS
alter table public.profiles enable row level security;
alter table public.points_transactions enable row level security;
alter table public.builds enable row level security;
alter table public.settings enable row level security;
alter table public.admin_users enable row level security;

-- الإعدادات: قراءة للجميع (السعر علني داخل التطبيق)، تعديل للأدمن فقط
drop policy if exists "settings_read_all" on public.settings;
create policy "settings_read_all" on public.settings for select using (true);
drop policy if exists "settings_admin_write" on public.settings;
create policy "settings_admin_write" on public.settings for all to authenticated using (true) with check (true);

-- المحافظ: التطبيق يسجّل محفظة جديدة ويقرأها؛ الرصيد لا يُعدَّل إلا عبر الدوال أو الأدمن
drop policy if exists "profiles_anon_insert" on public.profiles;
create policy "profiles_anon_insert" on public.profiles for insert to anon with check (true);
drop policy if exists "profiles_read_all" on public.profiles;
create policy "profiles_read_all" on public.profiles for select using (true);
drop policy if exists "profiles_admin_all" on public.profiles;
create policy "profiles_admin_all" on public.profiles for all to authenticated using (true) with check (true);

-- سجل النقاط: قراءة للأدمن فقط (الدوال تكتب متجاوزةً RLS)
drop policy if exists "transactions_admin_read" on public.points_transactions;
create policy "transactions_admin_read" on public.points_transactions for select to authenticated using (true);

-- البناءات: التطبيق يسجّل بناءً جديداً ويقرأ؛ الأدمن يديرها
drop policy if exists "builds_anon_insert" on public.builds;
create policy "builds_anon_insert" on public.builds for insert to anon with check (true);
drop policy if exists "builds_read_all" on public.builds;
create policy "builds_read_all" on public.builds for select using (true);
drop policy if exists "builds_admin_all" on public.builds;
create policy "builds_admin_all" on public.builds for all to authenticated using (true) with check (true);

-- الأدمن: قراءة للمسجّلين فقط
drop policy if exists "admin_users_auth_read" on public.admin_users;
create policy "admin_users_auth_read" on public.admin_users for select to authenticated using (true);

-- 9) تنفيذ الدوال: الشحن للأدمن فقط، الخصم للتطبيق والأدمن
revoke all on function public.credit_points(text, int, text) from public;
grant execute on function public.credit_points(text, int, text) to authenticated;
revoke all on function public.debit_points(text, int, text) from public;
grant execute on function public.debit_points(text, int, text) to anon, authenticated;
