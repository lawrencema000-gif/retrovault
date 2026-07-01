-- Enable RLS on all tables
alter table public.systems enable row level security;
alter table public.games enable row level security;
alter table public.profiles enable row level security;
alter table public.user_library enable row level security;
alter table public.downloads enable row level security;
alter table public.save_states enable row level security;
alter table public.memory_cards enable row level security;

-- Public, read-only catalog. Writes are service-role only (no policy = only service_role bypass).
create policy "systems_public_read" on public.systems
  for select using (true);

create policy "games_public_read" on public.games
  for select using (is_published);

-- Profiles: owner can read + update their own row (insert handled by trigger).
create policy "profiles_select_own" on public.profiles
  for select using (auth.uid() = id);
create policy "profiles_update_own" on public.profiles
  for update using (auth.uid() = id) with check (auth.uid() = id);

-- Per-user tables: owner has full control over their own rows.
create policy "user_library_all_own" on public.user_library
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "downloads_all_own" on public.downloads
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "save_states_all_own" on public.save_states
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "memory_cards_all_own" on public.memory_cards
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Storage buckets: box-art public (CDN), game-files + saves private.
insert into storage.buckets (id, name, public)
values ('box-art', 'box-art', true),
       ('game-files', 'game-files', false),
       ('saves', 'saves', false)
on conflict (id) do nothing;

-- Saves bucket: each user may only touch objects under their own {uid}/ folder.
create policy "saves_select_own" on storage.objects
  for select using (bucket_id = 'saves' and auth.uid()::text = (storage.foldername(name))[1]);
create policy "saves_insert_own" on storage.objects
  for insert with check (bucket_id = 'saves' and auth.uid()::text = (storage.foldername(name))[1]);
create policy "saves_update_own" on storage.objects
  for update using (bucket_id = 'saves' and auth.uid()::text = (storage.foldername(name))[1]);
create policy "saves_delete_own" on storage.objects
  for delete using (bucket_id = 'saves' and auth.uid()::text = (storage.foldername(name))[1]);

-- box-art is a public bucket (world-readable); client writes are service-role only.
