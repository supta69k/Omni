import admin from 'firebase-admin';

export interface FcmService {
  sendToUser(uid: string, title: string, body: string, data?: Record<string, string>): Promise<void>;
}

export class FirestoreFcmService implements FcmService {
  constructor(
    private readonly db: admin.firestore.Firestore,
    private readonly messaging: admin.messaging.Messaging,
  ) {}

  async sendToUser(
    uid: string,
    title: string,
    body: string,
    data?: Record<string, string>,
  ): Promise<void> {
    const userSnap = await this.db.collection('users').doc(uid).get();
    const tokens: unknown = userSnap.data()?.fcmTokens;
    if (!Array.isArray(tokens) || tokens.length === 0) {
      console.log('[FCM_SKIP] uid=' + uid + ' reason=no_tokens');
      return;
    }

    const validTokens = tokens.filter((t): t is string => typeof t === 'string' && t.length > 0);
    if (validTokens.length === 0) {
      console.log('[FCM_SKIP] uid=' + uid + ' reason=no_valid_tokens');
      return;
    }

    const staleTokens: string[] = [];

    for (const token of validTokens) {
      try {
        await this.messaging.send({
          token,
          notification: { title: title.slice(0, 60), body: body.slice(0, 160) },
          data: data ?? {},
          android: { priority: 'high' },
        });
        console.log('[FCM_SENT] uid=' + uid);
      } catch (error: any) {
        const code: string = error?.code ?? '';
        if (
          code === 'messaging/invalid-registration-token' ||
          code === 'messaging/registration-token-not-registered'
        ) {
          staleTokens.push(token);
          console.log('[FCM_STALE_TOKEN] uid=' + uid);
        } else {
          console.error('[FCM_SEND_ERROR] uid=' + uid + ' code=' + code);
        }
      }
    }

    if (staleTokens.length > 0) {
      try {
        await this.db.collection('users').doc(uid).update({
          fcmTokens: admin.firestore.FieldValue.arrayRemove(...staleTokens),
        });
        console.log('[FCM_CLEANED_TOKENS] uid=' + uid + ' count=' + staleTokens.length);
      } catch (cleanupError) {
        console.error('[FCM_CLEANUP_ERROR] uid=' + uid, cleanupError);
      }
    }
  }
}

export class NoOpFcmService implements FcmService {
  async sendToUser(): Promise<void> {}
}
