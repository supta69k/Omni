/**
 * Security-rules tests for `firestore.rules` — BACKEND_PLAN Phase 13.
 *
 * Run them with:
 *
 *     npm --prefix tools run test:rules
 *
 * which starts the Firestore emulator, loads the real `firestore.rules` from the repo root, and runs
 * this file against it. Nothing here touches the live project: the emulator is a local process and
 * the tests never authenticate to Google.
 *
 * **What this file is for.** Rules are the only part of this app that cannot be checked by the
 * compiler and cannot be exercised from the phone — a rule that is too permissive looks exactly like
 * a rule that is correct, right up until somebody takes advantage of it. So every assertion below is
 * written as a pair where it matters: the thing the app actually does must succeed, and the nearest
 * abuse of it must fail. A test that only proves the happy path proves nothing about a security rule.
 *
 * Uses Node's built-in test runner (`node:test`), so the only dependency is the Firebase testing
 * library itself.
 *
 * **If the run dies with "Could not start Firestore Emulator, port taken":** on Windows the CLI's
 * SIGINT reaches its own wrapper but not the emulator's Java child, so the previous run can still be
 * holding the port a minute later. Find it and end it:
 *
 *     netstat -ano | grep ':8085'
 *     taskkill //PID <pid> //F
 *
 * The emulator keeps everything in memory, so nothing is lost by killing it. Port 8085 is pinned in
 * `firebase.json` rather than the default 8080, which is too contested a port to rely on.
 */

import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import { after, before, describe, it } from 'node:test'

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing'
import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  getDoc,
  setDoc,
  updateDoc,
} from 'firebase/firestore'

const here = dirname(fileURLToPath(import.meta.url))
const rulesPath = join(here, '..', 'firestore.rules')

const ALICE = 'alice-uid'
const BOB = 'bob-uid'
const CAROL = 'carol-uid'

let testEnv
/** Alice, Bob and Carol as ordinary signed-in users; `admin` carries the custom claim. */
let alice
let bob
let carol
let admin
/** A signed-out client — every collection in this app requires a session. */
let anon

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-omni',
    firestore: { rules: readFileSync(rulesPath, 'utf8') },
  })
  alice = testEnv.authenticatedContext(ALICE).firestore()
  bob = testEnv.authenticatedContext(BOB).firestore()
  carol = testEnv.authenticatedContext(CAROL).firestore()
  admin = testEnv.authenticatedContext('admin-uid', { admin: true }).firestore()
  anon = testEnv.unauthenticatedContext().firestore()
})

after(async () => {
  await testEnv?.cleanup()
})

/** Seeds a document past the rules, so a test can start from a state the client cannot create. */
async function seed(path, data) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), path), data)
  })
}

const newUser = (overrides = {}) => ({
  name: 'A Name',
  email: 'a@example.com',
  role: 'USER',
  verified: false,
  createdAt: 1,
  ...overrides,
})

const newPost = (authorId, overrides = {}) => ({
  authorId,
  authorName: 'A Name',
  body: 'hello',
  likeCount: 0,
  commentCount: 0,
  repostCount: 0,
  createdAt: 1,
  ...overrides,
})

// ---- users -------------------------------------------------------------------------------------

