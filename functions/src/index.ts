import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import * as crypto from "crypto";
import sgMail from "@sendgrid/mail";

// Initialize Firebase Admin SDK
admin.initializeApp();

const db = admin.firestore();
const auth = admin.auth();

// Configure SendGrid using functions.config() (works on Spark plan)
// TODO: Migrate to params/Secrets when on Blaze plan
const SENDGRID_API_KEY = functions.config().sendgrid?.key;

if (SENDGRID_API_KEY) {
  sgMail.setApiKey(SENDGRID_API_KEY);
} else {
  console.warn("SENDGRID_API_KEY not configured - emails will not be sent");
}

// Helper: Generate cryptographically secure 6-digit OTP
function generateOtp(): string {
  return crypto.randomInt(100000, 999999).toString().padStart(6, "0");
}

// Helper: Hash OTP for storage (SHA-256)
function hashOtp(otp: string): string {
  return crypto.createHash("sha256").update(otp).digest("hex");
}

// Helper: Generate Firebase email verification link using Admin SDK
async function generateEmailVerificationLink(email: string): Promise<string> {
  try {
    const actionCodeSettings: admin.auth.ActionCodeSettings = {
      url: "https://omni-2c987.firebaseapp.com/", // Your Firebase Hosting URL or custom domain
      handleCodeInApp: true, // Opens in app if installed, else browser
      android: {
        packageName: "com.example.omni",
        installApp: true,
        minimumVersion: "1",
      },
      iOS: {
        bundleId: "com.example.omni",
      },
      dynamicLinkDomain: "omni-2c987.page.link", // Optional: if using Firebase Dynamic Links
    };

    const link = await auth.generateEmailVerificationLink(email, actionCodeSettings);
    return link;
  } catch (error) {
    console.error("Error generating email verification link:", error);
    throw new functions.https.HttpsError("internal", "Failed to generate verification link");
  }
}

