-- Systems
insert into public.systems (id, slug, name, core_lib_name, requires_bios, min_ram_mb, sort_order) values
  ('psp', 'psp', 'PlayStation Portable', 'ppsspp_libretro_android.so',      false, 2048, 1),
  ('ps1', 'ps1', 'PlayStation',          'swanstation_libretro_android.so',  true, 2048, 2),
  ('ps2', 'ps2', 'PlayStation 2',        'armsx2_libretro_android.so',       true, 6144, 3)
on conflict (id) do nothing;

-- Placeholder catalog (published so the storefront shows data; downloads disabled until files are
-- hosted on R2 and licenses verified). These are EXAMPLES, not a vetted Tier-1 list yet.
insert into public.games
  (system_id, title, slug, developer, description, license, source_url, download_size_bytes, is_published)
values
  ('psp', 'Sample PSP Homebrew', 'sample-psp-homebrew', 'Homebrew Community',
   'Placeholder entry for a freely redistributable PSP homebrew title. Real catalog data replaces this.',
   'gpl', null, 12582912, true),
  ('psp', 'Open Card Battler', 'open-card-battler', 'Open Source',
   'Placeholder open-source PSP card game. Redistribution permitted under its license.',
   'gpl', null, 35651584, true),
  ('ps1', 'Public Domain Racer', 'public-domain-racer', 'PSXDEV Community',
   'Placeholder PS1 homebrew racing demo released into the public domain.',
   'public_domain', null, 8388608, true),
  ('ps1', 'Net Yaroze Sampler', 'net-yaroze-sampler', 'Yaroze Authors',
   'Placeholder collection of freely shared Net Yaroze homebrew games.',
   'freeware', null, 5242880, true),
  ('ps2', 'PS2 Homebrew Demo', 'ps2-homebrew-demo', 'PS2DEV Community',
   'Placeholder PS2 homebrew tech demo. Freely redistributable under its stated license.',
   'mit', null, 50331648, true),
  ('ps2', 'Open Puzzle Deluxe', 'open-puzzle-deluxe', 'Indie (CC-BY)',
   'Placeholder Creative Commons PS2 puzzle game the developer permits redistributing.',
   'cc', null, 100663296, true)
on conflict (slug) do nothing;
