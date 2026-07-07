// verify-purchase — server-side verification of a Pulsar Gold purchase (P21).
//
// The `full`-flavor PlayBillingManager POSTs { packageName, productId, purchaseToken } here after a
// Play purchase. This function mints a Google OAuth token from a Play-linked service account and
// calls the Play Developer API purchases.products.get, granting Gold ONLY when Google reports the
// token as purchased (purchaseState === 0). The client's local entitlement is only ever a cache of
// what this endpoint confirmed — a stolen/forged token can't unlock Gold.
//
// STAGED: needs the secret GOOGLE_PLAY_SA_JSON (a Play Console-linked service-account key) and the
// app published to a Play track. Without the secret it FAILS CLOSED ({ verified: false }), so it is
// safe to deploy now. verify_jwt is disabled (the client sends no user JWT).

const SA_JSON = Deno.env.get("GOOGLE_PLAY_SA_JSON") ?? "";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function b64url(bytes: Uint8Array): string {
  let s = btoa(String.fromCharCode(...bytes));
  return s.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
function b64urlStr(str: string): string {
  return b64url(new TextEncoder().encode(str));
}

function pemToDer(pem: string): Uint8Array {
  const body = pem.replace(/-----BEGIN [^-]+-----/, "").replace(/-----END [^-]+-----/, "").replace(/\s+/g, "");
  const raw = atob(body);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

// Mint a short-lived OAuth access token for the androidpublisher scope via the SA JWT-bearer grant.
async function getAccessToken(sa: { client_email: string; private_key: string }): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claim = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const unsigned = `${b64urlStr(JSON.stringify(header))}.${b64urlStr(JSON.stringify(claim))}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const jwt = `${unsigned}.${b64url(new Uint8Array(sig))}`;
  const resp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });
  const data = await resp.json();
  if (!resp.ok || !data.access_token) throw new Error("oauth token exchange failed");
  return data.access_token as string;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method not allowed" }, 405);

  const { packageName, productId, purchaseToken } = await req.json().catch(() => ({}));
  if (!packageName || !productId || !purchaseToken) {
    return json({ error: "packageName, productId, purchaseToken required" }, 400);
  }

  // Fail closed until the service account is provisioned.
  if (!SA_JSON) return json({ verified: false, staged: true });

  try {
    const sa = JSON.parse(SA_JSON);
    const token = await getAccessToken(sa);
    const url =
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}` +
      `/purchases/products/${productId}/tokens/${encodeURIComponent(purchaseToken)}`;
    const resp = await fetch(url, { headers: { authorization: `Bearer ${token}` } });
    if (!resp.ok) return json({ verified: false }); // never grant on a non-200 from Google
    const purchase = await resp.json();
    // purchaseState: 0 = purchased, 1 = canceled, 2 = pending.
    const verified = purchase.purchaseState === 0;
    return json({ verified, entitlement: verified ? "gold" : null });
  } catch (_e) {
    return json({ verified: false }, 200); // fail closed on any error
  }
});
