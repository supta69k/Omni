import { Firestore, Timestamp } from 'firebase-admin/firestore';

export interface RateLimitConfig {
  points: number;
  durationSeconds: number;
  blockDurationSeconds: number;
}

export interface RateLimitInfo {
  consumedPoints: number;
  remainingPoints: number;
  isFirstInDuration: boolean;
  msBeforeNext: number;
}

export interface RateLimitError extends Error {
  msBeforeNext?: number;
  remainingPoints?: number;
  consumedPoints?: number;
}

export class FirestoreRateLimiter {
  private db: Firestore;
  private collectionName: string;
  private keyPrefix: string;
  private config: RateLimitConfig;

  constructor(db: Firestore, collectionName: string, keyPrefix: string, config: RateLimitConfig) {
    this.db = db;
    this.collectionName = collectionName;
    this.keyPrefix = keyPrefix;
    this.config = config;
  }

  private getDocRef(key: string) {
    return this.db.collection(this.collectionName).doc(`${this.keyPrefix}${key}`);
  }

  async consume(key: string, points: number = 1): Promise<RateLimitInfo> {
    const docRef = this.getDocRef(key);
    const now = Date.now();
    const durationMs = this.config.durationSeconds * 1000;
    const windowStart = now - durationMs;

    return this.db.runTransaction(async (transaction) => {
      const docSnap = await transaction.get(docRef);
      let data: { points: number[]; blockedUntil?: number } = { points: [] };

      if (docSnap.exists) {
        const existing = docSnap.data();
        if (existing) {
          data = {
            points: existing.points || [],
            blockedUntil: existing.blockedUntil,
          };
        }
      }

      // Check if currently blocked
      if (data.blockedUntil && data.blockedUntil > now) {
        const msBeforeNext = data.blockedUntil - now;
        const error = new Error('Rate limit exceeded') as Error & { msBeforeNext: number };
        error.msBeforeNext = msBeforeNext;
        throw error;
      }

      // Filter points within current window
      const recentPoints = data.points.filter(ts => ts > windowStart);
      const consumedPoints = recentPoints.length + points;

      if (consumedPoints > this.config.points) {
        // Block for blockDurationSeconds
        const blockedUntil = now + this.config.blockDurationSeconds * 1000;
        const error = new Error('Rate limit exceeded') as Error & { msBeforeNext: number };
        error.msBeforeNext = this.config.blockDurationSeconds * 1000;
        
        await transaction.set(docRef, {
          points: recentPoints,
          blockedUntil,
        });
        throw error;
      }

      // Add current request points
      const newPoints = [...recentPoints];
      for (let i = 0; i < points; i++) {
        newPoints.push(now);
      }

      await transaction.set(docRef, {
        points: newPoints,
      });

      return {
        consumedPoints,
        remainingPoints: Math.max(0, this.config.points - consumedPoints),
        isFirstInDuration: recentPoints.length === 0,
        msBeforeNext: durationMs,
      };
    });
  }

  async reset(key: string): Promise<void> {
    await this.getDocRef(key).delete();
  }
}