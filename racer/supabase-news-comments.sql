-- Diva Skate Racer: komentar pada pemberitahuan.
-- Jalankan pada project Supabase "racer" (project ref: yngyyoqmftslhcuqmgrn).
-- Identitas, nama, dan avatar selalu diambil dari skate_accounts setelah validasi token sesi.
-- Tabel tidak dapat ditulis langsung melalui Data API; operasi tulis hanya melalui RPC.

create table if not exists public.skate_news_comments (
  id bigserial primary key,
  news_id bigint not null references public.skate_news(id) on delete cascade,
  author_key text not null,
  body text not null,
  created_at timestamptz not null default now()
);
create index if not exists skate_news_comments_news_created_idx
  on public.skate_news_comments(news_id, created_at asc);
alter table public.skate_news_comments enable row level security;
revoke all on table public.skate_news_comments from anon, authenticated, public;
grant select on table public.skate_news_comments to anon, authenticated;

create or replace function public.skate_news_comments_list(p_news_id bigint)
returns jsonb language sql security definer
set search_path = public, skate_priv, pg_temp as $$
 select jsonb_build_object('ok',true,'rows',coalesce(jsonb_agg(jsonb_build_object(
   'id',c.id,'body',c.body,'created_at',c.created_at,'name',a.name,'avatar',a.avatar
 ) order by c.created_at asc),'[]'::jsonb))
 from public.skate_news_comments c
 join public.skate_accounts a on a.name_key=c.author_key
 where c.news_id=p_news_id
   and c.created_at>now()-interval '180 days'
   and c.id in (select c2.id from public.skate_news_comments c2
     where c2.news_id=p_news_id order by c2.created_at desc limit 100);
$$;

create or replace function public.skate_news_comment_add(p_name text,p_token text,p_news_id bigint,p_body text)
returns jsonb language plpgsql security definer
set search_path = public, skate_priv, pg_temp as $$
declare a public.skate_accounts; b text:=left(btrim(coalesce(p_body,'')),500); cid bigint;
begin
 if char_length(b)<1 then return jsonb_build_object('ok',false,'err','empty'); end if;
 update public.skate_accounts set last_seen=now()
  where name_key=lower(btrim(coalesce(p_name,''))) and p_token=any(tokens)
  returning * into a;
 if a.name_key is null then return jsonb_build_object('ok',false,'err','session'); end if;
 if not exists(select 1 from public.skate_news where id=p_news_id) then
  return jsonb_build_object('ok',false,'err','news');
 end if;
 if (select count(*) from public.skate_news_comments where author_key=a.name_key
     and created_at>now()-interval '1 minute')>=8 then
  return jsonb_build_object('ok',false,'err','rate');
 end if;
 insert into public.skate_news_comments(news_id,author_key,body)
  values(p_news_id,a.name_key,b) returning id into cid;
 return jsonb_build_object('ok',true,'id',cid);
end; $$;

create or replace function public.skate_news_comment_delete(p_name text,p_token text,p_id bigint)
returns jsonb language plpgsql security definer
set search_path = public, skate_priv, pg_temp as $$
declare a public.skate_accounts;
begin
 update public.skate_accounts set last_seen=now()
  where name_key=lower(btrim(coalesce(p_name,''))) and p_token=any(tokens)
  returning * into a;
 if a.name_key is null then return jsonb_build_object('ok',false,'err','session'); end if;
 delete from public.skate_news_comments c where c.id=p_id
  and (c.author_key=a.name_key or lower(a.name)='fakecuan');
 if not found then return jsonb_build_object('ok',false,'err','forbidden'); end if;
 return jsonb_build_object('ok',true);
end; $$;

revoke all on function public.skate_news_comments_list(bigint) from public;
revoke all on function public.skate_news_comment_add(text,text,bigint,text) from public;
revoke all on function public.skate_news_comment_delete(text,text,bigint) from public;
grant execute on function public.skate_news_comments_list(bigint) to anon,authenticated;
grant execute on function public.skate_news_comment_add(text,text,bigint,text) to anon,authenticated;
grant execute on function public.skate_news_comment_delete(text,text,bigint) to anon,authenticated;
