import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import admin from 'firebase-admin';
import { RateLimiterMemory } from 'rate-limiter-flexible';
import { z } from 'zod';
import { config, validateConfig } from './config.js';
import { initializeSendGrid, sendOtpEmail, isSendGridConfigured } from './sendgrid.js';
import { generateOtp, hashOtp, generateEmailVerificationLink, formatOtpForDisplay, getRemainingSeconds, formatDuration } from './utils.js';
import { generateOtpEmailHtml, generateOtpEmailText } from './email-template.js';
import { SendOtpRequest, SendOtpResponse, VerifyOtpRequest, VerifyOtpResponse, HealthResponse, OtpDocument, RateLimitDocument } from './types.js';
import { FirestoreRateLimiter } from './rate-limiter.js';

// Initialize Firebase Admin SDK
if (!admin.apps.length) {
  admin.initializeApp({
    credential: config.firebaseServiceAccount ? admin.credential.cert(config.firebaseServiceAccount) : admin.credential.applicationDefault(),
  });
}

const db = admin.firestore();
const auth = admin.auth();
const app = express();

// Initialize SendGrid
initializeSendGrid();

// Middleware
app.use(helmet());
app.use(cors({ origin: true }));
app.use(express.json());

// Request validation schemas
const sendOtpSchema = z.object({});
const verifyOtpSchema = z.object({
  code: z.string().length(6).regex(/^\d{6}$/, 'Code must be 6 digits'),
});

// Validation middleware
function validateRequest<T>(schema: z.ZodSchema<T>) {
  return (req: Request, res: Response, next: NextFunction) => {
    const result = schema.safeParse(req.body);
    if (!result.success) {
      return res.status(400).json({ error: 'Invalid request', details: result.error.flatten() });
    }
    req.body = result.data;
    next();
  };
}

// Authentication middleware - verifies Firebase ID token and attaches decoded claims
async function authenticate(req: Request, res: Response, next: NextFunction): Promise<void> {
  const authHeader = req.headers.authorization;
  if (!authHeader?.startsWith('Bearer ')) {
    res.status(401).json({ error: 'Missing or invalid Authorization header' });
    return;
  }

  try {
    const idToken = authHeader.split('Bearer ')[1];
    const decoded = await auth.verifyIdToken(idToken);
    (req as any).user = decoded;
    // The token is never logged — the uid is enough to match a request against a user.
    console.log('[AUTH_TOKEN_VALID] uid=' + decoded.uid);
    next();
  } catch (error) {
    res.status(401).json({ error: 'Invalid or expired token' });
  }
}

// Initialize Firestore-backed rate limiters
const sendRateLimiter = new FirestoreRateLimiter(db, 'otp_rate_limits', 'send_', {
  points: config.rateLimits.send.points,
  durationSeconds: config.rateLimits.send.durationMinutes * 60,
  blockDurationSeconds: config.rateLimits.send.blockMinutes * 60,
});

const verifyRateLimiter = new FirestoreRateLimiter(db, 'otp_rate_limits', 'verify_', {
  points: config.rateLimits.verify.points,
  durationSeconds: config.rateLimits.verify.durationMinutes * 60,
  blockDurationSeconds: config.rateLimits.verify.blockMinutes * 60,
});

// POST /otp/send - Send 6-digit OTP to user's email
app.post('/otp/send', authenticate, validateRequest(sendOtpSchema), async (req: Request, res: Response) => {
  const uid = (req as any).user?.uid; // hoisted so the catch branch below can report it
  try {
    const user = (req as any).user;
    const email = user.email;

    console.log('[OTP_SEND_REQUEST_RECEIVED] uid=' + uid + ' email=' + email);

    if (!email) {
      return res.status(400).json({ error: 'User has no email address' });
    }

    // Fail closed before any rate-limit point is spent: if the mail provider is not configured
    // there is nothing to send, and answering 200 anyway is how this feature failed silently.
    if (!isSendGridConfigured()) {
      console.error('[OTP_SEND_REJECTED] reason=sendgrid_not_configured uid=' + uid);
      return res.status(503).json({ error: 'Verification email service is not available right now. Please try again later.' });
    }

    // Check rate limit
    await sendRateLimiter.consume(uid);

    // Generate OTP
    const otp = generateOtp();
    const now = new Date();
    const expiresAt = new Date(now.getTime() + config.otp.expiryMinutes * 60 * 1000);
    console.log('[OTP_GENERATED] uid=' + uid + ' expiresAt=' + expiresAt.toISOString());

    // Generate Firebase email verification link
    let verificationLink: string;
    try {
      verificationLink = await generateEmailVerificationLink(auth, email, config.firebaseHostingUrl);
    } catch (linkError) {
      console.error('Error generating verification link:', linkError);
      return res.status(500).json({ error: 'Failed to generate verification link' });
    }
    // The link embeds a time-limited Firebase token; log only that it exists, never the value.
    console.log('[VERIFICATION_LINK_GENERATED] uid=' + uid);

    // Store OTP hash in Firestore (never plaintext)
    const otpDoc: OtpDocument = {
      uid,
      email,
      codeHash: hashOtp(otp),
      createdAt: admin.firestore.FieldValue.serverTimestamp() as any,
      expiresAt: admin.firestore.Timestamp.fromDate(expiresAt),
      attempts: 0,
      maxAttempts: config.otp.maxAttempts,
      used: false,
      status: 'pending',
    };

    await db.collection('otp_codes').doc(uid).set(otpDoc);

    // Send email with OTP + verification link
    try {
      await sendOtpEmail({
        email,
        otp,
        verificationLink,
        expiryMinutes: config.otp.expiryMinutes,
        appName: 'Omni',
      });
    } catch (emailError) {
      // If email fails, delete the OTP so user can retry
      await db.collection('otp_codes').doc(uid).delete();
      throw emailError;
    }

    const response: SendOtpResponse = {
      success: true,
      message: 'Verification code sent',
    };
    res.json(response);
  } catch (error: any) {
    // The limiter throws `Rate limit exceeded` — `Rate limiter` was never thrown by it, so a
    // throttled request fell through to the 500 branch and the app told the user to retry
    // immediately, against a limit they were told to wait for.
    if (error instanceof Error && error.message.includes('Rate limit')) {
      return res.status(429).json({ error: 'Too many requests. Please wait before resending.' });
    }
    if (error instanceof Error && error.message.startsWith('Failed to send verification email')) {
      console.error('[OTP_SEND_FAILED] reason=email_send uid=' + uid, error.message);
      return res.status(502).json({ error: 'The verification email could not be sent. Please try again.' });
    }
    console.error('OTP send error:', error);
    res.status(500).json({ error: 'Failed to send verification code' });
  }
});

