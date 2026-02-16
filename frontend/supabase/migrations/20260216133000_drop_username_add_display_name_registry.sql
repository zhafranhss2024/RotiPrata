-- Ensure display_name is populated before dropping username
update public.profiles
set display_name = username
where display_name is null
  and username is not null;

-- Replace username registry view with display_name registry
drop view if exists public.username_registry;

drop view if exists public.display_name_registry;
create view public.display_name_registry as
select lower(display_name) as display_name
from public.profiles
where display_name is not null
  and btrim(display_name) <> '';

grant select on public.display_name_registry to anon, authenticated;

-- Drop legacy column
alter table public.profiles drop column if exists username;

-- Enforce display name uniqueness (case-insensitive)
create unique index if not exists profiles_display_name_lower_unique
on public.profiles (lower(display_name));
