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
    // Tag the send so it can be isolated in SendGrid's Activity Feed.
    categories: ['otp-verification'],
    // SendGrid rewrites every href for click tracking by default, turning the Firebase action link
    // into a urlXXXX.sendgrid.net redirect. On a verification email that is doubly wrong: the
    // display/href domain mismatch is a spam signal, and the rewrite can mangle the signed Firebase
    // link itself. Open tracking's 1x1 pixel is a smaller but pointless signal on transactional mail.
    trackingSettings: {
      clickTracking: { enable: false, enableText: false },
      openTracking: { enable: false },
      subscriptionTracking: { enable: false },
    },
  };

  // `to` is the user's own address for their own request; it is what makes the Render log line
  // matchable against an inbox. The OTP and the link are deliberately never logged here.
  console.log('[SENDGRID_SEND_ATTEMPT] to=' + data.email + ' from=' + fromEmail);
  try {
    const [response] = await sgMail.send(msg);
    // A 202 means "queued", not "delivered" — everything that silently eats a message (a suppressed
    // recipient, a DMARC-misaligned sender, an account under review) happens after this line. The
    // x-message-id is the only handle that ties this send to a row in SendGrid's Activity Feed, so
    // log it: without it a "SUCCESS" line proves nothing when the user reports an empty inbox.
    const messageId = response?.headers?.['x-message-id'] ?? 'unknown';
    console.log(
      '[SENDGRID_SEND_SUCCESS] to=' + data.email +
      ' status=' + (response?.statusCode ?? '?') +
      ' messageId=' + messageId
    );
  } catch (error: any) {
    // SendGrid's 4xx/5xx body carries a JSON `errors` array; log it, then rethrow with the
    // original message so `/otp/send` reports the failure instead of a success.
    console.error('[SENDGRID_SEND_FAILURE] to=' + data.email, error.response?.body?.errors || error.message);
    throw new Error('Failed to send verification email: ' + (error.response?.body?.errors?.[0]?.message || error.message));
  }
}