// POST /otp/verify - Verify 6-digit OTP
app.post('/otp/verify', authenticate, validateRequest(verifyOtpSchema), async (req: Request, res: Response) => {
  try {
    const user = (req as any).user;
    const uid = user.uid;
    const { code } = req.body;

    // Check rate limit
    await verifyRateLimiter.consume(uid);

    // Get stored OTP
    const otpDocRef = db.collection('otp_codes').doc(uid);
    const otpDocSnap = await otpDocRef.get();

    if (!otpDocSnap.exists) {
      return res.status(400).json({ error: 'No code found. Please request a new one.' });
    }

    const otpData = otpDocSnap.data() as OtpDocument;

    // Check expiration
    const expiresAt = otpData.expiresAt.toDate();
    if (Date.now() > expiresAt.getTime()) {
      await otpDocRef.update({ status: 'expired' });
      return res.status(400).json({ error: 'Code expired. Please request a new one.' });
    }

    // Check if already used
    if (otpData.used) {
      return res.status(400).json({ error: 'Code already used. Please request a new one.' });
    }

    // Increment attempts
    const newAttempts = otpData.attempts + 1;
    await otpDocRef.update({ attempts: newAttempts });

    // Check max attempts
    if (newAttempts > otpData.maxAttempts) {
      await otpDocRef.update({ status: 'blocked' });
      return res.status(400).json({ error: 'Too many attempts. Please request a new code.' });
    }

    // Verify code (compare hash)
    const codeHash = hashOtp(code);
    if (codeHash !== otpData.codeHash) {
      return res.status(400).json({ error: 'Incorrect code. Please try again.' });
    }

    // Mark as used and verified
    await otpDocRef.update({
      used: true,
      status: 'verified',
    });

    // Mark Firebase user's email as verified using Admin SDK
    try {
      await auth.updateUser(uid, { emailVerified: true });
    } catch (authError) {
      console.error('Error updating Firebase Auth emailVerified:', authError);
      return res.status(500).json({ error: 'Verification succeeded but failed to update account' });
    }
    console.log('[EMAIL_VERIFIED] uid=' + uid);

    // Clean up - optionally delete or keep for audit
    // await otpDocRef.delete();

    const response: VerifyOtpResponse = {
      success: true,
      verified: true,
      message: 'Email verified successfully',
    };
    res.json(response);
  } catch (error: any) {
    if (error instanceof Error && error.message.includes('Rate limit')) {
      return res.status(429).json({ error: 'Too many verification attempts. Please wait.' });
    }
    console.error('OTP verify error:', error);
    res.status(500).json({ error: 'Failed to verify code' });
  }
});

// GET /health - Health check
app.get('/health', (_req: Request, res: Response) => {
  const response: HealthResponse = {
    // `sendgrid` is what makes the check useful: an HTTP 200 with the mail provider unconfigured
    // is exactly the state that looked healthy while every email silently went nowhere.
    status: isSendGridConfigured() ? 'ok' : 'degraded',
    timestamp: new Date().toISOString(),
    version: '1.0.1',
  };
  res.json(response);
});

// Error handling middleware
app.use((error: Error, _req: Request, res: Response, _next: NextFunction) => {
  console.error('Unhandled error:', error);
  res.status(500).json({ error: 'Internal server error' });
});

// 404 handler
app.use((_req: Request, res: Response) => {
  res.status(404).json({ error: 'Not found' });
});

// Start server
const PORT = config.port;
app.listen(PORT, () => {
  validateConfig();
  console.log(`🚀 OTP backend running on port ${PORT}`);
  console.log(`   Health: http://localhost:${PORT}/health`);
  console.log(`   OTP Send: POST /otp/send`);
  console.log(`   OTP Verify: POST /otp/verify`);
});

export { app };