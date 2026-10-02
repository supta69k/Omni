import { describe, it, expect, vi } from 'vitest';
import type { Request, Response } from 'express';
import { createCommentHandler } from '../src/comment.js';

/**
 * The comment handler's delivery guarantee — the same contract message sending holds:
 * once the comment is committed, the sender is told `success`, and no later best-effort
 * step (author read, notification write, FCM) can turn that into a failure that makes
 * the sender think their comment was lost while the recipient already has it.
 */
function makeReqRes() {
  const req = {
    user: { uid: 'sender' },
    params: { postId: 'post-1' },
    body: { body: 'nice post' },
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

function makeDeps(options: { notifyThrows?: boolean; postAuthorId?: string } = {}) {
  const commit = vi.fn(async () => {});
  const batch = { set: vi.fn(), update: vi.fn(), commit };

  const postRef = {
    get: vi.fn(async () => {
      if (options.notifyThrows) throw new Error('post read failed');
      return { data: () => ({ authorId: options.postAuthorId ?? 'someone-else' }) };
    }),
    collection: vi.fn(() => ({ doc: vi.fn(() => ({ id: 'comment-1' })) })),
  };

  const db = {
    collection: vi.fn((name: string) => {
      if (name === 'posts') return { doc: vi.fn(() => postRef) };
      if (name === 'users') {
        return { doc: vi.fn(() => ({ get: vi.fn(async () => ({ data: () => ({ name: 'Sender' }) })) })) };
      }
      if (name === 'notifications') {
        return { doc: vi.fn(() => ({ collection: vi.fn(() => ({ doc: vi.fn(() => ({ set: vi.fn(async () => {}) })) })) })) };
      }
      return {};
    }),
    batch: vi.fn(() => batch),
  } as any;

  const fcm = { sendToUser: vi.fn(async () => {}) } as any;
  return { db, fcm, commit };
}

describe('addComment delivery', () => {
  it('answers the sender success once the comment is committed', async () => {
    const deps = makeDeps();
    const { addComment } = createCommentHandler(deps);
    const { req, res, captured } = makeReqRes();

    await addComment(req, res);

    expect(deps.commit).toHaveBeenCalledTimes(1);
    expect(captured.status).toBe(200);
    expect(captured.json).toMatchObject({ success: true, commentId: 'comment-1' });
  });

  it('still reports success when the post-commit notification step fails', async () => {
    const deps = makeDeps({ notifyThrows: true });
    const { addComment } = createCommentHandler(deps);
    const { req, res, captured } = makeReqRes();

    await expect(addComment(req, res)).resolves.toBeUndefined();

    expect(deps.commit).toHaveBeenCalledTimes(1);
    expect(captured.status).toBe(200);
    expect(captured.json).toMatchObject({ success: true });
  });
});
