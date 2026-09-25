import { Request, Response } from 'express';
import { z } from 'zod';
import admin from 'firebase-admin';
import { FcmService } from './fcm.js';
import { RevenueCatService } from './revenuecat.js';
import { VerificationDecisionResponse } from './types.js';

export interface VerificationDeps {
  db: admin.firestore.Firestore;
  auth: admin.auth.Auth;
  fcm: FcmService;
  revenueCat: RevenueCatService;
}

const approveSchema = z.object({
  uid: z.string().min(1),
  profession: z.enum(['DOCTOR', 'NUTRITIONIST']),
});

const rejectSchema = z.object({
  uid: z.string().min(1),
  reason: z.string().max(500).optional(),
});

export function createVerificationHandlers(deps: VerificationDeps) {

  async function approve(req: Request, res: Response): Promise<void> {
    const caller = (req as any).user;
    if (caller?.admin !== true) {
      res.status(403).json({ error: 'Admin only' });
      return;
    }

    const parsed = approveSchema.safeParse(req.body);
    if (!parsed.success) {
      res.status(400).json({ error: 'Invalid request', details: parsed.error.flatten() });
      return;
    }
    const { uid, profession } = parsed.data;
    console.log('[VERIFICATION_APPROVE] admin=' + caller.uid + ' target=' + uid);

    const reqDoc = await deps.db.collection('verificationRequests').doc(uid).get();
    if (!reqDoc.exists || reqDoc.data()?.status !== 'pending') {
      res.status(404).json({ error: 'No pending request for this user' });
      return;
    }
    const application = reqDoc.data() ?? {};

    const batch = deps.db.batch();

    batch.update(deps.db.collection('verificationRequests').doc(uid), {
      status: 'approved',
      decidedAt: admin.firestore.FieldValue.serverTimestamp(),
      decidedBy: caller.uid,
    });

    batch.set(deps.db.collection('users').doc(uid), {
      verified: true,
      role: 'PROFESSIONAL',
      profession,
    }, { merge: true });

    // A doctor's approval also lists them in the Omni+ directory: the tile the doctor directory
    // renders is this document, and no client can write the collection (rules: write denied) —
    // only this backend, holding the admin SDK, may. The fields the directory and the consultation
    // flow read are exactly the ones Doctor models.
    if (profession === 'DOCTOR') {
      batch.set(deps.db.collection('doctors').doc(uid), {
        uid,
        name: String(application.name ?? '').trim(),
        specialty: String(application.specialty ?? '').trim() || 'General Medicine',
        bio: '',
        photoUrl: null,
        yearsExperience: 0,
        rating: 0,
        reviewCount: 0,
        languages: ['English'],
        consultationFee: '',
        available: true,
        verified: true,
      });
    }

    const notifRef = deps.db.collection('notifications').doc(uid).collection('items').doc();
    batch.set(notifRef, {
      type: 'VERIFICATION',
      title: 'Verification approved',
      body: 'Your professional profile is now verified.',
      read: false,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    await batch.commit();

    await deps.auth.setCustomUserClaims(uid, { verified: true, profession, admin: false });
    console.log('[VERIFICATION_CLAIMS_SET] uid=' + uid + ' profession=' + profession);

    // The doctor promotion: a verified doctor is comped Omni+ through RevenueCat, so every premium
    // check in the app stays one entitlement lookup and the paywall keeps working unchanged for
    // them. Server-side only — the secret key never reaches the app.
    let comped = false;
    if (profession === 'DOCTOR') {
      try {
        comped = await deps.revenueCat.grantDoctorEntitlement(uid);
      } catch (rcError) {
        console.error('[RC_COMP_UNEXPECTED] uid=' + uid, rcError);
      }
    }

    try {
      await deps.fcm.sendToUser(uid, 'Verification approved', 'Your professional profile is now verified.', { type: 'VERIFICATION' });
    } catch (fcmError) {
      console.error('[VERIFICATION_FCM_ERROR] uid=' + uid, fcmError);
    }

    const response: VerificationDecisionResponse = { success: true, message: 'Approved', comped };
    res.json(response);
  }

  async function reject(req: Request, res: Response): Promise<void> {
    const caller = (req as any).user;
    if (caller?.admin !== true) {
      res.status(403).json({ error: 'Admin only' });
      return;
    }

    const parsed = rejectSchema.safeParse(req.body);
    if (!parsed.success) {
      res.status(400).json({ error: 'Invalid request', details: parsed.error.flatten() });
      return;
    }
    const { uid, reason } = parsed.data;
    console.log('[VERIFICATION_REJECT] admin=' + caller.uid + ' target=' + uid);

    const reqDoc = await deps.db.collection('verificationRequests').doc(uid).get();
    if (!reqDoc.exists || reqDoc.data()?.status !== 'pending') {
      res.status(404).json({ error: 'No pending request for this user' });
      return;
    }

    const batch = deps.db.batch();

    batch.update(deps.db.collection('verificationRequests').doc(uid), {
      status: 'rejected',
      rejectionReason: reason ?? '',
      decidedAt: admin.firestore.FieldValue.serverTimestamp(),
      decidedBy: caller.uid,
    });

    const notifRef = deps.db.collection('notifications').doc(uid).collection('items').doc();
    batch.set(notifRef, {
      type: 'VERIFICATION',
      title: 'Verification update',
      body: reason ? 'Your application was not approved: ' + reason.slice(0, 120) : 'Your application was not approved. You may re-apply.',
      read: false,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    await batch.commit();

    try {
      await deps.fcm.sendToUser(uid, 'Verification update', 'Your application was not approved. You may re-apply.', { type: 'VERIFICATION' });
    } catch (fcmError) {
      console.error('[VERIFICATION_FCM_ERROR] uid=' + uid, fcmError);
    }

    const response: VerificationDecisionResponse = { success: true, message: 'Rejected' };
    res.json(response);
  }

  return { approve, reject };
}