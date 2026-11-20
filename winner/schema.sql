-- The Winner athlete/coach shared records
-- Run once in Supabase SQL Editor for cloud sync between the two pages.
create table if not exists public.winner_records (
  id text primary key,
  type text not null check (type in ('training','pb','video','coach')),
  athlete text not null default '',
  title text not null default '',
  detail text not null default '',
  value text not null default '',
  unit text not null default '',
  session_date date not null default current_date,
  created_at timestamptz not null default now()
);
alter table public.winner_records enable row level security;

-- Public demo-compatible policies. For production, replace with authenticated
-- athlete/coach policies and restrict rows by team/member identity.
drop policy if exists "winner records readable" on public.winner_records;
create policy "winner records readable" on public.winner_records for select to anon, authenticated using (true);
drop policy if exists "winner records insertable" on public.winner_records;
create policy "winner records insertable" on public.winner_records for insert to anon, authenticated with check (true);
drop policy if exists "winner records deletable" on public.winner_records;
create policy "winner records deletable" on public.winner_records for delete to authenticated using (true);
grant select, insert on public.winner_records to anon, authenticated;
grant delete on public.winner_records to authenticated;
