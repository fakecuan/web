-- =====================================================================
-- DIVA SKATE: daftar WAKTU TERBAIK untuk 500 M dan 1000 M (main sendiri).
-- Rekor 200 M lama TIDAK diubah/dihapus (tetap di tabel lamanya).
-- Jalankan SEKALI di Supabase: Dashboard > SQL Editor > New query > Run.
-- Aman dijalankan ulang.
-- =====================================================================

create table if not exists public.skate_times (
  race        text        not null,
  name        text        not null,
  time        numeric     not null,
  created_at  timestamptz not null default now(),
  primary key (race, name)
);
alter table public.skate_times enable row level security;

-- Simpan waktu (hanya disimpan bila lebih cepat dari waktu terbaik pemain itu).
create or replace function public.skate_time_submit(p_race text, p_name text, p_time numeric)
returns json language plpgsql security definer set search_path = public as $$
declare mn numeric;
begin
  if p_race not in ('tt500', 'tt1000', 'ds200', 'dtt200') then
    return json_build_object('ok', false, 'err', 'race');
  end if;
  mn := case p_race when 'tt1000' then 60 when 'tt500' then 30 else 10 end;
  if p_time is null or p_time < mn or p_time > 3600 or coalesce(btrim(p_name), '') = '' then
    return json_build_object('ok', false, 'err', 'time');
  end if;
  insert into public.skate_times (race, name, time) values (p_race, left(btrim(p_name), 16), round(p_time, 3))
  on conflict (race, name) do update set time = excluded.time, created_at = now()
    where excluded.time < public.skate_times.time;
  return json_build_object('ok', true);
end $$;

-- Top 10 per mata lomba.
create or replace function public.skate_time_top(p_race text)
returns json language sql security definer set search_path = public as $$
  select json_build_object('ok', true, 'rows', coalesce(json_agg(t), '[]'::json))
  from (select name, time from public.skate_times where race = p_race order by time asc limit 10) t;
$$;

grant execute on function public.skate_time_submit(text, text, numeric) to anon, authenticated;
grant execute on function public.skate_time_top(text) to anon, authenticated;
