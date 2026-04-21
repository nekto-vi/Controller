# ImageKit Integration Guide (EV)

This project stores scenario records in Firestore and image files in ImageKit.

## 0) Security first (required)

1. If `public` / `private` keys were exposed anywhere, rotate both in ImageKit Dashboard.
2. Never put `privateKey` into Android code, BuildConfig, repository, or screenshots.
3. Keep secrets only in:
   - Firebase Functions secret storage (`IMAGEKIT_PRIVATE_KEY`)
   - local machine user-level Gradle properties (public key + endpoint only)

## 1) Create ImageKit credentials

1. Open [ImageKit Dashboard](https://imagekit.io/dashboard/developer/api-keys).
2. Copy:
   - `publicKey` (safe for Android app)
   - `privateKey` (server-only)
   - `urlEndpoint` (optional)

## 2) Create secure auth endpoint (free option recommended)

Android app uploads via signed parameters (`token`, `expire`, `signature`), generated on backend.

### Option A (recommended, no paid Firebase): Cloudflare Worker

Use:
- [`cloudflare-worker/imagekit-auth.js`](cloudflare-worker/imagekit-auth.js)
- setup guide: [`cloudflare-worker/README.md`](cloudflare-worker/README.md)

This works on Cloudflare free tier and avoids Firebase Blaze requirement.

### Option B (optional): Firebase Functions

Use template:
- [`firebase-functions/imageKitAuth/index.js`](firebase-functions/imageKitAuth/index.js)

Note: Firebase secrets/functions deploy may require Blaze plan.

## 3) Configure Android build properties (local only)

Add to `%USERPROFILE%\\.gradle\\gradle.properties` (user-level file, outside repo):

```properties
IMAGEKIT_PUBLIC_KEY=public_xxxxx
IMAGEKIT_AUTH_ENDPOINT=https://imagekit-auth-worker.<your-subdomain>.workers.dev
```

The app reads these values in:
- [`app/build.gradle.kts`](app/build.gradle.kts)

Exposed to app as:
- `BuildConfig.IMAGEKIT_PUBLIC_KEY`
- `BuildConfig.IMAGEKIT_AUTH_ENDPOINT`

## 4) Firestore data model

Each scenario document stores:
- `imageUrl` (public URL from ImageKit)
- `imageFileId` (file id for future delete/update operations)

Legacy compatibility:
- if old docs still contain `imageUri`, repository reads it as fallback.

## 5) Runtime behavior

- User picks an image (`content://...`).
- On save/update, app uploads the file to ImageKit.
- Local URI is replaced by remote `imageUrl`.
- Firestore stores scenario + image metadata.
- UI displays image from `imageUrl` (cross-device).

## 6) Verify secrets are not in Git

From repo root run:

```bash
git status
git ls-files app/google-services.json
git ls-files gradle.properties
```

Expected:
- `app/google-services.json` must not be tracked.
- Do not store secrets in tracked files.
- Keep `.env`, `secrets.properties`, `*.keystore` ignored.

Also search for accidentally committed keys:

```bash
rg "private_[A-Za-z0-9+/=]+" -n .
rg "IMAGEKIT_PRIVATE_KEY|IMAGEKIT_PUBLIC_KEY" -n .
```

## 7) Demo checklist (for lab)

1. Create/edit scenario with image.
2. Firestore document has `imageUrl` + `imageFileId`.
3. ImageKit Media Library contains uploaded file.
4. App shows image after restart and on another device.
