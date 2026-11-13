const CODE_TTL_SECONDS = 300;
const CODE_LENGTH = 6;

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

function b64url(bytes) {
  let binary = "";
  for (const byte of new Uint8Array(bytes)) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

async function sha256Hex(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map(v => v.toString(16).padStart(2, "0")).join("");
}

function randomCode() {
  const limit = Math.floor(0x100000000 / 1_000_000) * 1_000_000;
  const buf = new Uint32Array(1);
  let value;
  do { crypto.getRandomValues(buf); value = buf[0]; } while (value >= limit);
  return String(value % 1_000_000).padStart(CODE_LENGTH, "0");
}

function randomId() {
  return crypto.randomUUID();
}

function isAdmin(request, env) {
  const supplied = request.headers.get("authorization") || "";
  return !!env.CYTV_ADMIN_KEY && supplied === `Bearer ${env.CYTV_ADMIN_KEY}`;
}

async function signReceipt(installationId, activationId, env) {
  if (!env.RECEIPT_PRIVATE_KEY_PKCS8 || !env.RECEIPT_KEY_ID) {
    throw new Error("Activation signing key is not configured");
  }
  const keyBytes = Uint8Array.from(atob(env.RECEIPT_PRIVATE_KEY_PKCS8), c => c.charCodeAt(0));
  const privateKey = await crypto.subtle.importKey(
    "pkcs8", keyBytes, { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]
  );
  const header = { alg: "ES256", typ: "JWT", kid: env.RECEIPT_KEY_ID };
  const payload = {
    iss: "CYTV",
    aud: "CYTV-Launcher",
    sub: installationId,
    activation_id: activationId,
    activated_at: Math.floor(Date.now() / 1000),
    permanent: true
  };
  const unsigned = `${b64url(new TextEncoder().encode(JSON.stringify(header)))}.${b64url(new TextEncoder().encode(JSON.stringify(payload)))}`;
  const signature = await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" }, privateKey, new TextEncoder().encode(unsigned)
  );
  return `${unsigned}.${b64url(signature)}`;
}

async function handleGenerate(request, env) {
  if (!isAdmin(request, env)) return json({ error: "unauthorized" }, 401);
  const code = randomCode();
  const now = Math.floor(Date.now() / 1000);
  const expiresAt = now + CODE_TTL_SECONDS;
  const codeHash = await sha256Hex(`${env.OTP_PEPPER}:${code}`);
  await env.DB.prepare(
    "INSERT INTO activation_codes (code_hash, created_at, expires_at) VALUES (?, ?, ?)"
  ).bind(codeHash, now, expiresAt).run();
  return json({ code, expiresAt, ttlSeconds: CODE_TTL_SECONDS });
}

async function handleActivate(request, env) {
  const body = await request.json().catch(() => null);
  const code = String(body?.code || "").trim();
  const installationId = String(body?.installationId || "").trim();
  if (!/^\d{6}$/.test(code) || !/^[A-Za-z0-9-]{16,80}$/.test(installationId)) {
    return json({ error: "invalid_request" }, 400);
  }
  const now = Math.floor(Date.now() / 1000);
  const codeHash = await sha256Hex(`${env.OTP_PEPPER}:${code}`);
  const activationId = randomId();

  // D1 batch is transactional: a code can be consumed by only one installation.
  await env.DB.batch([
    env.DB.prepare(
      "UPDATE activation_codes SET used_at = ?, installation_id = ?, activation_id = ? WHERE code_hash = ? AND used_at IS NULL AND expires_at > ?"
    ).bind(now, installationId, activationId, codeHash, now),
    env.DB.prepare(
      "INSERT INTO activations (activation_id, installation_id, activated_at) SELECT activation_id, installation_id, used_at FROM activation_codes WHERE activation_id = ? AND used_at = ?"
    ).bind(activationId, now)
  ]);

  const row = await env.DB.prepare(
    "SELECT activation_id, installation_id, activated_at FROM activations WHERE activation_id = ? AND installation_id = ?"
  ).bind(activationId, installationId).first();

  if (!row) return json({ error: "invalid_or_expired_code" }, 403);

  const receipt = await signReceipt(installationId, activationId, env);
  return json({ activated: true, permanent: true, receipt });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: {
        "access-control-allow-origin": url.origin,
        "access-control-allow-methods": "GET, POST, OPTIONS",
        "access-control-allow-headers": "content-type, authorization",
      }});
    }
    if (url.pathname === "/api/admin/code" && request.method === "POST") {
      return handleGenerate(request, env);
    }
    if (url.pathname === "/api/activate" && request.method === "POST") {
      return handleActivate(request, env);
    }
    if (url.pathname === "/api/health" && request.method === "GET") {
      return json({ service: "CYTV activation", status: "ok" });
    }
    if (env.ASSETS) return env.ASSETS.fetch(request);
    return json({ error: "not_found" }, 404);
  }
};
