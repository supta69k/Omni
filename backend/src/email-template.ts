import { OtpEmailTemplateData } from './types.js';

/**
 * Generate professional HTML email for OTP verification
 * Includes both OTP code and Firebase verification link
 */
export function generateOtpEmailHtml(data: OtpEmailTemplateData): string {
  const { email, otp, verificationLink, expiryMinutes, appName } = data;
  const formattedOtp = formatOtpForDisplay(otp);

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta http-equiv="X-UA-Compatible" content="IE=edge">
  <title>Verify your ${appName} email</title>
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
<body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background-color: #f5f5f5; line-height: 1.6;">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color: #f5f5f5; min-height: 100vh;">
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
              <p style="margin: 0 0 8px; font-size: 16px; line-height: 1.6; color: #4B5563; text-align: center;">Thanks for creating your ${appName} account.</p>
              <p style="margin: 0 0 24px; font-size: 16px; line-height: 1.6; color: #4B5563; text-align: center;">Please verify your email using either of the options below.</p>

              <!-- OTP Code Box -->
              <div style="background: linear-gradient(135deg, #F3F4F6 0%, #E5E7EB 100%); border-radius: 12px; padding: 24px; text-align: center; margin-bottom: 32px; border: 1px solid #D1D5DB;">
                <p style="margin: 0 0 12px; font-size: 13px; font-weight: 600; color: #6B7280; text-transform: uppercase; letter-spacing: 1px;">YOUR VERIFICATION CODE</p>
                <div style="font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Fira Mono', monospace; font-size: 42px; font-weight: 700; color: #1F2937; letter-spacing: 8px; user-select: all;">
                  ${formattedOtp}
                </div>
                <p style="margin: 16px 0 0; font-size: 13px; color: #9CA3AF;">This code expires in <strong>${expiryMinutes} minutes</strong>.</p>
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
              <p style="margin: 0; font-size: 13px; color: #9CA3AF; text-align: center;">The ${appName} Team</p>
            </td>
          </tr>
        </table>

        <!-- Legal/Unsubscribe -->
        <p style="margin: 16px 0 0; font-size: 11px; color: #9CA3AF; text-align: center; max-width: 480px;">
          This email was sent to ${email}. If you have questions, contact support@omni.app
        </p>
      </td>
    </tr>
  </table>
</body>
</html>`;
}

function formatOtpForDisplay(otp: string): string {
  return otp.replace(/(\d{3})(\d{3})/, '$1 $2');
}

/**
 * Generate plain-text version of the email
 */
export function generateOtpEmailText(data: OtpEmailTemplateData): string {
  const { email, otp, verificationLink, expiryMinutes, appName } = data;
  const formattedOtp = formatOtpForDisplay(otp);

  return `
${appName} - Verify your email address

Thanks for creating your ${appName} account.

Please verify your email using either of the options below.

YOUR VERIFICATION CODE

    ${formattedOtp}

This code expires in ${expiryMinutes} minutes.

OR

Verify your email: ${verificationLink}

You only need to use one of these methods.

If you didn't create this account, you can safely ignore this email.

The ${appName} Team

---
This email was sent to ${email}. If you have questions, contact support@omni.app
`;
}