describe('users/{uid}', () => {
  it('lets a signed-in user create their own profile as an ordinary user', async () => {
    await assertSucceeds(setDoc(doc(alice, `users/${ALICE}`), newUser()))
  })

  it('refuses a profile created for somebody else', async () => {
    await assertFails(setDoc(doc(alice, `users/${BOB}`), newUser()))
  })

  /**
   * DEVIATION 1's whole reason. The update rule blocks *changing* role and verified — nothing was
   * stopping a hand-rolled client from being born with them.
   */
  it('refuses a profile born as an admin, or already verified, or with a profession', async () => {
    await assertFails(setDoc(doc(bob, `users/${BOB}`), newUser({ role: 'ADMIN' })))
    await assertFails(setDoc(doc(bob, `users/${BOB}`), newUser({ verified: true })))
    await assertFails(setDoc(doc(bob, `users/${BOB}`), newUser({ profession: 'DOCTOR' })))
  })

  it('lets a user edit their own ordinary fields', async () => {
    await seed(`users/${ALICE}`, newUser())
    await assertSucceeds(updateDoc(doc(alice, `users/${ALICE}`), { name: 'Alice A' }))
    await assertSucceeds(updateDoc(doc(alice, `users/${ALICE}`), { waterGoal: 10 }))
  })

  it('refuses a user granting themselves a badge, a role, or an unread count', async () => {
    await seed(`users/${ALICE}`, newUser())
    await assertFails(updateDoc(doc(alice, `users/${ALICE}`), { verified: true }))
    await assertFails(updateDoc(doc(alice, `users/${ALICE}`), { role: 'ADMIN' }))
    await assertFails(updateDoc(doc(alice, `users/${ALICE}`), { profession: 'DOCTOR' }))
    await assertFails(updateDoc(doc(alice, `users/${ALICE}`), { unread: 99 }))
  })

  it('lets an admin grant the badge', async () => {
    await seed(`users/${ALICE}`, newUser())
    await assertSucceeds(
      updateDoc(doc(admin, `users/${ALICE}`), { verified: true, role: 'PROFESSIONAL' }),
    )
  })

  it('refuses a user editing anybody else', async () => {
    await seed(`users/${BOB}`, newUser())
    await assertFails(updateDoc(doc(alice, `users/${BOB}`), { name: 'not yours' }))
  })

  it('never lets a profile be deleted, not even by its owner or an admin', async () => {
    await seed(`users/${ALICE}`, newUser())
    await assertFails(deleteDoc(doc(alice, `users/${ALICE}`)))
    await assertFails(deleteDoc(doc(admin, `users/${ALICE}`)))
  })

  it('is readable by any signed-in user and by nobody else', async () => {
    await seed(`users/${BOB}`, newUser())
    await assertSucceeds(getDoc(doc(alice, `users/${BOB}`)))
    await assertFails(getDoc(doc(anon, `users/${BOB}`)))
  })
})

// ---- the follow graph --------------------------------------------------------------------------

describe('the follow graph', () => {
  it('lets a user follow somebody — both halves of the batch', async () => {
    await assertSucceeds(setDoc(doc(alice, `users/${ALICE}/following/${BOB}`), { createdAt: 1 }))
    await assertSucceeds(setDoc(doc(alice, `users/${BOB}/followers/${ALICE}`), { createdAt: 1 }))
  })

  it('lets a user unfollow', async () => {
    await seed(`users/${ALICE}/following/${BOB}`, { createdAt: 1 })
    await seed(`users/${BOB}/followers/${ALICE}`, { createdAt: 1 })
    await assertSucceeds(deleteDoc(doc(alice, `users/${ALICE}/following/${BOB}`)))
    await assertSucceeds(deleteDoc(doc(alice, `users/${BOB}/followers/${ALICE}`)))
  })

  /**
   * The follower count is an aggregation over this collection, so "can somebody else add a document
   * to it" is the same question as "can somebody inflate my follower count".
   */
  it('refuses inserting a third party into anybody\'s follower list', async () => {
    await assertFails(setDoc(doc(alice, `users/${BOB}/followers/${CAROL}`), { createdAt: 1 }))
  })

  it('refuses removing somebody else from a follower list', async () => {
    await seed(`users/${BOB}/followers/${CAROL}`, { createdAt: 1 })
    await assertFails(deleteDoc(doc(alice, `users/${BOB}/followers/${CAROL}`)))
  })

  it('refuses writing into somebody else\'s following list', async () => {
    await assertFails(setDoc(doc(alice, `users/${BOB}/following/${CAROL}`), { createdAt: 1 }))
  })
})

// ---- posts -------------------------------------------------------------------------------------

