# RetroVault — Supabase backend

Postgres catalog + Auth + Storage + RLS for the RetroVault storefront.

- **Project ref:** `mxasjicdkryaqugrccdo`
- **Region:** us-east-1
- **API URL:** https://mxasjicdkryaqugrccdo.supabase.co
- The **publishable/anon key** is safe to embed in the client (RLS enforces access); see
  `app/.../data/remote/SupabaseConfig.kt`. The **service_role** key and DB password are secrets
  and must never be committed or shipped in the app.

## Schema

`systems` · `games` (published catalog) · `profiles` · `user_library` · `downloads` ·
`save_states` · `memory_cards`. See [`migrations/`](migrations/).

### RLS model
- `systems`, `games`: world-readable (`games` only where `is_published`). Writes are
  **service-role only** (catalog is admin-managed).
- `profiles`: owner read/update; rows auto-created by an `auth.users` insert trigger.
- `user_library`, `downloads`, `save_states`, `memory_cards`: full control scoped to
  `auth.uid() = user_id`.

### Storage buckets
- `box-art` — public (CDN-cached).
- `game-files` — private (large legal payloads primarily live on Cloudflare R2; this is a
  fallback / small-file bucket).
- `saves` — private; users can only touch objects under their own `{uid}/` folder.

## Applying migrations

Migrations here mirror what was applied via the Supabase MCP. To re-apply to a fresh project,
run them in order (`0001` → `0004`) with the Supabase CLI:

```
supabase link --project-ref mxasjicdkryaqugrccdo
supabase db push
```

> Never store console BIOS/firmware or copyrighted ROMs in any bucket. Only legally
> distributable game files (homebrew / public-domain / freeware / licensed) are hosted.
