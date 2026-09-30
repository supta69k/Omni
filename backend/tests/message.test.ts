import { describe, it, expect, vi } from 'vitest';
import type { Request, Response } from 'express';
import { createMessageHandler } from '../src/message.js';

/**
 * The message send handler's delivery guarantee (BUG 6 — "sender does not see their own message").
 *
 * The message is committed first and the recipient's copy does not depend on anything after that. The
 * bug was that the *sender's* HTTP response did: a failure in the post-commit notification read/write
 * (a transient Firestore error, a quota cap) threw after the commit, the request returned 500, and the
 * client — with no optimistic echo at the time — showed the sender nothing even though the message was
 * safely stored and the recipient had it. So the contract under test is: once the message is committed,
 * the sender is told `success`, and no later best-effort step can turn that into a failure.
 */
function makeReqRes() {
  const req = {
    user: { uid: 'sender' },
    params: { conversationId: 'sender_recipient' },
    body: { recipientId: 'recipient', text: 'hello' },
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

/**
 * A fake Firestore that commits the message fine but can be told to fail the post-commit notification
 * step (either the sender read or the notification write).
 */
function makeDeps(options: { notifyThrows?: boolean } = {}) {
  const commit = vi.fn(async () => {});
  const batch = { set: vi.fn(), commit };

  const convoRef = {
    get: vi.fn(async () => ({
      exists: true,
      data: () => ({ participants: ['sender', 'recipient'] }),
    })),
    collection: vi.fn(() => ({ doc: vi.fn(() => ({ id: 'message-1' })) })),
  };

  const usersDoc = {
    get: vi.fn(async () => {
      if (options.notifyThrows) throw new Error('users read failed');
      return { data: () => ({ name: 'Sender Name' }) };
    }),
  };

  const notifDoc = { set: vi.fn(async () => {}) };

  const db = {
    collection: vi.fn((name: string) => {
      if (name === 'conversations') return { doc: vi.fn(() => convoRef) };
      if (name === 'users') return { doc: vi.fn(() => usersDoc) };
      if (name === 'notifications') {
        return { doc: vi.fn(() => ({ collection: vi.fn(() => ({ doc: vi.fn(() => notifDoc) })) })) };
      }
      return { doc: vi.fn(() => ({ get: vi.fn(async () => ({ exists: false })) })) };
    }),
    batch: vi.fn(() => batch),
  } as any;

  const fcm = { sendToUser: vi.fn(async () => {}) } as any;

  return { db, fcm, batch, commit };
}

describe('sendMessage delivery', () => {
  it('answers the sender success once the message is committed', async () => {
    const deps = makeDeps();
    const { sendMessage } = createMessageHandler(deps);
    const { req, res, captured } = makeReqRes();

    await sendMessage(req, res);

    expect(deps.commit).toHaveBeenCalledTimes(1);
    expect(captured.status).toBe(200);
    expect(captured.json).toMatchObject({ success: true, messageId: 'message-1' });
  });

  it('still reports success when the post-commit notification step fails', async () => {
    const deps = makeDeps({ notifyThrows: true });
    const { sendMessage } = createMessageHandler(deps);
    const { req, res, captured } = makeReqRes();

    // Must not reject: the notification failure is swallowed, not thrown into the request.
    await expect(sendMessage(req, res)).resolves.toBeUndefined();

    expect(deps.commit).toHaveBeenCalledTimes(1);
    expect(captured.status).toBe(200);
    expect(captured.json).toMatchObject({ success: true });
  });

  it('rejects a recipientId that is not the other participant', async () => {
    const deps = makeDeps();
    const { sendMessage } = createMessageHandler(deps);
    const { req, res, captured } = makeReqRes();
    (req as any).body.recipientId = 'stranger';

    await sendMessage(req, res);

    expect(captured.status).toBe(400);
    expect(deps.commit).not.toHaveBeenCalled();
  });
});
