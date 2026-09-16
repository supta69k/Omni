import { createHash, randomInt } from 'crypto';

const OTP_LENGTH = 6;
const OTP_MIN = 10 ** (OTP_LENGTH - 1);
const OTP_MAX = 10 ** OTP_LENGTH - 1;

/**
 * Generate a cryptographically secure 6-digit OTP
 */
export function generateOtp(): string {
  return randomInt(OTP_MIN, OTP_MAX).toString().padStart(OTP_LENGTH, '0');
}

/**
 * Hash an OTP using SHA-256 for secure storage
 * Never store plaintext OTPs in Firestore
 */
export function hashOtp(otp: string): string {
  return createHash('sha256').update(otp).digest('hex');
}

/**
 * Verify an OTP against its hash
 */
export function verifyOtpHash(otp: string, hash: string): boolean {
  return hashOtp(otp) === hash;
}

/**
 * Generate a Firebase email verification link using Admin SDK
 */
export async function generateEmailVerificationLink(
  auth: any,
  email: string,
  firebaseHostingUrl: string
): Promise<string> {
  const actionCodeSettings = {
    url: firebaseHostingUrl,
    handleCodeInApp: true,
    android: {
      packageName: 'com.example.omni',
      installApp: true,
      minimumVersion: '1',
    },
    ios: {
      bundleId: 'com.example.omni',
    },
  };

  const link = await auth.generateEmailVerificationLink(email, actionCodeSettings);
  return link;
}

/**
 * Format OTP for display (e.g., "123 456")
 */
export function formatOtpForDisplay(otp: string): string {
  return otp.replace(/(\d{3})(\d{3})/, '$1 $2');
}

/**
 * Calculate remaining seconds until expiry
 */
export function getRemainingSeconds(expiresAt: Date): number {
  const remaining = Math.max(0, Math.ceil((expiresAt.getTime() - Date.now()) / 1000));
  return remaining;
}

/**
 * Format seconds as MM:SS
 */
export function formatDuration(seconds: number): string {
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
}