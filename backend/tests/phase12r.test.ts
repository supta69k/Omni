import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { Request, Response } from 'express';
import admin from 'firebase-admin';
import { createVerificationHandlers } from '../src/verification.js';
import { createLikeHandler } from '../src/like.js';
import { createCommentHandler } from '../src/comment.js';
import { createMessageHandler } from '../src/message.js';
import { FcmService } from '../src/fcm.js';

// Mock Firebase Admin types
type MockFirestore = {
  collection: ReturnType<typeof vi.fn>;
  batch: ReturnType<typeof vi.fn>;
};

type MockAuth = {
  verifyIdToken: ReturnType<typeof vi.fn>;
  setCustomUserClaims: ReturnType<typeof vi.fn>;
};

type MockFcm = {
  sendToUser: ReturnType<typeof vi.fn>;
};

/** Minimal Express req/res doubles capturing status + json. */
function mockReqRes(body: unknown, uid: string | undefined, params?: Record<string, string>) {
  const req = {
    body,
    user: uid ? { uid, admin: false } : undefined,
    params: params || {},
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

/** Mock document snapshot */
function mockDocSnap(exists: boolean, data?: any) {
  return {
    exists,
    data: () => data,
    id: 'mock-doc-id',
  };
}

/** Mock collection/document chain */
function mockCollection(getResult?: any, setResult?: any) {
  const docMock = {
    get: vi.fn(async () => getResult || mockDocSnap(false)),
    set: vi.fn(async () => setResult || {}),
    update: vi.fn(async () => setResult || {}),
    delete: vi.fn(async () => {}),
    collection: vi.fn(() => mockCollection()),
  };
  return {
    doc: vi.fn(() => docMock),
    add: vi.fn(async () => ({ id: 'new-doc-id' })),
    where: vi.fn(() => mockCollection()),
    orderBy: vi.fn(() => mockCollection()),
    limit: vi.fn(() => mockCollection()),
  };
}

describe('Phase 12R: Verification Endpoints', () => {
  let mockDb: MockFirestore;
  let mockAuth: MockAuth;
  let mockFcm: MockFcm;
  let handlers: ReturnType<typeof createVerificationHandlers>;

  beforeEach(() => {
    const batchOps: any[] = [];
    mockDb = {
      collection: vi.fn(() => mockCollection()),
      batch: vi.fn(() => ({
        set: vi.fn((ref: any, data: any) => {
          batchOps.push({ op: 'set', ref, data });
        }),
        update: vi.fn((ref: any, data: any) => {
          batchOps.push({ op: 'update', ref, data });
        }),
        commit: vi.fn(async () => {}),
      })),
    };
    mockAuth = {
      verifyIdToken: vi.fn(async () => ({ uid: 'admin-uid', admin: true })),
      setCustomUserClaims: vi.fn(async () => {}),
    };
    mockFcm = {
      sendToUser: vi.fn(async () => {}),
    };
    handlers = createVerificationHandlers({
      db: mockDb as any,
      auth: mockAuth as any,
      fcm: mockFcm as any,
      revenueCat: { grantDoctorEntitlement: vi.fn(async () => true) },
    });
  });

  it('approve: rejects non-admin caller', async () => {
    const { req, res, captured } = mockReqRes({ uid: 'user-123', profession: 'DOCTOR' }, 'non-admin-uid');
    (req as any).user = { uid: 'non-admin-uid', admin: false };

    await handlers.approve(req, res);

    expect(captured.status).toBe(403);
    expect(captured.json.error).toBe('Admin only');
  });

  it('approve: validates request body (missing profession)', async () => {
    const { req, res, captured } = mockReqRes({ uid: 'user-123' }, 'admin-uid');
    (req as any).user = { uid: 'admin-uid', admin: true };

    await handlers.approve(req, res);

    expect(captured.status).toBe(400);
    expect(captured.json.error).toContain('Invalid request');
  });

  it('approve: sets custom claims and sends FCM', async () => {
    const { req, res, captured } = mockReqRes({ uid: 'user-123', profession: 'DOCTOR' }, 'admin-uid');
    (req as any).user = { uid: 'admin-uid', admin: true };

    // Mock verification request exists and is pending
    const verificationDocSnap = mockDocSnap(true, { status: 'pending', uid: 'user-123' });
    mockDb.collection = vi.fn((name: string) => {
      if (name === 'verificationRequests') {
        return {
          doc: vi.fn(() => ({
            get: vi.fn(async () => verificationDocSnap),
          })),
        } as any;
      }
      return mockCollection() as any;
    });

    await handlers.approve(req, res);

    expect(mockAuth.setCustomUserClaims).toHaveBeenCalledWith('user-123', {
      verified: true,
      profession: 'DOCTOR',
      admin: false,
    });
    expect(mockFcm.sendToUser).toHaveBeenCalledWith(
      'user-123',
      'Verification approved',
      'Your professional profile is now verified.',
      expect.objectContaining({ type: 'VERIFICATION' })
    );
    expect(captured.status).toBe(200);
    expect(captured.json.success).toBe(true);
  });

  it('reject: updates status and sends notification', async () => {
    const { req, res, captured } = mockReqRes({ uid: 'user-123', reason: 'Incomplete documents' }, 'admin-uid');
    (req as any).user = { uid: 'admin-uid', admin: true };

    const verificationDocSnap = mockDocSnap(true, { status: 'pending', uid: 'user-123' });
    mockDb.collection = vi.fn((name: string) => {
      if (name === 'verificationRequests') {
        return {
          doc: vi.fn(() => ({
            get: vi.fn(async () => verificationDocSnap),
            update: vi.fn(async () => {}),
          })),
        } as any;
      }
      return mockCollection() as any;
    });

    await handlers.reject(req, res);

    expect(mockAuth.setCustomUserClaims).not.toHaveBeenCalled();
    expect(mockFcm.sendToUser).toHaveBeenCalledWith(
      'user-123',
      'Verification update',
      'Your application was not approved. You may re-apply.',
      expect.objectContaining({ type: 'VERIFICATION' })
    );
    expect(captured.status).toBe(200);
  });
});

describe('Phase 12R: Like Endpoint', () => {
  let mockDb: MockFirestore;
  let mockFcm: MockFcm;
  let handler: ReturnType<typeof createLikeHandler>;

  beforeEach(() => {
    const batchOps: any[] = [];
    mockDb = {
      collection: vi.fn(() => mockCollection()),
      batch: vi.fn(() => ({
        set: vi.fn((ref: any, data: any) => {
          batchOps.push({ op: 'set', ref, data });
        }),
        update: vi.fn((ref: any, data: any) => {
          batchOps.push({ op: 'update', ref, data });
        }),
        delete: vi.fn((ref: any) => {
          batchOps.push({ op: 'delete', ref });
        }),
        commit: vi.fn(async () => {}),
      })),
    };
    mockFcm = {
      sendToUser: vi.fn(async () => {}),
    };
    handler = createLikeHandler({ db: mockDb as any, fcm: mockFcm as any });
  });

  it('toggleLike: creates like when not exists', async () => {
    const { req, res, captured } = mockReqRes({}, 'user-123', { postId: 'post-456' });

    // Like does not exist
    const likeDocSnap = mockDocSnap(false);
    const postDocSnap = mockDocSnap(true, { authorId: 'author-789' });
    const userDocSnap = mockDocSnap(true, { name: 'John Doe' });

    mockDb.collection = vi.fn((name: string) => {
      if (name === 'posts') {
        return {
          doc: vi.fn((id: string) => ({
            collection: vi.fn(() => ({
              doc: vi.fn(() => ({
                get: vi.fn(async () => likeDocSnap),
              })),
            })),
            get: vi.fn(async () => postDocSnap),
          })),
        } as any;
      }
      if (name === 'users') {
        return {
          doc: vi.fn(() => ({
            get: vi.fn(async () => userDocSnap),
          })),
        } as any;
      }
      return mockCollection() as any;
    });

    await handler.toggleLike(req, res);

    expect(mockFcm.sendToUser).toHaveBeenCalledWith(
      'author-789',
      expect.stringContaining('liked'),
      expect.any(String),
      expect.objectContaining({ type: 'LIKE', postId: 'post-456' })
    );
    expect(captured.status).toBe(200);
    expect(captured.json.liked).toBe(true);
  });

  it('toggleLike: deletes like when exists', async () => {
    const { req, res, captured } = mockReqRes({}, 'user-123', { postId: 'post-456' });

    // Like exists
    const likeDocSnap = mockDocSnap(true, { createdAt: new Date() });

    mockDb.collection = vi.fn((name: string) => {
      if (name === 'posts') {
        return {
          doc: vi.fn(() => ({
            collection: vi.fn(() => ({
              doc: vi.fn(() => ({
                get: vi.fn(async () => likeDocSnap),
              })),
            })),
          })),
        } as any;
      }
      return mockCollection() as any;
    });

    await handler.toggleLike(req, res);

    expect(mockFcm.sendToUser).not.toHaveBeenCalled(); // No notification on unlike
    expect(captured.status).toBe(200);
    expect(captured.json.liked).toBe(false);
  });

  it('toggleLike: skips notification for self-like', async () => {
    const { req, res, captured } = mockReqRes({}, 'user-123', { postId: 'post-456' });

    const likeDocSnap = mockDocSnap(false);
    const postDocSnap = mockDocSnap(true, { authorId: 'user-123' }); // Same as liker

    mockDb.collection = vi.fn((name: string) => {
      if (name === 'posts') {
        return {
          doc: vi.fn(() => ({
            collection: vi.fn(() => ({
              doc: vi.fn(() => ({
                get: vi.fn(async () => likeDocSnap),
              })),
            })),
            get: vi.fn(async () => postDocSnap),
          })),
        } as any;
      }
      return mockCollection() as any;
    });

    await handler.toggleLike(req, res);

    expect(mockFcm.sendToUser).not.toHaveBeenCalled();
    expect(captured.status).toBe(200);
  });
});

describe('Phase 12R: Comment Endpoint', () => {
  let mockDb: MockFirestore;
  let mockFcm: MockFcm;
  let handler: ReturnType<typeof createCommentHandler>;

  beforeEach(() => {
    mockDb = {
      collection: vi.fn(() => mockCollection()),
      batch: vi.fn(() => ({
        set: vi.fn(),
        update: vi.fn(),
        commit: vi.fn(async () => {}),
      })),
    };
    mockFcm = {
      sendToUser: vi.fn(async () => {}),
    };
    handler = createCommentHandler({ db: mockDb as any, fcm: mockFcm as any });
  });

  it('addComment: validates body (empty comment)', async () => {
    const { req, res, captured } = mockReqRes({ body: '   ' }, 'user-123', { postId: 'post-456' });

    await handler.addComment(req, res);

    expect(captured.status).toBe(400);
    expect(captured.json.error).toContain('Invalid request');
  });

  it('addComment: validates body (too long)', async () => {
    const { req, res, captured } = mockReqRes({ body: 'a'.repeat(501) }, 'user-123', { postId: 'post-456' });

    await handler.addComment(req, res);

    expect(captured.status).toBe(400);
  });

  it('addComment: increments counter and sends notification', async () => {
    const { req, res, captured } = mockReqRes({ body: 'Great post!' }, 'user-123', { postId: 'post-456' });

    const userDocSnap = mockDocSnap(true, { name: 'Jane Smith' });
    const postDocSnap = mockDocSnap(true, { authorId: 'author-789' });

    mockDb.collection = vi.fn((name: string) => {
      if (name === 'users') {
        return {
          doc: vi.fn(() => ({
            get: vi.fn(async () => userDocSnap),
          })),
        } as any;
      }
      if (name === 'posts') {
        return {
          doc: vi.fn(() => ({
            collection: vi.fn(() => ({
              doc: vi.fn(() => ({ id: 'comment-id-123' })),
            })),
            get: vi.fn(async () => postDocSnap),
          })),
        } as any;
      }
      return mockCollection() as any;
    });

    await handler.addComment(req, res);

    expect(mockFcm.sendToUser).toHaveBeenCalledWith(
      'author-789',
      expect.stringContaining('commented'),
      'Great post!',
      expect.objectContaining({ type: 'COMMENT', postId: 'post-456' })
    );
    expect(captured.status).toBe(200);
    expect(captured.json.success).toBe(true);
    expect(captured.json.commentId).toBeDefined();
  });
});

describe('Phase 12R: Message Endpoint', () => {
  let mockDb: MockFirestore;
  let mockFcm: MockFcm;
  let handler: ReturnType<typeof createMessageHandler>;

  beforeEach(() => {
    mockDb = {
      collection: vi.fn(() => mockCollection()),
      batch: vi.fn(() => ({
        set: vi.fn(),
        commit: vi.fn(async () => {}),
      })),
    };
    mockFcm = {
      sendToUser: vi.fn(async () => {}),
    };
    handler = createMessageHandler({ db: mockDb as any, fcm: mockFcm as any });
  });

  it('sendMessage: validates message has text or image', async () => {
    const { req, res, captured } = mockReqRes(
      { recipientId: 'recipient-456', text: '', imageUrl: undefined },
      'user-123',
      { conversationId: 'conv-789' }
    );

    await handler.sendMessage(req, res);

    expect(captured.status).toBe(400);
    expect(captured.json.error).toContain('text or image');
  });

  it('sendMessage: verifies sender is participant', async () => {
    const { req, res, captured } = mockReqRes(
      { recipientId: 'recipient-456', text: 'Hello' },
      'user-123',
      { conversationId: 'conv-789' }
    );

    const convoDocSnap = mockDocSnap(true, { participants: ['other-user', 'recipient-456'] }); // sender not in list

    mockDb.collection = vi.fn((name: string) => {
      if (name === 'conversations') {
        return {
          doc: vi.fn(() => ({
            get: vi.fn(async () => convoDocSnap),
            collection: vi.fn(() => ({
              doc: vi.fn(() => ({ id: 'msg-id-123' })),
            })),
          })),
        } as any;
      }
      return mockCollection() as any;
    });

    await handler.sendMessage(req, res);

    expect(captured.status).toBe(403);
    expect(captured.json.error).toContain('participant');
  });

  it('sendMessage: writes message and sends notification', async () => {
    const { req, res, captured } = mockReqRes(
      { recipientId: 'recipient-456', text: 'Hello there' },
      'user-123',
      { conversationId: 'conv-789' }
    );

    const convoDocSnap = mockDocSnap(true, { participants: ['user-123', 'recipient-456'] });
    const senderDocSnap = mockDocSnap(true, { name: 'Alice' });

    mockDb.collection = vi.fn((name: string) => {
      if (name === 'conversations') {
        return {
          doc: vi.fn(() => ({
            get: vi.fn(async () => convoDocSnap),
            collection: vi.fn(() => ({
              doc: vi.fn(() => ({ id: 'msg-id-123' })),
            })),
          })),
        } as any;
      }
      if (name === 'users') {
        return {
          doc: vi.fn(() => ({
            get: vi.fn(async () => senderDocSnap),
          })),
        } as any;
      }
      if (name === 'notifications') {
        return {
          doc: vi.fn(() => ({
            collection: vi.fn(() => ({
              doc: vi.fn(() => ({
                set: vi.fn(async () => {}),
              })),
            })),
          })),
        } as any;
      }
      return mockCollection() as any;
    });

    await handler.sendMessage(req, res);

    expect(mockFcm.sendToUser).toHaveBeenCalledWith(
      'recipient-456',
      'Alice',
      'Hello there',
      expect.objectContaining({ type: 'MESSAGE', conversationId: 'conv-789' })
    );
    expect(captured.status).toBe(200);
    expect(captured.json.success).toBe(true);
    expect(captured.json.messageId).toBe('msg-id-123');
  });

  it('sendMessage: handles image-only message', async () => {
    const { req, res, captured } = mockReqRes(
      { recipientId: 'recipient-456', text: '', imageUrl: 'https://example.com/photo.jpg' },
      'user-123',
      { conversationId: 'conv-789' }
    );

    const convoDocSnap = mockDocSnap(true, { participants: ['user-123', 'recipient-456'] });
    const senderDocSnap = mockDocSnap(true, { name: 'Bob' });

    mockDb.collection = vi.fn((name: string) => {
      if (name === 'conversations') {
        return {
          doc: vi.fn(() => ({
            get: vi.fn(async () => convoDocSnap),
            collection: vi.fn(() => ({
              doc: vi.fn(() => ({ id: 'msg-id-456' })),
            })),
          })),
        } as any;
      }
      if (name === 'users') {
        return {
          doc: vi.fn(() => ({
            get: vi.fn(async () => senderDocSnap),
          })),
        } as any;
      }
      if (name === 'notifications') {
        return {
          doc: vi.fn(() => ({
            collection: vi.fn(() => ({
              doc: vi.fn(() => ({
                set: vi.fn(async () => {}),
              })),
            })),
          })),
        } as any;
      }
      return mockCollection() as any;
    });

    await handler.sendMessage(req, res);

    expect(mockFcm.sendToUser).toHaveBeenCalledWith(
      'recipient-456',
      'Bob',
      '📷 Photo',
      expect.objectContaining({ type: 'MESSAGE' })
    );
    expect(captured.status).toBe(200);
  });
});
