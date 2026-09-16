import sgMail from '@sendgrid/mail';
import { config } from './config.js';
import { EmailData, OtpEmailTemplateData } from './types.js';
import { generateOtpEmailHtml, generateOtpEmailText } from './email-template.js';

let sendGridInitialized = false;

export function initializeSendGrid(): void {
  if (sendGridInitialized) return;

  const apiKey = config.sendGridApiKey;
  if (!apiKey) {
    console.warn('⚠️ SendGrid API key not configured - emails will not be sent');
    return;
  }

  sgMail.setApiKey(apiKey);
  sendGridInitialized = true;
  console.log('✅ SendGrid initialized');
}

export async function sendOtpEmail(data: OtpEmailTemplateData): Promise<void> {
  if (!sendGridInitialized) {
    initializeSendGrid();
  }

  if (!sendGridInitialized) {
    // Development mode - log instead of sending
    console.log('┌─────────────────────────────────────────────────────────────┐');
    console.log('│ [DEV MODE] OTP Email                                        │');
    console.log('├─────────────────────────────────────────────────────────────┤');
    console.log(`│ To: ${data.email.padEnd(49)} │`);
    console.log(`│ OTP: ${data.otp.padEnd(50)} │`);
    console.log(`│ Expires: ${data.expiryMinutes} min                              │`);
    console.log(`│ Link: ${data.verificationLink.substring(0, 50).padEnd(49)} │`);
    console.log('└─────────────────────────────────────────────────────────────┘');
    return;
  }

  const fromEmail = config.sendGridFromEmail;
  const html = generateOtpEmailHtml(data);
  const text = generateOtpEmailText(data);

  const msg: EmailData = {
    to: data.email,
    from: {
      email: fromEmail,
      name: data.appName,
    },
    subject: `Verify your ${data.appName} email address`,
    text,
    html,
  };

  try {
    await sgMail.send(msg);
    console.log(`✅ Verification email sent to ${data.email}`);
  } catch (error: any) {
    console.error('❌ SendGrid error:', error.response?.body?.errors || error.message);
    throw new Error('Failed to send verification email');
  }
}