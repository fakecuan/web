-- =====================================================================
-- DIVA SKATE: tabel & fungsi PEMBERITAHUAN (dipakai frame "Pemberitahuan"
-- di halaman utama game dan menu ADMIN > PENGUMUMAN).
-- Jalankan SEKALI di Supabase: Dashboard > SQL Editor > New query > Run.
-- Aman dijalankan ulang.
-- =====================================================================

create table if not exists public.skate_news (
  id          bigserial primary key,
  icon        text        not null default '📢',
  title       text        not null,
  body        text        not null default '',
  pinned      boolean     not null default false,
  created_at  timestamptz not null default now()
);

-- Tabel dikunci; akses hanya lewat fungsi di bawah.
alter table public.skate_news enable row level security;

-- Cek admin: nama harus fakecuan dan sesi (token) masih sah.
create or replace function public.skate_news_admin_ok(p_name text, p_token text)
returns boolean language plpgsql security definer set search_path = public as $$
declare r text;
begin
  if lower(coalesce(p_name, '')) <> 'fakecuan' then return false; end if;
  begin
    r := public.skate_resume(p_name, p_token)::text;
  exception when others then
    return false;
  end;
  return r ~ '"ok"\s*:\s*true';
end $$;

-- Daftar pemberitahuan (publik).
create or replace function public.skate_news_list()
returns json language sql security definer set search_path = public as $$
  select json_build_object('ok', true, 'rows', coalesce(json_agg(n), '[]'::json))
  from (
    select id, icon, title, body, pinned, created_at
    from public.skate_news
    order by pinned desc, created_at desc
    limit 30
  ) n;
$$;

-- Tulis pemberitahuan baru (admin).
create or replace function public.skate_news_post(p_name text, p_token text, p_icon text, p_title text, p_body text, p_pinned boolean)
returns json language plpgsql security definer set search_path = public as $$
declare nid bigint;
begin
  if not public.skate_news_admin_ok(p_name, p_token) then
    return json_build_object('ok', false, 'err', 'admin');
  end if;
  if coalesce(btrim(p_title), '') = '' then
    return json_build_object('ok', false, 'err', 'judul');
  end if;
  insert into public.skate_news (icon, title, body, pinned)
  values (left(coalesce(nullif(btrim(p_icon), ''), '📢'), 8), left(btrim(p_title), 90), left(coalesce(p_body, ''), 600), coalesce(p_pinned, false))
  returning id into nid;
  return json_build_object('ok', true, 'id', nid);
end $$;

-- Hapus pemberitahuan (admin).
create or replace function public.skate_news_delete(p_name text, p_token text, p_id bigint)
returns json language plpgsql security definer set search_path = public as $$
begin
  if not public.skate_news_admin_ok(p_name, p_token) then
    return json_build_object('ok', false, 'err', 'admin');
  end if;
  delete from public.skate_news where id = p_id;
  return json_build_object('ok', true);
end $$;

revoke all on function public.skate_news_admin_ok(text, text) from public, anon, authenticated;
grant execute on function public.skate_news_list() to anon, authenticated;
grant execute on function public.skate_news_post(text, text, text, text, text, boolean) to anon, authenticated;
grant execute on function public.skate_news_delete(text, text, bigint) to anon, authenticated;
