-- Incremental pull cursor. Phones compare rows with their own updated_at (latest write wins) but
-- page through changes with synced_at, which the server stamps, so a device with a slow clock can
-- never write a row that other devices skip.
create or replace function public.set_synced_at()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  new.synced_at := (extract(epoch from clock_timestamp()) * 1000)::bigint;
  return new;
end;
$$;

alter table public.settings add column synced_at bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint;
alter table public.entries  add column synced_at bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint;
alter table public.holidays add column synced_at bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint;
alter table public.periods  add column synced_at bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint;

create trigger settings_synced_at before insert or update on public.settings for each row execute function public.set_synced_at();
create trigger entries_synced_at  before insert or update on public.entries  for each row execute function public.set_synced_at();
create trigger holidays_synced_at before insert or update on public.holidays for each row execute function public.set_synced_at();
create trigger periods_synced_at  before insert or update on public.periods  for each row execute function public.set_synced_at();

drop index if exists public.entries_user_updated_at_idx;
drop index if exists public.holidays_user_updated_at_idx;
drop index if exists public.periods_user_updated_at_idx;
create index entries_user_synced_at_idx  on public.entries  (user_id, synced_at);
create index holidays_user_synced_at_idx on public.holidays (user_id, synced_at);
create index periods_user_synced_at_idx  on public.periods  (user_id, synced_at);
