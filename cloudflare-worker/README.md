# Cloudflare Worker for ImageKit Auth (No Firebase Blaze)

Use this if you want ImageKit upload signing without paid Firebase plan.

## 1) Install Wrangler CLI

```bash
npm install -g wrangler
wrangler login
```

## 2) Create Worker project (once)

```bash
mkdir imagekit-auth-worker
cd imagekit-auth-worker
wrangler init --yes
```

Replace generated worker code with contents of:
- [`imagekit-auth.js`](./imagekit-auth.js)

## 3) Add secret private key

```bash
wrangler secret put IMAGEKIT_PRIVATE_KEY
```

Paste your **new rotated** ImageKit private key.

## 4) Deploy

```bash
wrangler deploy
```

You will get URL like:

`https://imagekit-auth-worker.<your-subdomain>.workers.dev`

Use this URL in Android local gradle properties:

```properties
IMAGEKIT_AUTH_ENDPOINT=https://imagekit-auth-worker.<your-subdomain>.workers.dev
```

## 5) Verify endpoint

Open the worker URL in browser. It should return JSON:

```json
{"token":"...","expire":1234567890,"signature":"..."}
```

## 6) Security checklist

- Never commit private key.
- Rotate leaked keys in ImageKit Dashboard.
- Keep only public key and endpoint in `%USERPROFILE%\\.gradle\\gradle.properties`.
