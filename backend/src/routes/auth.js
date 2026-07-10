const express = require('express');
const router = express.Router();
const { Resend } = require('resend');
const admin = require('../firebase');
const db = require('../db');
const { v4: uuidv4 } = require('uuid');

const resend = new Resend(process.env.RESEND_API_KEY);

function generateOTP() {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

// POST /api/auth/send-code
router.post('/send-code', async (req, res) => {
  const { email } = req.body;
  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return res.status(400).json({ error: 'Geçersiz e-posta adresi' });
  }

  const code = generateOTP();
  const expiresAt = Math.floor(Date.now() / 1000) + 600; // 10 min

  // Invalidate old codes
  db.prepare('UPDATE otp_codes SET used=1 WHERE email=? AND used=0').run(email);

  // Insert new
  db.prepare('INSERT INTO otp_codes (email, code, expires_at) VALUES (?, ?, ?)')
    .run(email, code, expiresAt);

  try {
    await resend.emails.send({
      from: 'Velaud <noreply@velaud.app>',
      to: email,
      subject: `${code} - Velaud Doğrulama Kodunuz`,
      html: `
        <div style="font-family:Inter,sans-serif;max-width:480px;margin:0 auto;padding:32px;background:#F3EEE6;border-radius:16px;">
          <h1 style="font-size:28px;color:#2B2621;margin-bottom:8px;">Velaud Doğrulama</h1>
          <p style="color:#8C8175;font-size:15px;margin-bottom:24px;">Giriş yapmak için aşağıdaki kodu kullanın:</p>
          <div style="background:#FFFFFF;border:2px solid #C96442;border-radius:14px;padding:20px 32px;text-align:center;margin-bottom:24px;">
            <span style="font-size:42px;font-weight:700;letter-spacing:12px;color:#C96442;">${code}</span>
          </div>
          <p style="color:#8C8175;font-size:13px;">Bu kod 10 dakika geçerlidir. Siz bu isteği yapmadıysanız bu e-postayı dikkate almayın.</p>
        </div>
      `
    });

    res.json({ success: true, message: 'Kod gönderildi' });
  } catch (err) {
    console.error('Resend error:', err);
    res.status(500).json({ error: 'E-posta gönderilemedi' });
  }
});

// POST /api/auth/verify-code
router.post('/verify-code', async (req, res) => {
  const { email, code } = req.body;
  if (!email || !code) return res.status(400).json({ error: 'Eksik alanlar' });

  const now = Math.floor(Date.now() / 1000);
  const row = db.prepare(
    'SELECT * FROM otp_codes WHERE email=? AND code=? AND used=0 AND expires_at>? ORDER BY created_at DESC LIMIT 1'
  ).get(email, code.trim(), now);

  if (!row) {
    return res.status(401).json({ error: 'Geçersiz veya süresi dolmuş kod' });
  }

  // Mark used
  db.prepare('UPDATE otp_codes SET used=1 WHERE id=?').run(row.id);

  // Upsert user
  let user = db.prepare('SELECT * FROM users WHERE email=?').get(email);
  if (!user) {
    const uid = uuidv4();
    db.prepare('INSERT INTO users (id, email, display_name) VALUES (?, ?, ?)').run(uid, email, email.split('@')[0]);
    user = db.prepare('SELECT * FROM users WHERE id=?').get(uid);
  }
  db.prepare('UPDATE users SET last_seen=strftime(\'%s\',\'now\') WHERE id=?').run(user.id);

  // Create Firebase custom token
  try {
    const firebaseUid = `email_${user.id}`;
    const customToken = await admin.auth().createCustomToken(firebaseUid, {
      email: user.email,
      displayName: user.display_name
    });

    res.json({ success: true, customToken, userId: user.id, displayName: user.display_name });
  } catch (err) {
    console.error('Firebase token error:', err);
    res.status(500).json({ error: 'Token oluşturulamadı' });
  }
});

// POST /api/auth/sync-google (called after Google sign-in to register user)
router.post('/sync-google', async (req, res) => {
  const { idToken } = req.body;
  if (!idToken) return res.status(400).json({ error: 'Token gerekli' });

  try {
    const decoded = await admin.auth().verifyIdToken(idToken);
    const { uid, email, name, picture } = decoded;

    let user = db.prepare('SELECT * FROM users WHERE firebase_uid=?').get(uid);
    if (!user) {
      const id = uuidv4();
      db.prepare('INSERT OR IGNORE INTO users (id, email, display_name, photo_url, firebase_uid) VALUES (?, ?, ?, ?, ?)')
        .run(id, email, name || email?.split('@')[0], picture || null, uid);
      user = db.prepare('SELECT * FROM users WHERE id=?').get(id);
    } else {
      db.prepare('UPDATE users SET last_seen=strftime(\'%s\',\'now\'), display_name=?, photo_url=? WHERE id=?')
        .run(name || user.display_name, picture || user.photo_url, user.id);
    }

    res.json({ success: true, userId: user.id, displayName: user.display_name });
  } catch (err) {
    console.error('Sync google error:', err);
    res.status(401).json({ error: 'Token doğrulanamadı' });
  }
});

module.exports = router;
