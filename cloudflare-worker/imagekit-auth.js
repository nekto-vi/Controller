/**
 * Cloudflare Worker (free tier) for ImageKit upload auth.
 * Set secret:
 *   wrangler secret put IMAGEKIT_PRIVATE_KEY
 *
 * Deploy:
 *   wrangler deploy
 */

export default {
  async fetch(request, env) {
    if (request.method !== "GET") {
      return json({ error: "Method not allowed" }, 405);
    }

    const privateKey = env.IMAGEKIT_PRIVATE_KEY;
    if (!privateKey) {
      return json({ error: "IMAGEKIT_PRIVATE_KEY missing" }, 500);
    }

    const token = crypto.randomUUID();
    const expire = Math.floor(Date.now() / 1000) + 60 * 5;
    const signature = await hmacSha1Hex(privateKey, `${token}${expire}`);

    return json(
      { token, expire, signature },
      200,
      {
        "Cache-Control": "no-store",
        "Access-Control-Allow-Origin": "*",
      }
    );
  },
};

async function hmacSha1Hex(secret, message) {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-1" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, enc.encode(message));
  const bytes = new Uint8Array(signature);
  let hex = "";
  for (const b of bytes) {
    hex += b.toString(16).padStart(2, "0");
  }
  return hex;
}

function json(payload, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      "content-type": "application/json; charset=UTF-8",
      ...extraHeaders,
    },
  });
}