describe('posts/{postId}', () => {
  it('lets an author publish their own post with zeroed counters', async () => {
    await assertSucceeds(setDoc(doc(alice, 'posts/p1'), newPost(ALICE)))
  })

  it('refuses a post published under somebody else\'s name', async () => {
    await assertFails(setDoc(doc(alice, 'posts/p2'), newPost(BOB)))
  })

  /** DEVIATION 2: the client may not touch a counter, at birth or afterwards. */
  it('refuses a post born with counters already set', async () => {
    await assertFails(setDoc(doc(alice, 'posts/p3'), newPost(ALICE, { likeCount: 500 })))
    await assertFails(setDoc(doc(alice, 'posts/p4'), newPost(ALICE, { commentCount: 500 })))
    await assertFails(setDoc(doc(alice, 'posts/p4b'), newPost(ALICE, { repostCount: 500 })))
  })

  it('refuses a post body past the two-thousand-character ceiling', async () => {
    await assertFails(setDoc(doc(alice, 'posts/p5'), newPost(ALICE, { body: 'x'.repeat(2001) })))
  })

  it('lets an author edit their own post', async () => {
    await seed('posts/p6', newPost(ALICE))
    await assertSucceeds(updateDoc(doc(alice, 'posts/p6'), { body: 'edited' }))
  })

  /**
   * Phase 13's first closed hole. The old rule matched on the *stored* `authorId`, so an author could
   * hand their post to another uid — where it would appear under that person's name, on their profile,
   * carrying their badge.
   */
  it('refuses an author reassigning their post to somebody else', async () => {
    await seed('posts/p7', newPost(ALICE))
    await assertFails(updateDoc(doc(alice, 'posts/p7'), { authorId: BOB }))
  })

  it('refuses editing or deleting anybody else\'s post', async () => {
    await seed('posts/p8', newPost(ALICE))
    await assertFails(updateDoc(doc(bob, 'posts/p8'), { body: 'not yours' }))
    await assertFails(deleteDoc(doc(bob, 'posts/p8')))
  })

  it('lets an author delete their own post, and an admin delete anybody\'s', async () => {
    await seed('posts/p9', newPost(ALICE))
    await assertSucceeds(deleteDoc(doc(alice, 'posts/p9')))
    await seed('posts/p10', newPost(ALICE))
    await assertSucceeds(deleteDoc(doc(admin, 'posts/p10')))
  })

  it('is invisible to a signed-out client', async () => {
    await seed('posts/p11', newPost(ALICE))
    await assertFails(getDoc(doc(anon, 'posts/p11')))
  })
})

describe('posts/{postId}/likes/{uid}', () => {
  /** One document per liker is what makes the count forgery-proof: the id *is* the liker. */
  it('lets a user like and unlike as themselves', async () => {
    await seed('posts/liked', newPost(ALICE))
    await assertSucceeds(setDoc(doc(bob, 'posts/liked/likes/' + BOB), { createdAt: 1 }))
    await assertSucceeds(deleteDoc(doc(bob, 'posts/liked/likes/' + BOB)))
  })

  it('refuses a like written under somebody else\'s uid', async () => {
    await seed('posts/liked2', newPost(ALICE))
    await assertFails(setDoc(doc(bob, 'posts/liked2/likes/' + CAROL), { createdAt: 1 }))
  })

  it('refuses removing somebody else\'s like', async () => {
    await seed('posts/liked3', newPost(ALICE))
    await seed(`posts/liked3/likes/${CAROL}`, { createdAt: 1 })
    await assertFails(deleteDoc(doc(bob, `posts/liked3/likes/${CAROL}`)))
  })
})

describe('posts/{postId}/reposts/{uid}', () => {
  /**
   * The marker whose id is the reposter, the like's own shape. What makes the repost count honest:
   * one person can write exactly one, under their own uid.
   */
  it('lets a user mark their own repost, and doing it twice writes one document', async () => {
    await seed('posts/r1', newPost(ALICE))
    await assertSucceeds(setDoc(doc(bob, `posts/r1/reposts/${BOB}`), { createdAt: 1 }))
    await assertSucceeds(setDoc(doc(bob, `posts/r1/reposts/${BOB}`), { createdAt: 2 }))
  })

  it('refuses a repost marked under somebody else\'s uid', async () => {
    await seed('posts/r2', newPost(ALICE))
    await assertFails(setDoc(doc(bob, `posts/r2/reposts/${CAROL}`), { createdAt: 1 }))
  })

  it('refuses withdrawing somebody else\'s repost', async () => {
    await seed('posts/r3', newPost(ALICE))
    await seed(`posts/r3/reposts/${CAROL}`, { createdAt: 1 })
    await assertFails(deleteDoc(doc(bob, `posts/r3/reposts/${CAROL}`)))
  })

  it('is invisible to a signed-out client', async () => {
    await seed('posts/r4', newPost(ALICE))
    await seed(`posts/r4/reposts/${BOB}`, { createdAt: 1 })
    await assertFails(getDoc(doc(anon, `posts/r4/reposts/${BOB}`)))
  })
})

