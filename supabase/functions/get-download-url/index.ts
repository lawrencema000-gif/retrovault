// get-download-url — issues a short-TTL signed download URL for a PUBLISHED, legally-distributable
// game. Verifies eligibility server-side (service role) before signing. Large payloads sign against
// Cloudflare R2 (added in the functional pass); smaller/legal files sign against Supabase Storage.
//
// Never signs anything that isn't is_published, and never touches BIOS/copyrighted content.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method not allowed" }, 405);

  try {
    const { game_id } = await req.json().catch(() => ({}));
    if (!game_id) return json({ error: "game_id required" }, 400);

    const admin = createClient(SUPABASE_URL, SERVICE_ROLE);
    const { data: game, error } = await admin
      .from("games")
      .select("id, slug, storage_provider, storage_key, download_url, download_size_bytes, sha256, is_published")
      .eq("id", game_id)
      .eq("is_published", true)
      .single();

    if (error || !game) return json({ error: "not found or not published" }, 404);

    // Direct URL — only for content that is legally hostable at that location.
    if (game.storage_provider === "external" && game.download_url) {
      return json(signed(game, game.download_url));
    }

    // Supabase Storage signed URL (5-minute TTL).
    if (game.storage_provider === "supabase" && game.storage_key) {
      const { data, error: se } = await admin.storage
        .from("game-files")
        .createSignedUrl(game.storage_key, 300);
      if (se || !data) return json({ error: "sign failed" }, 500);
      return json(signed(game, data.signedUrl));
    }

    // TODO(functional pass): presign a Cloudflare R2 GET (zero egress) for large game payloads.
    return json({ error: "no download available yet" }, 409);
  } catch (e) {
    return json({ error: String(e) }, 500);
  }
});

function signed(game: Record<string, unknown>, url: string) {
  const key = (game.storage_key as string | null) ?? "";
  return {
    url,
    sha256: (game.sha256 as string | null) ?? null,
    size: (game.download_size_bytes as number | null) ?? 0,
    file_name: key.split("/").pop() || `${game.slug}.bin`,
  };
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
