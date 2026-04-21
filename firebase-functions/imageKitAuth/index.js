/**
 * Firebase Cloud Function (HTTP) to generate ImageKit upload auth params.
 *
 * Requirements:
 * 1) npm install firebase-functions crypto
 * 2) Store secret key (do NOT hardcode):
 *    firebase functions:secrets:set IMAGEKIT_PRIVATE_KEY
 * 3) Deploy:
 *    firebase deploy --only functions:imageKitAuth
 */
const functions = require("firebase-functions");
const crypto = require("crypto");

exports.imageKitAuth = functions
  .runWith({ secrets: ["IMAGEKIT_PRIVATE_KEY"] })
  .https.onRequest((req, res) => {
    if (req.method !== "GET") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    const privateKey = process.env.IMAGEKIT_PRIVATE_KEY;
    if (!privateKey) {
      res.status(500).json({ error: "IMAGEKIT_PRIVATE_KEY missing" });
      return;
    }

    const token = crypto.randomUUID();
    const expire = Math.floor(Date.now() / 1000) + 60 * 5;
    const signature = crypto
      .createHmac("sha1", privateKey)
      .update(token + expire)
      .digest("hex");

    res.set("Cache-Control", "no-store");
    res.json({ token, expire, signature });
  });
