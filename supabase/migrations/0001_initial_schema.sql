-- RetroVault initial schema
-- Enums
create type public.game_system_kind as enum ('psp','ps1','ps2');
create type public.game_license as enum (
  'homebrew','public_domain','freeware','cc','gpl','mit','bsd','user_owned','demo'
);
create type public.storage_provider as enum ('r2','supabase','external');
create type public.download_status as enum ('queued','downloading','installed','failed','paused');
create type public.user_tier as enum ('free','gold');

-- systems (PSP/PS1/PS2)
create table public.systems (
  id text primary key,                        -- 'psp' | 'ps1' | 'ps2'
  slug text not null unique,
  name text not null,
  core_lib_name text,                          -- e.g. 'ppsspp_libretro_android.so'
  requires_bios boolean not null default false,
  min_ram_mb integer not null default 0,
  sort_order integer not null default 0,
  created_at timestamptz not null default now()
);

-- catalog of legally-distributable games (admin-managed via service role)
create table public.games (
  id uuid primary key default gen_random_uuid(),
  system_id text not null references public.systems(id) on delete restrict,
  title text not null,
  slug text not null unique,
  developer text,
  description text,
  license public.game_license not null,
  license_url text,
  source_url text,                             -- GPL corresponding-source / repo link
  storage_provider public.storage_provider,
  storage_key text,                            -- key/path in R2 or Supabase bucket (null until hosted)
  download_url text,                           -- optional direct URL (only if legally hostable there)
  download_size_bytes bigint not null default 0,
  sha256 text,
  box_art_url text,
  region text,
  version text,
  is_published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index games_system_idx on public.games(system_id);
create index games_published_idx on public.games(is_published) where is_published;

-- one profile row per auth user
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  avatar_url text,
  tier public.user_tier not null default 'free',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- a user's saved/added games
create table public.user_library (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  game_id uuid not null references public.games(id) on delete cascade,
  added_at timestamptz not null default now(),
  unique (user_id, game_id)
);
create index user_library_user_idx on public.user_library(user_id);

-- download/install state per user+game
create table public.downloads (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  game_id uuid not null references public.games(id) on delete cascade,
  status public.download_status not null default 'queued',
  bytes_downloaded bigint not null default 0,
  local_path text,
  installed_at timestamptz,
  updated_at timestamptz not null default now(),
  unique (user_id, game_id)
);
create index downloads_user_idx on public.downloads(user_id);

-- cloud-synced save states (fragile across core versions)
create table public.save_states (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  game_id uuid not null references public.games(id) on delete cascade,
  slot integer not null,
  storage_key text not null,
  size_bytes bigint not null default 0,
  screenshot_key text,
  core_version text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, game_id, slot)
);
create index save_states_user_idx on public.save_states(user_id);

-- durable memory-card / SRAM per user+system
create table public.memory_cards (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  system_id text not null references public.systems(id) on delete restrict,
  storage_key text not null,
  size_bytes bigint not null default 0,
  updated_at timestamptz not null default now(),
  unique (user_id, system_id)
);
create index memory_cards_user_idx on public.memory_cards(user_id);

-- updated_at maintenance
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger games_updated_at before update on public.games
  for each row execute function public.set_updated_at();
create trigger profiles_updated_at before update on public.profiles
  for each row execute function public.set_updated_at();
create trigger downloads_updated_at before update on public.downloads
  for each row execute function public.set_updated_at();
create trigger save_states_updated_at before update on public.save_states
  for each row execute function public.set_updated_at();
create trigger memory_cards_updated_at before update on public.memory_cards
  for each row execute function public.set_updated_at();

-- auto-create a profile when a user signs up
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data->>'full_name', split_part(new.email, '@', 1)))
  on conflict (id) do nothing;
  return new;
end;
$$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();