describe('posts/{postId}/comments', () => {
  it('lets any signed-in user comment under their own name', async () => {
    await seed('posts/c1', newPost(ALICE))
    await assertSucceeds(
      addDoc(collection(bob, 'posts/c1/comments'), { authorId: BOB, body: 'nice', createdAt: 1 }),
    )
  })

  it('refuses a comment signed with somebody else\'s uid', async () => {
    await seed('posts/c2', newPost(ALICE))
    await assertFails(
      addDoc(collection(bob, 'posts/c2/comments'), { authorId: CAROL, body: 'nope', createdAt: 1 }),
    )
  })

  /**
   * Phase 13. The composer caps at 280; this is what stops a hand-rolled client putting a megabyte
   * of text on somebody else's post, in a collection every reader of that post downloads.
   */
  it('refuses a comment past the five-hundred-character ceiling', async () => {
    await seed('posts/c3', newPost(ALICE))
    await assertFails(
      addDoc(collection(bob, 'posts/c3/comments'), {
        authorId: BOB,
        body: 'x'.repeat(501),
        createdAt: 1,
      }),
    )
  })

  it('lets a commenter delete their own comment and nobody else\'s', async () => {
    await seed('posts/c4', newPost(ALICE))
    await seed('posts/c4/comments/one', { authorId: BOB, body: 'mine', createdAt: 1 })
    await assertFails(deleteDoc(doc(carol, 'posts/c4/comments/one')))
    await assertSucceeds(deleteDoc(doc(bob, 'posts/c4/comments/one')))
  })
})

// ---- verification ------------------------------------------------------------------------------

describe('verificationRequests/{uid}', () => {
  const application = (overrides = {}) => ({
    uid: ALICE,
    name: 'Alice A',
    profession: 'DOCTOR',
    licenseNumber: 'BMDC-A-1',
    status: 'pending',
    submittedAt: 1,
    ...overrides,
  })

  it('lets an applicant submit a pending application for themselves', async () => {
    await assertSucceeds(setDoc(doc(alice, `verificationRequests/${ALICE}`), application()))
  })

  it('refuses an application that awards itself the decision', async () => {
    await assertFails(
      setDoc(doc(bob, `verificationRequests/${BOB}`), application({ uid: BOB, status: 'approved' })),
    )
  })

  it('refuses an application filed on somebody else\'s behalf', async () => {
    await assertFails(setDoc(doc(alice, `verificationRequests/${BOB}`), application({ uid: BOB })))
  })

  it('is readable by the applicant and an admin, and by nobody else', async () => {
    await seed(`verificationRequests/${ALICE}`, application())
    await assertSucceeds(getDoc(doc(alice, `verificationRequests/${ALICE}`)))
    await assertSucceeds(getDoc(doc(admin, `verificationRequests/${ALICE}`)))
    await assertFails(getDoc(doc(bob, `verificationRequests/${ALICE}`)))
  })

  /** A pending application must not be edited out from under the reviewer reading it. */
  it('refuses re-submitting while an application is pending', async () => {
    await seed(`verificationRequests/${ALICE}`, application())
    await assertFails(
      updateDoc(doc(alice, `verificationRequests/${ALICE}`), { licenseNumber: 'BMDC-A-2' }),
    )
  })

  it('lets an applicant re-apply after a rejection', async () => {
    await seed(`verificationRequests/${ALICE}`, application({ status: 'rejected', note: 'no' }))
    await assertSucceeds(
      updateDoc(doc(alice, `verificationRequests/${ALICE}`), {
        status: 'pending',
        licenseNumber: 'BMDC-A-2',
      }),
    )
  })

  /** A rejected application may only go back to pending — never straight to approved. */
  it('refuses an applicant approving their own rejected application', async () => {
    await seed(`verificationRequests/${ALICE}`, application({ status: 'rejected' }))
    await assertFails(
      updateDoc(doc(alice, `verificationRequests/${ALICE}`), { status: 'approved' }),
    )
  })

  it('refuses an approved application being reopened by the account that benefits from it', async () => {
    await seed(`verificationRequests/${ALICE}`, application({ status: 'approved' }))
    await assertFails(updateDoc(doc(alice, `verificationRequests/${ALICE}`), { status: 'pending' }))
  })

  it('lets an admin decide', async () => {
    await seed(`verificationRequests/${ALICE}`, application())
    await assertSucceeds(
      updateDoc(doc(admin, `verificationRequests/${ALICE}`), { status: 'approved', reviewedAt: 2 }),
    )
  })
})

