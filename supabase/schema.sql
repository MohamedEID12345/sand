-- ═══════════════════════════════════════════════════════════════════════════
-- سند (Sand) — مخطط قاعدة البيانات على Supabase
-- شغّله مرة واحدة: Supabase Dashboard → SQL Editor → New query → الصق الملف كله → Run
-- آمن للتكرار (idempotent).
--
-- التخزين: شجرة مستندات (نفس شكل البيانات الذي يستخدمه الـ backend) في جدول واحد:
--   كل صف = عقدة على مسار مثل  users/<uid>  أو  library/articles/<lawId>  وقيمتها JSON.
-- الوصول من الـ backend فقط بمفتاح SUPABASE_SECRET_KEY؛ RLS مفعّل بدون سياسات ⇒
-- المفتاح العام (publishable/anon) لا يقرأ ولا يكتب شيئًا.
-- ═══════════════════════════════════════════════════════════════════════════

create table if not exists public.lg_nodes (
  path       text collate "C" primary key,
  value      jsonb not null,
  ver        bigint not null default 1,
  updated_at timestamptz not null default now()
);

alter table public.lg_nodes enable row level security;
revoke all on table public.lg_nodes from anon, authenticated, public;
grant select, insert, update, delete on table public.lg_nodes to service_role;

-- يطبّق مجموعة عمليات في معاملة واحدة (كلها أو لا شيء):
--   put     {o,p,v}      كتابة/استبدال صف
--   del     {o,p}        حذف صف
--   deltree {o,p}        حذف صف وكل ما تحته
--   cas     {o,p,ver,v}  كتابة مشروطة بالإصدار (ver=0: يجب ألا يكون موجودًا) — v=null للحذف
-- عند تعارض الإصدار يرفع LG_CONFLICT ويعيد الـ backend المحاولة.
create or replace function public.lg_apply(ops jsonb) returns void
language plpgsql
set search_path = public
as $$
declare
  op jsonb; n int; p text; v jsonb; expected bigint;
begin
  for op in select * from jsonb_array_elements(ops) loop
    p := op->>'p';
    v := op->'v';
    if v is not null and jsonb_typeof(v) = 'null' then v := null; end if;
    if p is null or p = '' then raise exception 'LG_BAD_PATH'; end if;
    case op->>'o'
      when 'put' then
        if v is null then
          delete from lg_nodes where path = p;
        else
          insert into lg_nodes(path, value, ver) values (p, v, 1)
          on conflict (path) do update set value = excluded.value, ver = lg_nodes.ver + 1, updated_at = now();
        end if;
      when 'del' then
        delete from lg_nodes where path = p;
      when 'deltree' then
        delete from lg_nodes where path = p or (path >= p || '/' and path < p || '0');
      when 'cas' then
        expected := coalesce((op->>'ver')::bigint, 0);
        if expected = 0 then
          if v is null then
            perform 1 from lg_nodes where path = p;
            if found then raise exception 'LG_CONFLICT'; end if;
          else
            insert into lg_nodes(path, value, ver) values (p, v, 1) on conflict (path) do nothing;
            get diagnostics n = row_count;
            if n = 0 then raise exception 'LG_CONFLICT'; end if;
          end if;
        else
          if v is null then
            delete from lg_nodes where path = p and ver = expected;
          else
            update lg_nodes set value = v, ver = ver + 1, updated_at = now() where path = p and ver = expected;
          end if;
          get diagnostics n = row_count;
          if n = 0 then raise exception 'LG_CONFLICT'; end if;
        end if;
      else
        raise exception 'LG_BAD_OP %', op->>'o';
    end case;
  end loop;
end;
$$;

revoke all on function public.lg_apply(jsonb) from public, anon, authenticated;
grant execute on function public.lg_apply(jsonb) to service_role;

-- مساحة الملفات (خاصة): مستندات القضايا، العقود المصدّرة، النسخ الاحتياطية، ذاكرة المكتبة
insert into storage.buckets (id, name, public, file_size_limit)
values ('sand-files', 'sand-files', false, 52428800)
on conflict (id) do nothing;

-- تحقق سريع: يجب أن يظهر 0 أو أكثر بدون خطأ
select count(*) as nodes from public.lg_nodes;
