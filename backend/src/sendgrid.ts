import sgMail from '@sendgrid/mail';
import { config } from './config.js';
import { OtpEmailTemplateData } from './types.js';
import { generateOtpEmailHtml, generateOtpEmailText } from './email-template.js';

let sendGridInitialized = false;

export function initializeSendGrid(): void {
  if (sendGridInitialized) return;

  const apiKey = config.sendGridApiKey;
  if (!apiKey) {
    console.warn('⚠️ SendGrid API key not configured — /otp/send will reject until SENDGRID_API_KEY is set');
    return;
  }

  sgMail.setApiKey(apiKey);
  sendGridInitialized = true;
  console.log('✅ [SENDGRID_INITIALIZED]');
}

/**
 * Whether SendGrid is actually configured to deliver mail.
 *
 * Exposed by `/health` and checked before a send is accepted. Without the key there is nothing to
 * deliver with, and the old behaviour (print a dev-mode box and return success) is what let
 * `/otp/send` answer 200 for an email that was never sent — the app told the user "code sent"
 * and nobody received anything. Failing loudly is the only honest answer.
 */
export function isSendGridConfigured(): boolean {
  return sendGridInitialized;
}

export async function sendOtpEmail(data: OtpEmailTemplateData): Promise<void> {
  if (!sendGridInitialized) {
    initializeSendGrid();
  }

  if (!sendGridInitialized) {
    console.error('[SENDGRID_SEND_SKIPPED] reason=not_configured to=' + data.email);
    throw new Error('SendGrid is not configured. Set SENDGRID_API_KEY and redeploy.');
  }

  const fromEmail = config.sendGridFromEmail;
  const html = generateOtpEmailHtml(data);
  const text = generateOtpEmailText(data);

  const msg = {
    to: data.email,
    from: {
      email: fromEmail,
      name: data.appName,
    },
    subject: `Verify your ${data.appName} email address`,
    text,
    html,
  };

  // `to` is the user's own address for their own request; it is what makes the Render log line
  // matchable against an inbox. The OTP and the link are deliberately never logged here.
  console.log('[SENDGRID_SEND_ATTEMPT] to=' + data.email + ' from=' + fromEmail);
  try {
    await sgMail.send(msg);
    console.log('[SENDGRID_SEND_SUCCESS] to=' + data.email);
  } catch (error: any) {
    // SendGrid's 4xx/5xx body carries a JSON `errors` array; log it, then rethrow with the
    // original message so `/otp/send` reports the failure instead of a success.
    console.error('[SENDGRID_SEND_FAILURE] to=' + data.email, error.response?.body?.errors || error.message);
    throw new Error('Failed to send verification email: ' + (error.response?.body?.errors?.[0]?.message || error.message));
  }
}