// ---- messaging ---------------------------------------------------------------------------------

describe('conversations and their messages', () => {
  const thread = (overrides = {}) => ({
    participants: [ALICE, BOB],
    lastMessage: '',
    lastMessageAt: 1,
    unread: { [ALICE]: 0, [BOB]: 0 },
    ...overrides,
  })

  it('lets a participant open a two-person thread', async () => {
    await assertSucceeds(setDoc(doc(alice, `conversations/${ALICE}_${BOB}`), thread()))
  })

  /**
   * `conversationIdentityMap` — exactly what `FirestoreMessageRepository.openConversation` writes.
   *
   * It carries the pair and their denormalised names and photos, and **none** of the summary fields
   * (`lastMessage`, `lastMessageAt`, `unread`), which is what makes it safe to merge onto a thread
   * that already has traffic in it.
   */
  const identity = (self, other) => ({
    participants: [self, other].sort(),
    participantNames: { [self]: 'Self', [other]: 'Other' },
    participantPhotos: { [self]: null, [other]: null },
  })

  /**
   * The Message button on a public profile, first tap — the thread document does not exist yet.
   *
   * This is the regression that broke user-to-user messaging on a real device. The repository used
   * to `get()` the document before writing it, and `allow read: if … request.auth.uid in
   * resource.data.participants` **denies** on a document that does not exist, because `resource` is
   * null and the expression errors. The client saw PERMISSION_DENIED rather than "not found", so the
   * first message between any two people could never be sent. The rules were right; the read was the
   * bug. This asserts the merged write alone — no read — satisfies `allow create`.
   */
  it('lets the Message button open a brand-new thread with one merged write', async () => {
    await assertSucceeds(
      setDoc(
        doc(alice, `conversations/${ALICE}_${BOB}`),
        identity(ALICE, BOB),
        { merge: true },
      ),
    )
  })

  /**
   * The same tap on a thread that already exists — re-entering a conversation you have history in.
   *
   * The merge must land on `allow update`, whose second clause requires `participants` to come back
   * byte-identical. `conversationIdentityMap` sorts the pair, so both sides of a conversation
   * produce the same array regardless of who is calling — this is what proves that, from Bob's side
   * rather than Alice's.
   */
  it('lets the other participant re-open an existing thread without changing the pair', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), `conversations/${ALICE}_${BOB}`),
        thread({ lastMessage: 'already talking', unread: { [ALICE]: 0, [BOB]: 3 } }),
      )
    })

    await assertSucceeds(
      setDoc(
        doc(bob, `conversations/${ALICE}_${BOB}`),
        identity(BOB, ALICE),
        { merge: true },
      ),
    )
  })

  /**
   * And the abuse of the same write. A merge that reorders or rewrites `participants` is still an
   * update, and the rule compares the arrays rather than their contents — so Carol cannot merge
   * herself into a thread she is not in, and neither participant can drop the other out of one.
   */
  it('refuses a merged write that rewrites the participants of an existing thread', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `conversations/${ALICE}_${BOB}`), thread())
    })

    await assertFails(
      setDoc(
        doc(alice, `conversations/${ALICE}_${BOB}`),
        { participants: [ALICE, CAROL] },
        { merge: true },
      ),
    )
  })

  it('refuses opening a thread the caller is not in', async () => {
    await assertFails(
      setDoc(doc(carol, `conversations/${ALICE}_${BOB}`), thread()),
    )
  })

  it('refuses a group thread — this app only has two-person chats', async () => {
    await assertFails(
      setDoc(doc(alice, 'conversations/group'), thread({ participants: [ALICE, BOB, CAROL] })),
    )
  })

  /**
   * Phase 13. `size() == 2` alone is satisfied by the same uid twice — a thread with one person in
   * it, which no screen can render (there is no "other" side to name) and nothing can ever arrive
   * in. The client guards it too; this is the half that holds for a client nobody wrote.
   */
  it('refuses a thread whose two participants are the same person', async () => {
    await assertFails(
      setDoc(doc(alice, `conversations/${ALICE}_${ALICE}`), {
        participants: [ALICE, ALICE],
        lastMessage: '',
        lastMessageAt: 1,
        unread: { [ALICE]: 0 },
      }),
    )
  })

  it('is readable only by its two participants', async () => {
    await seed('conversations/t1', thread())
    await assertSucceeds(getDoc(doc(alice, 'conversations/t1')))
    await assertSucceeds(getDoc(doc(bob, 'conversations/t1')))
    await assertFails(getDoc(doc(carol, 'conversations/t1')))
  })

  it('lets a participant update the summary the list draws', async () => {
    await seed('conversations/t2', thread())
    await assertSucceeds(
      updateDoc(doc(alice, 'conversations/t2'), { lastMessage: 'hi', lastSenderId: ALICE }),
    )
  })

  /** Phase 13: the pair is fixed at creation, so nobody can be dropped from or added to a thread. */
  it('refuses rewriting the participant list', async () => {
    await seed('conversations/t3', thread())
    await assertFails(updateDoc(doc(alice, 'conversations/t3'), { participants: [ALICE, CAROL] }))
    await assertFails(updateDoc(doc(alice, 'conversations/t3'), { participants: [ALICE] }))
  })

  it('lets a participant send a message', async () => {
    await seed('conversations/t4', thread())
    await assertSucceeds(
      addDoc(collection(alice, 'conversations/t4/messages'), {
        senderId: ALICE,
        text: 'hello',
        createdAt: 1,
        readBy: [ALICE],
      }),
    )
  })

  /**
   * Phase 13's second closed hole, and the worse of the two. Create only checked that `senderId` was
   * the caller's own uid — so any signed-in user could drop a message into any conversation id, under
   * their real name, in a stranger's thread. They could not read the reply, which made it a one-way
   * channel into somebody else's private chat.
   */
  it('refuses a message from somebody who is not in the thread', async () => {
    await seed('conversations/t5', thread())
    await assertFails(
      addDoc(collection(carol, 'conversations/t5/messages'), {
        senderId: CAROL,
        text: 'I should not be here',
        createdAt: 1,
        readBy: [CAROL],
      }),
    )
  })

  it('refuses a message signed with another participant\'s uid', async () => {
    await seed('conversations/t6', thread())
    await assertFails(
      addDoc(collection(alice, 'conversations/t6/messages'), {
        senderId: BOB,
        text: 'putting words in your mouth',
        createdAt: 1,
        readBy: [BOB],
      }),
    )
  })

  it('never lets a sent message be edited or unsent', async () => {
    await seed('conversations/t7', thread())
    await seed('conversations/t7/messages/m1', { senderId: ALICE, text: 'said', createdAt: 1 })
    await assertFails(updateDoc(doc(alice, 'conversations/t7/messages/m1'), { text: 'unsaid' }))
    await assertFails(deleteDoc(doc(alice, 'conversations/t7/messages/m1')))
  })
})

