import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { Request, Response } from 'express';
import { createVerificationHandlers } from '../src/verification.js';

/**
 * The doctor half of verification approve (Omni+ Phase D):
 *
 *  1. Approving a DOCTOR also writes the `doctors/{uid}` directory tile — the document the Omni+
 *     doctor directory and the consultation flow read, which no client can write (rules deny it).
 *  2. The doctor is comped the `omni_plus` entitlement through RevenueCat, server-side only.
 *  3. A nutritionist approval does neither.
 *
 * The grant happens through the write batch, so the mocks capture `batch.set` calls and assert on
 * the captured data — the same shapes `phase12r.test.ts` uses.
 */
function mockReqRes(body: unknown, uid: string | undefined, isAdmin = true) {
  const req = {
    body,
    user: uid ? { uid, admin: isAdmin } : undefined,
    params: {},
  } as unknown as Request;
  const captured: { status: number; json: any } = { status: 200, json: undefined };
  const res = {
    status(code: number) {
      captured.status = code;
      return this;
    },
    json(payload: any) {
      captured.json = payload;
      return this;
    },
  } as unknown as Response;
  return { req, res, captured };
}

function makeHarness(requestData: any) {
  const doctorsRef = { id: 'user-123' };
  const batchSets: Array<{ ref: any; data: any }> = [];
  const grantDoctorEntitlement = vi.fn(async () => true);

  const db = {
    collection: vi.fn((name: string) => {
      if (name === 'verificationRequests') {
        return {
          doc: vi.fn(() => ({
            get: vi.fn(async () => ({ exists: true, id: 'user-123', data: () => requestData })),
          })),
        };
      }
      if (name === 'doctors') {
        return { doc: vi.fn(() => doctorsRef) };
      }
      if (name === 'notifications') {
        return {
          doc: vi.fn(() => ({ collection: vi.fn(() => ({ doc: vi.fn(() => ({ id: 'notif' })) })) })),
        };
      }
      return { doc: vi.fn(() => ({ set: vi.fn(async () => {}), update: vi.fn(async () => {}) })) };
    }),
    batch: vi.fn(() => ({
      set: vi.fn((ref: any, data: any) => batchSets.push({ ref, data })),
      update: vi.fn(),
      commit: vi.fn(async () => {}),
    })),
  };

  const handlers = createVerificationHandlers({
    db: db as any,
    auth: { setCustomUserClaims: vi.fn(async () => {}) } as any,
    fcm: { sendToUser: vi.fn(async () => {}) } as any,
    revenueCat: { grantDoctorEntitlement },
  });

  return { batchSets, doctorsRef, grantDoctorEntitlement, handlers };
}

describe('Doctor verification approve', () => {
  it('approving a doctor writes the directory tile through the batch', async () => {
    const { batchSets, doctorsRef, handlers } = makeHarness({
      status: 'pending',
      uid: 'user-123',
      name: 'Dr. Ayesha Rahman',
      specialty: 'Cardiology',
      profession: 'DOCTOR',
    });
    const { req, res, captured } = mockReqRes({ uid: 'user-123', profession: 'DOCTOR' }, 'admin-uid');
    (req as any).user = { uid: 'admin-uid', admin: true };

    await handlers.approve(req, res);

    expect(captured.status).toBe(200);
    const tile = batchSets.find((entry) => entry.ref === doctorsRef);
    expect(tile?.data).toEqual(
      expect.objectContaining({
        uid: 'user-123',
        name: 'Dr. Ayesha Rahman',
        specialty: 'Cardiology',
        verified: true,
        available: true,
      }),
    );
  });

  it('approving a doctor grants the omni_plus entitlement and reports it', async () => {
    const { grantDoctorEntitlement, handlers } = makeHarness({
      status: 'pending',
      uid: 'user-123',
      name: 'Dr. Ayesha Rahman',
      specialty: 'Cardiology',
    });
    const { req, res, captured } = mockReqRes({ uid: 'user-123', profession: 'DOCTOR' }, 'admin-uid');
    (req as any).user = { uid: 'admin-uid', admin: true };

    await handlers.approve(req, res);

    expect(grantDoctorEntitlement).toHaveBeenCalledWith('user-123');
    expect(captured.json.comped).toBe(true);
  });

  it('a doctor application without a specialty defaults to General Medicine', async () => {
    const { batchSets, doctorsRef, handlers } = makeHarness({
      status: 'pending',
      uid: 'user-123',
      name: 'Dr. No Specialty',
      profession: 'DOCTOR',
    });
    const { req, res, captured } = mockReqRes({ uid: 'user-123', profession: 'DOCTOR' }, 'admin-uid');
    (req as any).user = { uid: 'admin-uid', admin: true };

    await handlers.approve(req, res);

    const tile = batchSets.find((entry) => entry.ref === doctorsRef);
    expect(tile?.data).toEqual(expect.objectContaining({ specialty: 'General Medicine' }));
  });

  it('an unconfigured RevenueCat key still approves the doctor, comped: false', async () => {
    const { batchSets, doctorsRef, grantDoctorEntitlement, handlers } = makeHarness({
      status: 'pending',
      uid: 'user-123',
      name: 'Dr. Ayesha Rahman',
      specialty: 'Cardiology',
    });
    grantDoctorEntitlement.mockResolvedValue(false);

    const { req, res, captured } = mockReqRes({ uid: 'user-123', profession: 'DOCTOR' }, 'admin-uid');
    (req as any).user = { uid: 'admin-uid', admin: true };

    await handlers.approve(req, res);

    expect(captured.status).toBe(200);
    expect(captured.json.comped).toBe(false);
    expect(batchSets.some((entry) => entry.ref === doctorsRef)).toBe(true);
  });

  it('approving a nutritionist writes no directory tile and grants nothing', async () => {
    const { batchSets, doctorsRef, grantDoctorEntitlement, handlers } = makeHarness({
      status: 'pending',
      uid: 'user-123',
      name: 'Nut Karim',
      profession: 'NUTRITIONIST',
    });
    const { req, res, captured } = mockReqRes({ uid: 'user-123', profession: 'NUTRITIONIST' }, 'admin-uid');
    (req as any).user = { uid: 'admin-uid', admin: true };

    await handlers.approve(req, res);

    expect(captured.status).toBe(200);
    expect(captured.json.comped).toBe(false);
    expect(batchSets.some((entry) => entry.ref === doctorsRef)).toBe(false);
    expect(grantDoctorEntitlement).not.toHaveBeenCalled();
  });
});
