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
  /** Whether a SendGrid API key is actually loaded — `status` alone hid this before. */
  sendgrid: boolean;
  /**
   * Domain part of SENDGRID_FROM_EMAIL only. The domain is what decides deliverability (a consumer
   * domain like gmail.com fails DMARC alignment through SendGrid and gets spam-foldered), and it
   * answers "what is Render actually configured with?" without publishing a full address.
   */
  fromDomain: string;
}

// ---- AI meal analysis (Phase 7) --------------------------------------------

/** One food the model identified inside a meal description. */
export interface MealItem {
  name: string;
  quantity: number;
  unit: string;
  calories: number;
  proteinGrams: number;
  carbsGrams: number;
  fatGrams: number;
}

/** The summed nutrition across a meal's items. Always recomputed server-side from the items. */
export interface MealTotals {
  calories: number;
  proteinGrams: number;
  carbsGrams: number;
  fatGrams: number;
}

/** The raw shape Gemini is asked to return (before server-side validation). */
export interface RawMealAnalysis {
  mealName?: unknown;
  items?: unknown;
  totals?: unknown;
  estimated?: unknown;
  needsClarification?: unknown;
  clarificationQuestion?: unknown;
}

/** The validated, sanitized result the endpoint returns to Android. */
export interface MealAnalysisResult {
  mealName: string;
  items: MealItem[];
  totals: MealTotals;
  /** Always true for this phase — AI values are estimates, never exact measurements. */
  estimated: boolean;
  needsClarification: boolean;
  clarificationQuestion: string | null;
}

export interface AnalyzeMealRequest {
  mealText: string;
}

export interface AnalyzeMealResponse {
  success: boolean;
  result: MealAnalysisResult;
}

export interface SendGridEmailData {
  to: string;
  from: { email: string; name?: string };
  subject: string;
  text: string;
  html: string;
}

export interface OtpEmailTemplateData {
  email: string;
  otp: string;
  verificationLink: string;
  expiryMinutes: number;
  appName: string;
}

// ---- Phase 12R: Cloud Function → Render migration -------------------------

export interface ToggleLikeResponse {
  success: boolean;
  liked: boolean;
}

export interface AddCommentResponse {
  success: boolean;
  commentId: string;
}

export interface SendMessageResponse {
  success: boolean;
  messageId: string;
}

export interface VerificationDecisionResponse {
  success: boolean;
  message: string;
  /** Doctor approvals only: whether the omni_plus promotional entitlement was granted. */
  comped?: boolean;
}

export interface GrantAdminResponse {
  success: boolean;
}