// ---- the remaining collections -------------------------------------------------------------------

describe('private per-user collections', () => {
  it('keeps a day, its meals, guide progress and emergency contacts to their owner', async () => {
    const paths = [
      `users/${BOB}/days/2026-09-10`,
      `users/${BOB}/days/2026-09-10/meals/m1`,
      `users/${BOB}/guideProgress/cpr`,
      `users/${BOB}/emergencyContacts/c1`,
    ]
    for (const path of paths) {
      await assertSucceeds(setDoc(doc(bob, path), { value: 1 }))
      await assertFails(setDoc(doc(alice, path), { value: 1 }))
      await assertFails(getDoc(doc(alice, path)))
    }
  })

  it('keeps an SOS event to its owner, readable by an admin', async () => {
    await seed(`sosEvents/${BOB}/items/e1`, { lat: 1, lng: 1 })
    await assertSucceeds(getDoc(doc(bob, `sosEvents/${BOB}/items/e1`)))
    await assertSucceeds(getDoc(doc(admin, `sosEvents/${BOB}/items/e1`)))
    await assertFails(getDoc(doc(alice, `sosEvents/${BOB}/items/e1`)))
  })
})

describe('notifications/{uid}/items', () => {
  /** DEVIATION 3: a user may fill their *own* inbox, which is what the SOS confirmation needs. */
  it('lets a user write into their own inbox', async () => {
    await assertSucceeds(
      setDoc(doc(alice, `notifications/${ALICE}/items/n1`), { type: 'SOS', read: false }),
    )
  })

  it('refuses writing into anybody else\'s inbox', async () => {
    await assertFails(
      setDoc(doc(alice, `notifications/${BOB}/items/n1`), { type: 'FAKE', read: false }),
    )
  })

  it('lets a user mark their own notification read, and nothing else about it', async () => {
    await seed(`notifications/${ALICE}/items/n2`, { type: 'SOS', read: false, body: 'saved' })
    await assertSucceeds(updateDoc(doc(alice, `notifications/${ALICE}/items/n2`), { read: true }))
    await assertFails(updateDoc(doc(alice, `notifications/${ALICE}/items/n2`), { body: 'rewritten' }))
  })

  it('never lets a notification be deleted from the phone', async () => {
    await seed(`notifications/${ALICE}/items/n3`, { type: 'SOS', read: false })
    await assertFails(deleteDoc(doc(alice, `notifications/${ALICE}/items/n3`)))
  })
})

