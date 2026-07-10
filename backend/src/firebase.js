const admin = require('firebase-admin');
const path = require('path');

if (!admin.apps.length) {
  let credential;

  if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
    // Injected as env var (Replit secrets)
    const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON);
    credential = admin.credential.cert(serviceAccount);
  } else {
    // Local file fallback
    const serviceAccountPath = path.join(__dirname, '../firebase-adminsdk.json');
    credential = admin.credential.cert(require(serviceAccountPath));
  }

  admin.initializeApp({ credential });
}

module.exports = admin;
