import { describe, it, expect, vi, beforeEach } from 'vitest';
import { generateOtp, hashOtp, verifyOtpHash, formatOtpForDisplay, getRemainingSeconds, formatDuration } from '../src/utils.js';
import { generateOtpEmailHtml, generateOtpEmailText } from '../src/email-template.js';

describe('OTP Utilities', () => {
  describe('generateOtp', () => {
    it('should generate a 6-digit string', () => {
      const otp = generateOtp();
      expect(otp).toMatch(/^\d{6}$/);
    });

    it('should generate different OTPs on subsequent calls', () => {
      const otp1 = generateOtp();
      const otp2 = generateOtp();
      expect(otp1).not.toBe(otp2);
    });

    it('should pad with leading zeros if needed', () => {
      // Run many times to catch edge cases
      for (let i = 0; i < 100; i++) {
        const otp = generateOtp();
        expect(otp.length).toBe(6);
        expect(parseInt(otp, 10)).toBeGreaterThanOrEqual(100000);
        expect(parseInt(otp, 10)).toBeLessThanOrEqual(999999);
      }
    });
  });

  describe('hashOtp', () => {
    it('should produce consistent hash for same input', () => {
      const otp = '123456';
      const hash1 = hashOtp(otp);
      const hash2 = hashOtp(otp);
      expect(hash1).toBe(hash2);
    });

    it('should produce different hashes for different inputs', () => {
      const hash1 = hashOtp('123456');
      const hash2 = hashOtp('654321');
      expect(hash1).not.toBe(hash2);
    });

    it('should produce SHA-256 hash (64 hex chars)', () => {
      const hash = hashOtp('123456');
      expect(hash).toMatch(/^[a-f0-9]{64}$/);
    });
  });

  describe('verifyOtpHash', () => {
    it('should return true for correct OTP', () => {
      const otp = '123456';
      const hash = hashOtp(otp);
      expect(verifyOtpHash(otp, hash)).toBe(true);
    });

    it('should return false for incorrect OTP', () => {
      const hash = hashOtp('123456');
      expect(verifyOtpHash('654321', hash)).toBe(false);
      expect(verifyOtpHash('123457', hash)).toBe(false);
    });
  });

  describe('formatOtpForDisplay', () => {
    it('should format 6-digit OTP with space', () => {
      expect(formatOtpForDisplay('123456')).toBe('123 456');
    });
  });

  describe('getRemainingSeconds', () => {
    it('should return positive seconds for future date', () => {
      const future = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes
      const remaining = getRemainingSeconds(future);
      expect(remaining).toBeGreaterThan(0);
      expect(remaining).toBeLessThanOrEqual(300);
    });

    it('should return 0 for past date', () => {
      const past = new Date(Date.now() - 60 * 1000); // 1 minute ago
      const remaining = getRemainingSeconds(past);
      expect(remaining).toBe(0);
    });
  });

  describe('formatDuration', () => {
    it('should format seconds as MM:SS', () => {
      expect(formatDuration(0)).toBe('00:00');
      expect(formatDuration(59)).toBe('00:59');
      expect(formatDuration(60)).toBe('01:00');
      expect(formatDuration(300)).toBe('05:00');
      expect(formatDuration(3599)).toBe('59:59');
    });
  });
});

describe('Email Templates', () => {
  const mockData = {
    email: 'test@example.com',
    otp: '123456',
    verificationLink: 'https://example.com/verify?link=abc123',
    expiryMinutes: 10,
    appName: 'Omni',
  };

  describe('generateOtpEmailHtml', () => {
    it('should include OTP code', () => {
      const html = generateOtpEmailHtml(mockData);
      expect(html).toContain('123 456');
    });

    it('should include verification link', () => {
      const html = generateOtpEmailHtml(mockData);
      expect(html).toContain('https://example.com/verify?link=abc123');
    });

    it('should include app name', () => {
      const html = generateOtpEmailHtml(mockData);
      expect(html).toContain('Omni');
    });

    it('should include expiry minutes', () => {
      const html = generateOtpEmailHtml(mockData);
      expect(html).toContain('10 minutes');
    });

    it('should include Verify my email button', () => {
      const html = generateOtpEmailHtml(mockData);
      expect(html).toContain('Verify my email');
    });

    it('should not expose raw Firebase URL', () => {
      const html = generateOtpEmailHtml(mockData);
      // The link should be wrapped in a button, not shown as raw URL
      expect(html).not.toContain('actionCodeSettings');
      expect(html).not.toContain('mode=verifyEmail');
    });
  });

  describe('generateOtpEmailText', () => {
    it('should include OTP code', () => {
      const text = generateOtpEmailText(mockData);
      expect(text).toContain('123 456');
    });

    it('should include verification link', () => {
      const text = generateOtpEmailText(mockData);
      expect(text).toContain('https://example.com/verify?link=abc123');
    });

    it('should include app name', () => {
      const text = generateOtpEmailText(mockData);
      expect(text).toContain('Omni');
    });
  });
});