// Helper: Send beautiful HTML email via SendGrid
async function sendVerificationEmail(
  email: string,
  otp: string,
  verificationLink: string
): Promise<void> {
  const apiKey = functions.config().sendgrid?.key;
  const fromEmail = functions.config().sendgrid?.from || "noreply@omni.app";

  if (!apiKey) {
    console.log(`[DEV MODE] OTP Email to ${email}: ${otp}`);
    console.log(`[DEV MODE] Verification Link: ${verificationLink}`);
    return;
  }

  sgMail.setApiKey(apiKey);

  const htmlContent = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Verify your Omni email</title>
  <!--[if mso]>
  <noscript>
    <xml>
      <o:OfficeDocumentSettings>
        <o:PixelsPerInch>96</o:PixelsPerInch>
      </o:OfficeDocumentSettings>
    </xml>
  </noscript>
  <![endif]-->
</head>
<body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background-color: #f5f5f5;">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color: #f5f5f5;">
    <tr>
      <td align="center" style="padding: 40px 20px;">
        <table role="presentation" width="100%" max-width="480" cellspacing="0" cellpadding="0" border="0" style="background-color: #ffffff; border-radius: 16px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); overflow: hidden;">
          <!-- Header -->
          <tr>
            <td style="background: linear-gradient(135deg, #4F46E5 0%, #7C3AED 100%); padding: 32px 24px; text-align: center;">
              <h1 style="margin: 0; font-size: 28px; font-weight: 700; color: #ffffff; letter-spacing: -0.5px;">OMNI</h1>
              <p style="margin: 8px 0 0; font-size: 14px; color: rgba(255,255,255,0.9);">Healthcare & Wellness</p>
            </td>
          </tr>

          <!-- Content -->
          <tr>
            <td style="padding: 40px 32px;">
              <h2 style="margin: 0 0 16px; font-size: 24px; font-weight: 600; color: #1F2937; text-align: center;">Verify your email address</h2>
              <p style="margin: 0 0 24px; font-size: 16px; line-height: 1.6; color: #4B5563; text-align: center;">Thanks for creating your Omni account.<br>Please verify your email using either of the options below.</p>

              <!-- OTP Code Box -->
              <div style="background: linear-gradient(135deg, #F3F4F6 0%, #E5E7EB 100%); border-radius: 12px; padding: 24px; text-align: center; margin-bottom: 32px; border: 1px solid #D1D5DB;">
                <p style="margin: 0 0 12px; font-size: 13px; font-weight: 600; color: #6B7280; text-transform: uppercase; letter-spacing: 1px;">YOUR VERIFICATION CODE</p>
                <div style="font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Fira Mono', monospace; font-size: 42px; font-weight: 700; color: #1F2937; letter-spacing: 8px; user-select: all;">
                  ${otp.match(/.{1,3}/g)?.join(" ") || otp}
                </div>
                <p style="margin: 16px 0 0; font-size: 13px; color: #9CA3AF;">This code expires in <strong>10 minutes</strong>.</p>
              </div>

              <!-- OR Divider -->
              <div style="display: flex; align-items: center; gap: 16px; margin: 32px 0;">
                <div style="flex: 1; height: 1px; background-color: #E5E7EB;"></div>
                <span style="font-size: 13px; font-weight: 600; color: #9CA3AF; text-transform: uppercase; letter-spacing: 0.5px;">OR</span>
                <div style="flex: 1; height: 1px; background-color: #E5E7EB;"></div>
              </div>

              <!-- Verify Button -->
              <div style="text-align: center; margin-bottom: 24px;">
                <a href="${verificationLink}" style="display: inline-block; background: linear-gradient(135deg, #4F46E5 0%, #7C3AED 100%); color: #ffffff; font-size: 16px; font-weight: 600; padding: 16px 32px; border-radius: 12px; text-decoration: none; box-shadow: 0 4px 14px rgba(79, 70, 229, 0.4); transition: all 0.2s ease;">
                  Verify my email
                </a>
              </div>

              <p style="margin: 0; font-size: 13px; color: #9CA3AF; text-align: center;">You only need to use one of these methods.</p>
            </td>
          </tr>

          <!-- Footer -->
          <tr>
            <td style="background-color: #F9FAFB; padding: 24px 32px; border-top: 1px solid #E5E7EB;">
              <p style="margin: 0 0 8px; font-size: 13px; color: #6B7280; text-align: center;">If you didn't create this account, you can safely ignore this email.</p>
              <p style="margin: 0; font-size: 13px; color: #9CA3AF; text-align: center;">The Omni Team</p>
            </td>
          </tr>
        </table>

        <!-- Unsubscribe/Legal -->
        <p style="margin: 16px 0 0; font-size: 11px; color: #9CA3AF; text-align: center; max-width: 480px;">
          This email was sent to ${email}. If you have questions, contact support@omni.app
        </p>
      </td>
    </tr>
  </table>
</body>
</html>
  `;

  const textContent = `
OMNI - Verify your email address

Thanks for creating your Omni account.

Please verify your email using either of the options below.

YOUR VERIFICATION CODE

    ${otp}

This code expires in 10 minutes.

OR

Verify your email: ${verificationLink}

You only need to use one of these methods.

If you didn't create this account, you can safely ignore this email.

The Omni Team
  `;

  const msg = {
    to: email,
    from: {
      email: fromEmail,
      name: "Omni",
    },
    subject: "Verify your Omni email address",
    text: textContent,
    html: htmlContent,
  };

  try {
    await sgMail.send(msg);
    console.log(`Verification email sent to ${email}`);
  } catch (error) {
    console.error("SendGrid error:", error);
    throw new functions.https.HttpsError("internal", "Failed to send verification email");
  }
}

// ============================================================
// CALLABLE FUNCTION: sendOtpCode
// ============================================================
export const sendOtpCode = functions.https.onCall(async (request) => {
  // Verify authentication
  if (!request.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated");
  }

  const uid = request.auth.uid;
  const email = request.auth.token.email;

  if (!email) {
    throw new functions.https.HttpsError("failed-precondition", "User has no email address");
  }

  // Rate limiting: Check Firestore for recent sends
  const rateLimitRef = db.collection("otp_rate_limits").doc(uid);
  const rateLimitDoc = await rateLimitRef.get();

  const now = Date.now();
  const WINDOW_MS = 10 * 60 * 1000; // 10 minutes
  const MAX_SENDS = 3;

  if (rateLimitDoc.exists) {
    const data = rateLimitDoc.data()!;
    const recentSends = (data.sends || []).filter((ts: number) => now - ts < WINDOW_MS);
    if (recentSends.length >= MAX_SENDS) {
      throw new functions.https.HttpsError("resource-exhausted", "Too many requests. Please wait before resending.");
    }
    recentSends.push(now);
    await rateLimitRef.set({ sends: recentSends });
  } else {
    await rateLimitRef.set({ sends: [now] });
  }

  // Generate OTP
  const otp = generateOtp();
  const otpHash = hashOtp(otp);
  const expiresAt = admin.firestore.Timestamp.fromMillis(now + 10 * 60 * 1000); // 10 minutes

  // Generate Firebase email verification link
  let verificationLink: string;
  try {
    verificationLink = await generateEmailVerificationLink(email);
  } catch (error) {
    throw new functions.https.HttpsError("internal", "Failed to generate verification link");
  }

  // Store OTP hash in Firestore (never plaintext)
  const otpRef = db.collection("otp_codes").doc(uid);
  await otpRef.set({
    otpHash,
    expiresAt,
    attempts: 0,
    used: false,
    email,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  // Send email with OTP + verification link
  try {
    await sendVerificationEmail(email, otp, verificationLink);
  } catch (error) {
    // If email fails, delete the OTP so user can retry
    await otpRef.delete();
    throw error;
  }

  return { success: true, message: "Verification code sent" };
});

// ============================================================
// CALLABLE FUNCTION: verifyOtpCode
// ============================================================
export const verifyOtpCode = functions.https.onCall(async (request) => {
  // Verify authentication
  if (!request.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated");
  }

  const uid = request.auth.uid;
  const code = request.data.code;

  if (!code || code.length !== 6 || !/^\d{6}$/.test(code)) {
    throw new functions.https.HttpsError("invalid-argument", "Invalid code format");
  }

  // Rate limiting for verification attempts
  const verifyRateLimitRef = db.collection("otp_verify_rate_limits").doc(uid);
  const verifyRateLimitDoc = await verifyRateLimitRef.get();

  const now = Date.now();
  const VERIFY_WINDOW_MS = 10 * 60 * 1000; // 10 minutes
  const MAX_VERIFY_ATTEMPTS = 10;

  if (verifyRateLimitDoc.exists) {
    const data = verifyRateLimitDoc.data()!;
    const recentAttempts = (data.attempts || []).filter((ts: number) => now - ts < VERIFY_WINDOW_MS);
    if (recentAttempts.length >= MAX_VERIFY_ATTEMPTS) {
      throw new functions.https.HttpsError("resource-exhausted", "Too many verification attempts. Please wait.");
    }
    recentAttempts.push(now);
    await verifyRateLimitRef.set({ attempts: recentAttempts });
  } else {
    await verifyRateLimitRef.set({ attempts: [now] });
  }

  // Get stored OTP
  const otpRef = db.collection("otp_codes").doc(uid);
  const otpDoc = await otpRef.get();

  if (!otpDoc.exists) {
    throw new functions.https.HttpsError("not-found", "No code found. Please request a new one.");
  }

  const otpData = otpDoc.data()!;

  // Check expiration
  const expiresAt = otpData.expiresAt.toMillis ? otpData.expiresAt.toMillis() : otpData.expiresAt;
  if (now > expiresAt) {
    await otpRef.delete();
    throw new functions.https.HttpsError("deadline-exceeded", "Code expired. Please request a new one.");
  }

  // Check if already used
  if (otpData.used) {
    throw new functions.https.HttpsError("failed-precondition", "Code already used. Please request a new one.");
  }

  // Increment attempts
  const newAttempts = (otpData.attempts || 0) + 1;
  await otpRef.update({ attempts: newAttempts });

  // Check max attempts per code
  if (newAttempts > 5) {
    await otpRef.delete();
    throw new functions.https.HttpsError("failed-precondition", "Too many attempts. Please request a new code.");
  }

  // Verify code (compare hash)
  const submittedHash = hashOtp(code);
  if (submittedHash !== otpData.otpHash) {
    throw new functions.https.HttpsError("permission-denied", "Incorrect code. Please try again.");
  }

  // Mark as used
  await otpRef.update({ used: true });

  // Mark Firebase user's email as verified using Admin SDK
  try {
    await auth.updateUser(uid, { emailVerified: true });
  } catch (error) {
    console.error("Error updating Firebase Auth emailVerified:", error);
    throw new functions.https.HttpsError("internal", "Verification succeeded but failed to update account");
  }

  // Clean up OTP
  await otpRef.delete();

  return { success: true, verified: true, message: "Email verified successfully" };
});

// ============================================================
// CALLABLE FUNCTION: resendOtpCode (alias for sendOtpCode)
// ============================================================
export const resendOtpCode = functions.https.onCall(async (request) => {
  // Same logic as sendOtpCode - reuses the same rate limit
  // We need to call the internal logic, not the exported function directly
  // since the exported function is wrapped by onCall
  // For now, we'll just duplicate the logic or create a shared helper
  
  // Verify authentication
  if (!request.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated");
  }

  const uid = request.auth.uid;
  const email = request.auth.token.email;

  if (!email) {
    throw new functions.https.HttpsError("failed-precondition", "User has no email address");
  }

  // Rate limiting: Check Firestore for recent sends
  const rateLimitRef = db.collection("otp_rate_limits").doc(uid);
  const rateLimitDoc = await rateLimitRef.get();

  const now = Date.now();
  const WINDOW_MS = 10 * 60 * 1000; // 10 minutes
  const MAX_SENDS = 3;

  if (rateLimitDoc.exists) {
    const data = rateLimitDoc.data()!;
    const recentSends = (data.sends || []).filter((ts: number) => now - ts < WINDOW_MS);
    if (recentSends.length >= MAX_SENDS) {
      throw new functions.https.HttpsError("resource-exhausted", "Too many requests. Please wait before resending.");
    }
    recentSends.push(now);
    await rateLimitRef.set({ sends: recentSends });
  } else {
    await rateLimitRef.set({ sends: [now] });
  }

  // Generate OTP
  const otp = generateOtp();
  const otpHash = hashOtp(otp);
  const expiresAt = admin.firestore.Timestamp.fromMillis(now + 10 * 60 * 1000); // 10 minutes

  // Generate Firebase email verification link
  let verificationLink: string;
  try {
    verificationLink = await generateEmailVerificationLink(email);
  } catch (error) {
    throw new functions.https.HttpsError("internal", "Failed to generate verification link");
  }

  // Store OTP hash in Firestore (never plaintext)
  const otpRef = db.collection("otp_codes").doc(uid);
  await otpRef.set({
    otpHash,
    expiresAt,
    attempts: 0,
    used: false,
    email,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  // Send email with OTP + verification link
  try {
    await sendVerificationEmail(email, otp, verificationLink);
  } catch (error) {
    // If email fails, delete the OTP so user can retry
    await otpRef.delete();
    throw error;
  }

  return { success: true, message: "Verification code sent" };
});