# Deploy CYTV activation service

This directory contains the first deployable Cloudflare Worker scaffold and the mobile admin page.

## Prerequisites

- A Cloudflare account with Workers and D1 enabled.
- Node.js and Wrangler CLI.
- A public hostname is optional; the initial deployment can use a workers.dev hostname.

## 1. Create the D1 database

From this directory:

```sh
npx wrangler d1 create cytv-activation
```

Copy the returned database ID into `wrangler.toml` in place of `REPLACE_WITH_CLOUDFLARE_D1_DATABASE_ID`.

Apply the schema:

```sh
npx wrangler d1 execute cytv-activation --remote --file=./schema.sql
```

## 2. Create signing keys

The activation receipt is an ES256 (ECDSA P-256) signed token. The private key stays in Cloudflare; the corresponding public key will be embedded in the Android application for offline verification.

Generate a private key and public key:

```sh
openssl ecparam -name prime256v1 -genkey -noout -out cytv-receipt-private.pem
openssl pkcs8 -topk8 -nocrypt -in cytv-receipt-private.pem -outform DER | base64 -w0
openssl pkey -in cytv-receipt-private.pem -pubout -outform DER | base64 -w0
```

Keep the private PEM and PKCS#8 output secret. Do not commit either to GitHub. Keep the public SPKI output for the Android verifier implementation.

## 3. Configure Worker secrets

Use strong, randomly generated values. Do not put them in source files or chat.

```sh
npx wrangler secret put CYTV_ADMIN_KEY
npx wrangler secret put OTP_PEPPER
npx wrangler secret put RECEIPT_PRIVATE_KEY_PKCS8
npx wrangler secret put RECEIPT_KEY_ID
```

For `RECEIPT_PRIVATE_KEY_PKCS8`, paste the base64 PKCS#8 DER output from step 2. For `RECEIPT_KEY_ID`, use a non-secret key label such as `cytv-activation-2026-01`.

## 4. Deploy

```sh
npx wrangler deploy
```

The Worker serves the admin page at the root URL and exposes:

- `POST /api/admin/code` — administrator-only code generation.
- `POST /api/activate` — one-time six-digit code redemption bound to an installation ID.
- `GET /api/health` — health check.

## 5. Smoke test

Open the deployed root URL on a phone, enter the administrator key, and generate a code. Confirm the countdown is five minutes. Do not use a production code for test scripts or screenshots.

The API can be checked with:

```sh
curl https://YOUR-WORKER.workers.dev/api/health
```

## Security and release gate

This is a deployment scaffold, not a production-ready release yet. Before using it with real devices:

- Add and test server-side throttling for administrator code generation and redemption attempts.
- Add an automated test suite for expiry, single-use, concurrent redemption, invalid attempts, and installation binding.
- Implement Android installation-key creation, ES256 receipt verification against the public key, and protected local receipt storage.
- Verify that backup/restore cannot silently restore activation state.
- Configure Cloudflare Access or another strong administrator login layer in addition to the admin secret.
- Confirm the D1 schema and Worker behavior in a non-production Cloudflare environment.

Never publish the administrator key, OTP pepper, or receipt private key.