describe('hospitals', () => {
  it('is readable by any signed-in client and writable by none', async () => {
    await seed('hospitals/osm-1', { name: 'Memon-2' })
    await assertSucceeds(getDoc(doc(alice, 'hospitals/osm-1')))
    await assertFails(getDoc(doc(anon, 'hospitals/osm-1')))
    await assertFails(updateDoc(doc(alice, 'hospitals/osm-1'), { name: 'not a hospital' }))
    await assertFails(deleteDoc(doc(admin, 'hospitals/osm-1')))
  })
})

describe('pharmacies', () => {
  it('is readable by any signed-in client and writable by none', async () => {
    await seed('pharmacies/osm-1', { name: 'Lazz Pharma' })
    await assertSucceeds(getDoc(doc(alice, 'pharmacies/osm-1')))
    await assertFails(getDoc(doc(anon, 'pharmacies/osm-1')))
    await assertFails(updateDoc(doc(alice, 'pharmacies/osm-1'), { name: 'not a pharmacy' }))
    await assertFails(deleteDoc(doc(admin, 'pharmacies/osm-1')))
  })
})

describe('stories', () => {
  it('lets an author publish and take down their own story', async () => {
    await assertSucceeds(setDoc(doc(alice, 'stories/s1'), { authorId: ALICE, createdAt: 1 }))
    await assertSucceeds(deleteDoc(doc(alice, 'stories/s1')))
  })

  it('refuses a story published under somebody else\'s name', async () => {
    await assertFails(setDoc(doc(alice, 'stories/s2'), { authorId: BOB, createdAt: 1 }))
  })

  /** A story is published once and taken down, never edited. */
  it('refuses editing a story, even the author\'s own', async () => {
    await seed('stories/s3', { authorId: ALICE, createdAt: 1 })
    await assertFails(updateDoc(doc(alice, 'stories/s3'), { text: 'second thoughts' }))
  })

  it('refuses taking down somebody else\'s story', async () => {
    await seed('stories/s4', { authorId: ALICE, createdAt: 1 })
    await assertFails(deleteDoc(doc(bob, 'stories/s4')))
  })
})

describe('an unwritten collection', () => {
  /** Deny-by-default: a path with no `match` block is unreachable, so a new feature starts locked. */
  it('is unreachable even for an admin', async () => {
    await assertFails(getDoc(doc(admin, 'somethingNew/x')))
    await assertFails(setDoc(doc(admin, 'somethingNew/x'), { value: 1 }))
  })
})
