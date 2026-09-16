import { Timestamp } from 'firebase-admin/firestore';

export interface OtpDocument {
  uid: string;
  email: string;
  codeHash: string;
  createdAt: Timestamp;
  expiresAt: Timestamp;
  attempts: number;
  maxAttempts: number;
  used: boolean;
  status: 'pending' | 'verified' | 'expired' | 'blocked';
}

export interface RateLimitDocument {
  uid: string;
  sends: number[];
  verifies: number[];
  updatedAt: Timestamp;
}

export interface SendOtpRequest {
  // Empty body - UID and email come from verified ID token
}

export interface SendOtpResponse {
  success: boolean;
  message: string;
}

export interface VerifyOtpRequest {
  code: string;
}

export interface VerifyOtpResponse {
  success: boolean;
  verified: boolean;
  message: string;
}

export interface HealthResponse {
  status: 'ok' | 'degraded' | 'down';
  timestamp: string;
  version: string;
}

export interface EmailData {
  to: string;
  subject: string;
  html: string;
  text: string;
}

export interface OtpEmailTemplateData {
  email: string;
  otp: string;
  verificationLink: string;
  expiryMinutes: number;
  appName: string;
}