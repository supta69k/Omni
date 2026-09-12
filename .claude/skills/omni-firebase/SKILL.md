---
name: omni-firebase
description: Omni's Firebase setup — Auth, the Firestore collection map, the composite indexes, the security-rules model and its emulator tests, and the Cloudinary upload path. Use before changing any repository, rule, or index, or before adding a media upload.
---

# Omni Firebase

Firebase Auth + Cloud Firestore, **Spark plan**. Cloudinary handles media because Firebase Storage is
Blaze-only. See [[omni-architecture]] for the layering these rules sit under.

## Auth

`AuthRepository` (impl `FirebaseAuthRepository`, fake `PreviewAuthRepository`) exposes `authState`,
`sessionUid`, `currentUid`, `signUp`, `signIn`, `sendPasswordReset`, `signOut`. `signUp` creates the
account *and* its `users/{uid}` document in one flow. Firebase errors are mapped to user-facing
sentences by `firebaseAuthErrorMessage(code)` — add new cases there rather than formatting raw
exceptions in a screen.

`sessionUid` is the flow key for every read. `currentUid` is for actions only.

## Collection map

```
users/{uid}
  days/{yyyy-MM-dd}                 metrics + steps for that day
  days/{yyyy-MM-dd}/meals/{mealId}
  guideProgress/{guideId}
  emergencyContacts/{contactId}
  following/{targetUid}
  followers/{followerUid}
posts/{postId}
  likes/{uid}  reposts/{uid}  comments/{commentId}
conversations/{cid}                  cid = sorted uid pair joined with "_"
  messages/{mid}
stories/{storyId}
notifications/{uid}/items/{itemId}
sosEvents/{uid}/items/{itemId}
verificationRequests/{uid}
hospitals/{hospitalId}               read-only directory, written from the console
```

## Composite indexes

Declared in `firestore.indexes.json`:

- `conversations`: `participants ARRAY_CONTAINS` + `lastMessageAt DESC`
- `posts`: `authorId ASC` + `createdAt DESC` — serves both the profile grid and the chunked Following query

`stories` deliberately needs none: one `orderBy("createdAt" DESC).limit(50)` listener with expiry
filtered client-side. If a new query needs an index, add it here and say so explicitly in the report.

## Security rules

`firestore.rules` is the real thing and is covered by emulator tests in `tools/rules.test.mjs`
(`npm --prefix tools run test:rules`). The shape to preserve:

- `users/{uid}` — own document writable by the owner; `following` writable by the owner,
  `followers/{follower}` writable by the follower.
- `posts/{postId}` — author-owned; `likes/{uid}`, `reposts/{uid}`, `comments/{commentId}` require the
  writer's own uid on their own document.
- `conversations/{cid}` — read and update require being in `resource.data.participants`; create requires
  exactly two *different* participants including the caller; `participants` is immutable on update.
- `conversations/{cid}/messages/{mid}` — create requires `participantOf(cid)` **and**
  `senderId == request.auth.uid`; update and delete are `false`.

Two traps worth remembering: `resource` is **null** on a document that does not exist, so a rule that
dereferences `resource.data` denies rather than reporting "not found"; and `get()` inside a batched
write does **not** see the batch's other writes, so a conversation document must exist before any
message create can satisfy `participantOf`.

**Never** write `allow read, write: if true`, and never weaken a rule to make a feature work. Every new
rule gets a test pair: the thing the app does must succeed, and the nearest abuse of it must fail.

## Deployment

`firebase.json` and `.firebaserc` are configured; the Firestore emulator is pinned to port **8085**.
On Windows `emulators:exec` can leave the Java child holding the port — recover with
`netstat -ano | grep ':8085.*LISTENING'` then `taskkill //PID <pid> //F`.

## Cloudinary

`MediaRepository` is the single upload path. Cloud name `g3zuzufg`, **unsigned** preset `omni_mobile`.
The API secret must never appear in Android code, and no Firebase service-account JSON may enter the
app module or the repository. Validate type and size **before** uploading, not after.

## Blaze-plan consequences

No Cloud Functions, no Firebase Storage, no FCM server sends. Denormalised counters therefore stay 0 and
are computed by client aggregation; thread summaries and unread counts are maintained by the sending
client.
