import { Request, Response } from 'express';
import admin from 'firebase-admin';
import { FcmService } from './fcm.js';
import { ToggleLikeResponse } from './types.js';

export interface LikeDeps {
  db: admin.firestore.Firestore;
  fcm: FcmService;
}

export function createLikeHandler(deps: LikeDeps) {

  async function toggleLike(req: Request, res: Response): Promise<void> {
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

    const postRef = deps.db.collection('posts').doc(postId);
    const likeRef = postRef.collection('likes').doc(uid);

    const likeSnap = await likeRef.get();
    const alreadyLiked = likeSnap.exists;

    if (alreadyLiked) {
      const batch = deps.db.batch();
      batch.delete(likeRef);
      batch.update(postRef, {
        likeCount: admin.firestore.FieldValue.increment(-1),
      });
      await batch.commit();
      console.log('[LIKE_REMOVED] uid=' + uid + ' post=' + postId);

      const response: ToggleLikeResponse = { success: true, liked: false };
      res.json(response);
      return;
    }

    const batch = deps.db.batch();
    batch.set(likeRef, {
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    batch.update(postRef, {
      likeCount: admin.firestore.FieldValue.increment(1),
    });
    await batch.commit();
    console.log('[LIKE_ADDED] uid=' + uid + ' post=' + postId);

    const postSnap = await postRef.get();
    const authorId = postSnap.data()?.authorId as string | undefined;

    if (authorId && authorId !== uid) {
      const userSnap = await deps.db.collection('users').doc(uid).get();
      const likerName = (userSnap.data()?.name as string) || 'Someone';

      const notifRef = deps.db.collection('notifications').doc(authorId).collection('items').doc();
      await notifRef.set({
        type: 'LIKE',
        title: likerName + ' liked your post',
        body: '',
        read: false,
        deeplink: 'omni://post/' + postId,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      try {
        await deps.fcm.sendToUser(authorId, likerName + ' liked your post', '', { type: 'LIKE', postId });
      } catch (fcmError) {
        console.error('[LIKE_FCM_ERROR] author=' + authorId, fcmError);
      }
    }

    const response: ToggleLikeResponse = { success: true, liked: true };
    res.json(response);
  }

  return { toggleLike };
}