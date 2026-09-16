import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import { RateLimiterMemory } from 'rate-limiter-flexible';
import admin from 'firebase-admin';
import crypto from 'crypto';
import dotenv from 'dotenv';

dotenv.config();

// Initialize Firebase Admin SDK
if (!admin.apps.length) {
  const serviceAccount = process.env.FIREBASE_SERVICE_ACCOUNT
    ? JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)
    : undefined;

  admin.initializeApp({
    credential: serviceAccount ? admin.credential.cert(serviceAccount) : admin.credential.applicationDefault(),
  });
}

const db = admin.firestore();
const auth = admin.auth();
const app = express();

app.use(helmet());
app.use(cors({ origin: true }));
app.use(express.json());

// Rate limiters
const otpSendLimiter = new RateLimiterMemory({
  points: 3, // 3 requests
  duration: 60 * 10, // per 10 minutes
  blockDuration: 60 * 30, // block for 30 minutes if exceeded
});

const otpVerifyLimiter = new RateLimiterMemory({
  points: 10, // 10 attempts
  duration: 60 * 10, // per 10 minutes
  blockDuration: 60 * 10, // block for 10 minutes
});

// In-memory OTP store (replace with Firestore/Redis in production)
// Key: uid, Value: { code, expiresAt, attempts, used }
const otpStore = new Map();

// Generate cryptographically secure 6-digit code
function generateOtp() {
  return crypto.randomInt(100000, 999999).toString().padStart(6, '0');
}

// Send email via Firebase Auth's custom email (or use SendGrid/Nodemailer in production)
async function sendOtpEmail(email, code) {
  // Option 1: Use Firebase Admin to send custom email (requires custom email template setup)
  // Option 2: Use SendGrid, Mailgun, Nodemailer, etc.
  // For now, log it - replace with real email service
  console.log(`[OTP EMAIL] To: ${email} | Code: ${code} | Expires in 10 min`);

  // TODO: Integrate with your email provider (SendGrid, Mailgun, Nodemailer, etc.)
  // Example with Nodemailer:
  // await transporter.sendMail({
  //   from: '"Omni" <noreply@omni.app>',
  //   to: email,
  //   subject: 'Your Omni verification code',
  //   text: `Your verification code is: ${code}\n\nThis code expires in 10 minutes.`,
  //   html: `<p>Your verification code is: <strong>${code}</strong></p><p>This code expires in 10 minutes.</p>`,
  // });
}

// POST /otp/send - Send 6-digit OTP to user's email
app.post('/otp/send', async (req, res) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader?.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Missing or invalid Authorization header' });
    }

    const idToken = authHeader.split('Bearer ')[1];
    const decoded = await auth.verifyIdToken(idToken);
    const uid = decoded.uid;
    const email = decoded.email;

    if (!email) {
      return res.status(400).json({ error: 'User has no email address' });
    }

    // Check rate limit
    await otpSendLimiter.consume(uid);

    // Generate OTP
    const code = generateOtp();
    const expiresAt = Date.now() + 10 * 60 * 1000; // 10 minutes

    // Store OTP (in production, use Firestore with TTL index)
    otpStore.set(uid, {
      code,
      expiresAt,
      attempts: 0,
      used: false,
      email,
    });

    // Also persist to Firestore for durability
    await db.collection('otp_codes').doc(uid).set({
      code,
      expiresAt: admin.firestore.Timestamp.fromMillis(expiresAt),
      attempts: 0,
      used: false,
      email,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    // Send email
    await sendOtpEmail(email, code);

    res.json({ success: true, message: 'Verification code sent' });
  } catch (err) {
    if (err instanceof Error && err.message.includes('Rate limiter')) {
      return res.status(429).json({ error: 'Too many requests. Please wait before resending.' });
    }
    console.error('OTP send error:', err);
    res.status(500).json({ error: 'Failed to send verification code' });
  }
});

// POST /otp/verify - Verify 6-digit OTP
app.post('/otp/verify', async (req, res) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader?.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Missing or invalid Authorization header' });
    }

    const idToken = authHeader.split('Bearer ')[1];
    const decoded = await auth.verifyIdToken(idToken);
    const uid = decoded.uid;

    const { code } = req.body;
    if (!code || code.length !== 6 || !/^\d{6}$/.test(code)) {
      return res.status(400).json({ error: 'Invalid code format' });
    }

    // Check rate limit
    await otpVerifyLimiter.consume(uid);

    // Get stored OTP (try memory first, then Firestore)
    let otpData = otpStore.get(uid);

    if (!otpData) {
      const doc = await db.collection('otp_codes').doc(uid).get();
      if (doc.exists) {
        otpData = doc.data();
        otpStore.set(uid, otpData);
      }
    }

    if (!otpData) {
      return res.status(400).json({ error: 'No code found. Please request a new one.' });
    }

    // Check expiration
    if (Date.now() > otpData.expiresAt) {
      otpStore.delete(uid);
      await db.collection('otp_codes').doc(uid).delete();
      return res.status(400).json({ error: 'Code expired. Please request a new one.' });
    }

    // Check if already used
    if (otpData.used) {
      return res.status(400).json({ error: 'Code already used. Please request a new one.' });
    }

    // Increment attempts
    otpData.attempts += 1;
    otpStore.set(uid, otpData);
    await db.collection('otp_codes').doc(uid).update({ attempts: otpData.attempts });

    // Check max attempts
    if (otpData.attempts > 5) {
      otpStore.delete(uid);
      await db.collection('otp_codes').doc(uid).delete();
      return res.status(400).json({ error: 'Too many attempts. Please request a new code.' });
    }

    // Verify code
    if (otpData.code !== code) {
      return res.status(400).json({ error: 'Incorrect code. Please try again.' });
    }

    // Mark as used
    otpData.used = true;
    otpStore.set(uid, otpData);
    await db.collection('otp_codes').doc(uid).update({ used: true });

    // Mark Firebase user's email as verified
    await auth.updateUser(uid, { emailVerified: true });

    // Clean up
    otpStore.delete(uid);
    await db.collection('otp_codes').doc(uid).delete();

    res.json({ success: true, verified: true, message: 'Email verified successfully' });
  } catch (err) {
    if (err instanceof Error && err.message.includes('Rate limiter')) {
      return res.status(429).json({ error: 'Too many verification attempts. Please wait.' });
    }
    console.error('OTP verify error:', err);
    res.status(500).json({ error: 'Failed to verify code' });
  }
});

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`OTP backend running on port ${PORT}`);
});