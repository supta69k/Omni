import { Request, Response } from 'express';
import { z } from 'zod';
import admin from 'firebase-admin';
import { FcmService } from './fcm.js';
import { SendMessageResponse } from './types.js';

export interface MessageDeps {
  db: admin.firestore.Firestore;
  fcm: FcmService;
}

const sendMessageSchema = z.object({
  recipientId: z.string().min(1),
  text: z.string().max(2000).optional().default(''),
  imageUrl: z.string().url().optional(),
});

export function createMessageHandler(deps: MessageDeps) {

  async function sendMessage(req: Request, res: Response): Promise<void> {
    const senderId = (req as any).user?.uid as string | undefined;
    if (!senderId) {
      res.status(401).json({ error: 'Unauthenticated' });
      return;
    }

    const { conversationId } = req.params;
    if (!conversationId) {
      res.status(400).json({ error: 'conversationId is required' });
      return;
    }

    const parsed = sendMessageSchema.safeParse(req.body);
    if (!parsed.success) {
      res.status(400).json({ error: 'Invalid request', details: parsed.error.flatten() });
      return;
    }
    const { recipientId, text, imageUrl } = parsed.data;
    const body = text.trim();
    if (!body && !imageUrl) {
      res.status(400).json({ error: 'Message must have text or image' });
      return;
    }

    const convoRef = deps.db.collection('conversations').doc(conversationId);
    const convoSnap = await convoRef.get();

    if (convoSnap.exists) {
      const participants = convoSnap.data()?.participants as string[] | undefined;
      if (!participants || !participants.includes(senderId)) {
        res.status(403).json({ error: 'Not a participant of this conversation' });
        return;
      }
      // The unread counter below is incremented for recipientId, so it must actually be the other
      // participant of this thread — never a uid the client picked freely. Otherwise a malformed or
      // malicious request could inflate a stranger's unread badge.
      if (recipientId === senderId || !participants.includes(recipientId)) {
        res.status(400).json({ error: 'recipientId is not a participant of this conversation' });
        return;
      }
    }

    const messageRef = convoRef.collection('messages').doc();
    const messageData: Record<string, any> = {
      senderId,
      text: body,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };
    if (imageUrl) {
      messageData.imageUrl = imageUrl;
    }

    const preview = body || '📷 Photo';

    const batch = deps.db.batch();
    batch.set(messageRef, messageData);
    batch.set(convoRef, {
      lastMessage: preview.slice(0, 200),
      lastMessageAt: admin.firestore.FieldValue.serverTimestamp(),
      lastSenderId: senderId,
      unread: { [recipientId]: admin.firestore.FieldValue.increment(1) },
    }, { merge: true });
    await batch.commit();

    console.log('[MESSAGE_SENT] from=' + senderId + ' convo=' + conversationId + ' msg=' + messageRef.id);

    // The message is committed — the recipient already has it via their listener. Answer the sender
    // NOW, before the best-effort notification/push work below, so that a failure in the notification
    // read/write or FCM can never turn an already-delivered message into a 500 that makes the sender
    // think their message was lost. That asymmetry (recipient sees it, sender gets an error and, with
    // no optimistic echo, sees nothing) was the sender-missing-message bug.
    const response: SendMessageResponse = { success: true, messageId: messageRef.id };
    res.json(response);

    // Best-effort, after the response: an in-app notification and a push. Wrapped as one so a transient
    // Firestore read/write or FCM failure is logged, not thrown into an already-answered request.
    try {
      const senderSnap = await deps.db.collection('users').doc(senderId).get();
      const senderName = (senderSnap.data()?.name as string) || 'Someone';

      const notifRef = deps.db.collection('notifications').doc(recipientId).collection('items').doc();
      await notifRef.set({
        type: 'MESSAGE',
        title: senderName,
        body: preview.slice(0, 160),
        read: false,
        deeplink: 'omni://chat/' + conversationId,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      await deps.fcm.sendToUser(recipientId, senderName, preview.slice(0, 160), { type: 'MESSAGE', conversationId });
    } catch (notifyError) {
      console.error('[MESSAGE_NOTIFY_ERROR] recipient=' + recipientId, notifyError);
    }
  }

  return { sendMessage };
}