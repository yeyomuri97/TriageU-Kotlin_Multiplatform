-- Uso diario del asistente de IA (límite por usuario). Idempotente.

create table if not exists public.ai_usage (
  user_id uuid not null references auth.users(id) on delete cascade,
  day date not null,
  count int not null default 0,
  primary key (user_id, day)
);

alter table public.ai_usage enable row level security;

drop policy if exists "ai_usage_select_own" on public.ai_usage;
create policy "ai_usage_select_own" on public.ai_usage
  for select to authenticated
  using (auth.uid() = user_id);

-- Sin políticas de insert/update/delete: solo la Edge Function (service role) escribe.

-- Incremento atómico con tope. Devuelve el nuevo contador, o NULL si ya se alcanzó el límite.
create or replace function public.increment_ai_usage(p_user uuid, p_limit int)
returns int
language sql
security definer
set search_path = public
as $$
  insert into public.ai_usage as u (user_id, day, count)
  values (p_user, (now() at time zone 'America/Bogota')::date, 1)
  on conflict (user_id, day)
  do update set count = u.count + 1
  where u.count < p_limit
  returning u.count;
$$;

-- Devuelve una consulta cuando la IA no pudo responder (no penaliza al usuario).
create or replace function public.refund_ai_usage(p_user uuid)
returns void
language sql
security definer
set search_path = public
as $$
  update public.ai_usage
  set count = greatest(count - 1, 0)
  where user_id = p_user and day = (now() at time zone 'America/Bogota')::date;
$$;

revoke all on function public.increment_ai_usage(uuid, int) from public, anon, authenticated;
revoke all on function public.refund_ai_usage(uuid) from public, anon, authenticated;
grant execute on function public.increment_ai_usage(uuid, int) to service_role;
grant execute on function public.refund_ai_usage(uuid) to service_role;
