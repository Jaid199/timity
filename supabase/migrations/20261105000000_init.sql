-- Timity: initial schema for Supabase (Postgres).
-- Mirrors the Room tables on the phone. Every row belongs to one auth user and is protected
-- by Row Level Security, so the anon key can ship inside the APK.
--
-- Conventions
--   * ids are uuids generated on the phone so rows can be created offline and synced later.
--   * all timestamps are bigint epoch milliseconds, matching Room; updated_at drives
--     latest-write-wins and deleted_at is a tombstone so deletions reach other devices.
--   * days are bigint epoch days (days since 1970-01-01), matching LocalDate.toEpochDay().
--   * times of day are minutes from midnight, 0..1439.

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------------------------------------------
-- settings: one row per user
-- ---------------------------------------------------------------------------------------------
create table public.settings (
  user_id            uuid primary key default auth.uid() references auth.users (id) on delete cascade,
  overtime_rate      double precision not null default 0 check (overtime_rate >= 0),
  standard_hours     double precision not null default 8 check (standard_hours between 0 and 24),
  overtime_mode      text not null default 'OUTSIDE_WORKING_HOURS'
                     check (overtime_mode in ('OUTSIDE_WORKING_HOURS', 'BEYOND_STANDARD_HOURS')),
  work_start         integer not null default 540 check (work_start between 0 and 1439),
  work_end           integer not null default 1020 check (work_end between 0 and 1439),
  weekend_days       text[] not null default array['FRIDAY', 'SATURDAY']
                     check (weekend_days <@ array['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY']),
  theme              text not null default 'SYSTEM' check (theme in ('SYSTEM', 'LIGHT', 'DARK')),
  holidays_synced_at bigint not null default 0 check (holidays_synced_at >= 0),
  updated_at         bigint not null check (updated_at >= 0)
);
comment on table public.settings is 'Per-user preferences. The Calendarific API key is deliberately not stored here.';

-- ---------------------------------------------------------------------------------------------
-- entries: one row per work session
-- ---------------------------------------------------------------------------------------------
create table public.entries (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null default auth.uid() references auth.users (id) on delete cascade,
  clock_in   bigint not null check (clock_in >= 0),
  clock_out  bigint check (clock_out is null or clock_out >= clock_in),
  note       text not null default '' check (char_length(note) <= 500),
  updated_at bigint not null check (updated_at >= 0),
  deleted_at bigint check (deleted_at is null or deleted_at >= 0)
);
comment on table public.entries is 'Work sessions. clock_out is null while clocked in.';
create index entries_user_clock_in_idx on public.entries (user_id, clock_in desc);
create index entries_user_updated_at_idx on public.entries (user_id, updated_at);

-- ---------------------------------------------------------------------------------------------
-- holidays: synced (Calendarific) and custom days off
-- ---------------------------------------------------------------------------------------------
create table public.holidays (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null default auth.uid() references auth.users (id) on delete cascade,
  epoch_day  bigint not null,
  name       text not null check (char_length(name) between 1 and 120),
  type       text not null check (char_length(type) between 1 and 60),
  source     text not null check (source in ('calendarific', 'custom')),
  enabled    boolean not null default true,
  updated_at bigint not null check (updated_at >= 0),
  deleted_at bigint check (deleted_at is null or deleted_at >= 0),
  unique (user_id, epoch_day, name)
);
comment on table public.holidays is 'Days on which all work counts as overtime. type is a Calendarific type, or Custom / Sick day / Day off.';
create index holidays_user_epoch_day_idx on public.holidays (user_id, epoch_day);
create index holidays_user_updated_at_idx on public.holidays (user_id, updated_at);

-- ---------------------------------------------------------------------------------------------
-- periods: date ranges with their own working hours (Ramadan)
-- ---------------------------------------------------------------------------------------------
create table public.periods (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name            text not null check (char_length(name) between 1 and 60),
  start_epoch_day bigint not null,
  end_epoch_day   bigint not null check (end_epoch_day >= start_epoch_day),
  work_start      integer not null check (work_start between 0 and 1439),
  work_end        integer not null check (work_end between 0 and 1439),
  standard_hours  double precision not null check (standard_hours between 0 and 24),
  enabled         boolean not null default true,
  updated_at      bigint not null check (updated_at >= 0),
  deleted_at      bigint check (deleted_at is null or deleted_at >= 0)
);
comment on table public.periods is 'Named date ranges whose hours replace the defaults while active, e.g. Ramadan.';
create index periods_user_days_idx on public.periods (user_id, start_epoch_day, end_epoch_day);
create index periods_user_updated_at_idx on public.periods (user_id, updated_at);

-- ---------------------------------------------------------------------------------------------
-- Row Level Security: a user can only touch their own rows
-- ---------------------------------------------------------------------------------------------
alter table public.settings enable row level security;
alter table public.entries  enable row level security;
alter table public.holidays enable row level security;
alter table public.periods  enable row level security;

create policy "own rows" on public.settings
  for all to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create policy "own rows" on public.entries
  for all to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create policy "own rows" on public.holidays
  for all to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create policy "own rows" on public.periods
  for all to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

-- Expose the tables to the Data API for signed-in users only. The app signs in (anonymously or
-- with Google) before it syncs, so the anon role gets nothing.
grant usage on schema public to authenticated;
grant select, insert, update, delete on public.settings, public.entries, public.holidays, public.periods to authenticated;
revoke all on public.settings, public.entries, public.holidays, public.periods from anon;
-- Supabase's default privileges also grant these; RLS does not cover TRUNCATE, so drop them.
revoke truncate, trigger, references on public.settings, public.entries, public.holidays, public.periods from authenticated;
