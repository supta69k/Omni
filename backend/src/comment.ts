import { Request, Response } from 'express';
import { z } from 'zod';
import admin from 'firebase-admin';
import { FcmService } from './fcm.js';
import { AddCommentResponse } from './types.js';

export interface CommentDeps {
  db: admin.firestore.Firestore;
  fcm: FcmService;
}

const addCommentSchema = z.object({
  body: z.string().trim().min(1, 'Comment cannot be empty').max(500),
});

export function createCommentHandler(deps: CommentDeps) {

  async function addComment(req: Request, res: Response): Promise<void> {
    const uid = (req as any).user?.uid as string | undefined;
    if (!uid) {
      res.status(401).json({ error: 'Unauthenticated' });
      return;
    }

    const { postId } = req.params;
    if (!postId) {
      res.status(400).json({ error: 'postId is required' });
      return;
    }

    const parsed = addCommentSchema.safeParse(req.body);
    if (!parsed.success) {
      res.status(400).json({ error: 'Invalid request', details: parsed.error.flatten() });
      return;
    }
    const { body } = parsed.data;

    const userSnap = await deps.db.collection('users').doc(uid).get();
    const authorName = (userSnap.data()?.name as string) || 'Someone';

    const postRef = deps.db.collection('posts').doc(postId);
    const commentsRef = postRef.collection('comments');

    const commentRef = commentsRef.doc();
    const batch = deps.db.batch();

    batch.set(commentRef, {
      authorId: uid,
      authorName,
      body,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    batch.update(postRef, {
      commentCount: admin.firestore.FieldValue.increment(1),
    });
    await batch.commit();
    console.log('[COMMENT_ADDED] uid=' + uid + ' post=' + postId + ' comment=' + commentRef.id);

    const postSnap = await postRef.get();
    const authorId = postSnap.data()?.authorId as string | undefined;

    if (authorId && authorId !== uid) {
      const notifRef = deps.db.collection('notifications').doc(authorId).collection('items').doc();
      await notifRef.set({
        type: 'COMMENT',
        title: authorName + ' commented on your post',
        body: body.slice(0, 160),
        read: false,
        deeplink: 'omni://post/' + postId,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      try {
        await deps.fcm.sendToUser(authorId, authorName + ' commented on your post', body.slice(0, 160), { type: 'COMMENT', postId });
      } catch (fcmError) {
        console.error('[COMMENT_FCM_ERROR] author=' + authorId, fcmError);
      }
    }

    const response: AddCommentResponse = { success: true, commentId: commentRef.id };
    res.json(response);
  }

  return { addComment };
}