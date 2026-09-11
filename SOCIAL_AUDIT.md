# Omni — social system audit (Phase 1)

Read before the repair, written from the actual source rather than from the bug reports. Where a
report's premise turned out to be wrong, that is said plainly here instead of being "fixed" by
rewriting code that already works.

---

## 1. The map (the 18 items asked for)

| # | Thing | Where it lives | State |
|---|-------|----------------|-------|
| 1 | Feed screen | `ui/feed/FeedScreen.kt` | works |
| 2 | Post model | `data/model/Post.kt` | works; no `repostCount`/`repostedByMe` |
| 3 | Post repository | `data/repo/FeedRepository.kt` + `FirestoreFeedRepository.kt` + `PreviewFeedRepository.kt` | works |
| 4 | Story model | `data/model/Story.kt` | **already a separate entity** |
| 5 | Story repository | `data/repo/StoryRepository.kt` | **already separate**; never touches `posts` |
| 6 | Comment model / repo | `Post.kt` (`Comment`) + `FirestoreFeedRepository.observeComments/addComment` | works |
| 7 | Like / reaction | `posts/{id}/likes/{uid}` — one doc per liker | works |
| 8 | Repost / share | `FeedViewModel.repost` (quoting post) + Android share sheet | real, but **uncounted and duplicable** |
| 9 | Follow / follower | `data/repo/FollowRepository.kt` | **already exists**, idempotent, both halves batched |
| 10 | User profile model / repo | `data/model/User.kt`, `data/repo/FirestoreUserRepository.kt` | works |
| 11 | Public profile screen | `ui/profile/ProfileScreen.kt` | exists, **with Follow and Message already on it** |
| 12 | My own profile | same screen, `state.isMe` hides the actions | correct in principle, wrong while loading |
| 13 | Message / conversation repo | `data/repo/MessageRepository.kt` | works; deterministic thread id |
| 14 | Profile navigation | `MainActivity.profileUid` → `ProfileViewModel.setProfileUid` | correct route, **stale state** |
| 15 | Cloudinary media repo | `data/repo/MediaRepository.kt` | works, unsigned, one pipeline |
| 16 | Security rules | `firestore.rules` (202 lines, deny-by-default) | sound; two small gaps |
| 17 | Indexes | `firestore.indexes.json` | both composites present |
| 18 | Social ViewModels | `FeedViewModel`, `StoriesViewModel`, `ProfileViewModel`, `MessagesViewModel`, `SessionViewModel` | see below |

### Collections actually used

```
users/{uid}                          name, email, photoUrl, role, verified, profession, goals, prefs
users/{uid}/following/{targetUid}    { createdAt }            — doc id IS the target
users/{uid}/followers/{followerUid}  { createdAt }            — doc id IS the follower
posts/{postId}                       authorId, authorName, authorPhotoUrl, authorVerified,
                                     authorProfession, body, imageUrl, createdAt,
                                     likeCount, commentCount, repostCount  (counters always 0)
posts/{postId}/likes/{uid}           { createdAt }            — doc id IS the liker
posts/{postId}/comments/{auto}       authorId, authorName, body, createdAt
stories/{auto}                       authorId, authorName, authorPhotoUrl, authorVerified,
                                     imageUrl, caption, createdAt, expiresAt
conversations/{a_b}                  participants[2] (sorted), participantNames, lastMessage,
                                     lastMessageAt, unread{}
conversations/{cid}/messages/{auto}  senderId, text, createdAt
```

The owner field is `authorId` on posts / comments / stories, `participants` on conversations,
`senderId` on messages, and the **document id itself** on likes, follows and followers. The uid
travels between screens in `MainActivity`'s `profileUid` state, handed to `ProfileViewModel` by
`setProfileUid` in a `LaunchedEffect`.

Stories are distinguished from posts by living in a different collection with a different model,
repository, rules block and ViewModel. **There is no shared write path.** Counters are never written
by the client (rules DEVIATION 2), so every count is a server-side `count()` aggregation at read
time.

---

## 2. What the reports got right, and why

### Bug A — "uploading a Story creates a normal Feed post"

**Not a data-layer bug. A navigation-layer bug, and the story pipeline has no entry point at all.**

`FeedScreen.kt:405` builds the first rail tile:

```kotlin
ShareMealTile(
    hasStory = myTile != null,
    onClick = { if (myTile != null) onOpenViewer(myTileIndex) else onCompose() },
)
```

`MainActivity.kt:446` binds `onCompose = { screen = AppScreen.ComposePost }`. So "Add to your
story" opens the **post composer**, which calls `feed.createPost(...)`. That is the whole bug.

`CreateStorySheet` *is* wired at `MainActivity.kt:459`, but it is unreachable: the only statement
that sets `storySheetOpen = true` is inside the picker callback at `:413`, and the only caller of
`pickStoryImage.launch(...)` is the sheet's own `onPickImage` at `:464`. The sheet can only be
opened by a sheet that is already open. `StoriesViewModel.createStory` and
`FirestoreStoryRepository.createStory` are correct and were never being called.

### Bug B — "no way to write a comment"

`CommentsSheet.kt:206`:

```kotlin
decorationBox = { inner ->
    if (draft.isEmpty()) { Text("Add a comment…", …) }
    Box(Modifier.width(0.dp)) { inner() }      // ← the field is zero-width
},
```

The placeholder draws, so the field *looks* present; the actual input has no width and therefore no
hit area and no caret. It violates this codebase's own `decorationBox` rule (placeholder and
`inner()` in **one** `Box(contentAlignment = CenterStart)`), which every other field here follows.
The send button, the 280-char cap, `imePadding`, the list and the empty state were all already
there.

### Bug C — "the profile from a post is wrong / shows my own data"

The route is right: `onOpenProfile(post.authorId)` → `profileUid = authorId` → `setProfileUid`.
Nothing anywhere substitutes `currentUid` for a target uid.

The defect is **state lifetime**. `ProfileViewModel` is Activity-scoped and `uiState` is a five-way
`combine` held in a `StateFlow`. Changing `profileUid` re-keys four `flatMapLatest` sources, but
until *all* of them emit again the last combined value stands — so the page renders the **previously
viewed** profile's user, posts, follower count and follow state. If the previous profile was mine
(reachable by tapping my own avatar on my own post), the next person's page shows my name, my photo
and my posts.

Worse, `isMe = user?.uid == me`. While `user` is null — the entire load window — that is `false`,
so **my own profile briefly offers to follow and message myself**, and a slow read leaves it there.

### Bug D — "there is no follow option"

`ProfileScreen.kt:187-230` already draws Follow and Message. Two things hide or break them:

1. the stale-state window above can leave `isMe = true` from a previous visit, hiding both buttons;
2. a refused follow write was silently swallowed until this session's `followError` fix, so a
   button that existed did nothing and said nothing.

`FollowRepository` itself is correct and already satisfies Phase 8's "deterministic relationship
IDs, no duplicates": a follow *is* the document `users/{me}/following/{them}`, mirrored to
`users/{them}/followers/{me}` in one `WriteBatch`. **No new collection is needed.**

### Bug E — "likes don't work consistently"

Likes are correct (one document per liker, optimistic override retained deliberately because a
subcollection write does not re-fire the `posts` listener). What is *not* correct is the repost
pill beside it: `FeedScreen.kt:770` hardcodes `text = "0"`, and `FeedViewModel.repost` has no
duplicate guard — tapping it five times writes five quoting posts. From the outside, one dead
counter next to two live ones reads as "the buttons don't work".

### The cross-account race the report describes

Three concrete instances:

* `MainActivity.kt:426` and `:439` read `container.authRepository.currentUid` **during
  composition**. That is not snapshot state, so signing out and back in without recreating the
  Activity leaves the feed and the story rail keyed to the *previous* account's uid until something
  else happens to recompose that subtree. B then sees "Your story" on A's tile.
* `StoriesViewModel.kt:88` and `:101` read `currentUid` imperatively **inside the combine lambda**
  instead of from the auth flow.
* `FeedViewModel.kt:183` does the same inside the Following filter.

`SessionViewModel` is not implicated — every read there is `flatMapLatest` over the auth state and
drops cleanly on sign-out.

---

## 3. What the reports got wrong

* **Stories are already a separate entity.** Model, repository, rules block, expiry, author
  grouping (`groupBy { it.authorId }`), viewer paging and "Your story" substitution all exist and
  are correct. Only the button that reaches them was wired to the wrong destination.
* **A follow system already exists** and is race-free by construction. Building a second one would
  be the duplicate the brief forbids.
* **`observeByAuthor` already queries `whereEqualTo("authorId", …)`** server-side with the composite
  index deployed, so Phase 10's "never query currentUser.uid / don't filter in Compose" is already
  satisfied. Only the *count* is wrong (it is `posts.size`, capped at the 20 the page loads).
* **Cloudinary is already one pipeline with two entry points** (`upload(uid, image, folder)`), so
  Phase 13's "upload must not decide post vs story" already holds.
* **No fake/mock social data and no UI-as-backend state** was found anywhere in the social surface.

---

## 4. Gaps in the rules (both are tightenings)

1. `conversations` create requires `participants.size() == 2` but does not require the two to be
   **distinct**, so a self-thread `[A, A]` would pass. Phase 9 forbids it.
2. There is no `posts/{id}/reposts/{uid}` block, which the repost fix needs.

Everything else in `firestore.rules` is already ownership-validated and covered by the 61 emulator
tests in `tools/rules.test.mjs`. Nothing needs weakening.

---

## 5. Performance findings (Phase 15)

`FirestoreFeedRepository.joinCounts` re-reads **four things per post on every snapshot** — my like
document plus two `count()` aggregations, and aggregations cannot be served from cache, so each is a
network round trip. A 20-post page is 60 round trips, re-run whenever any post changes. That is the
mechanical explanation for "the app feels very heavy". The fix is to join incrementally: keep the
per-post counts and only re-read the documents the snapshot actually reports as changed.

The feed also has no author-profile cache: it renders the identity denormalised onto each post at
write time, so a renamed or newly-photographed author stays stale forever on old posts.

---

## 6. Repair order

1. Route the story tile to the story pipeline; make `CreateStorySheet` reachable.
2. Fix the comment field's zero width; surface send failures.
3. Resolve post and comment authors from `authorId` through a session author cache.
4. Make reposts counted, marked and non-duplicable.
5. Rebuild `ProfileUiState` so stale content is structurally impossible.
6. Guard self-conversations at the ViewModel, the repository and the rules.
7. Read the signed-in uid from a flow everywhere, never during composition.
8. Incremental count join; blank-URL hardening.
9. Extend the rules tests; re-run them and the JVM tests; compile.

All nine are done. Two things found along the way are recorded below, because neither was in the
audit above and one of them is the deepest cause in the file.

---

## 7. The session key (found by a test, after the audit)

Item 7 above was written as "read the signed-in uid from a flow, never during composition", and
that is what was first built: `SessionViewModel.signedInUid`, plus the same three-line derivation
each ViewModel already had —

```kotlin
private val uid: Flow<String?> = authRepository.authState
    .map { if (it == AuthState.AUTHENTICATED) authRepository.currentUid else null }
    .distinctUntilChanged()
```

The `ProfileViewModel` test written to pin the wrong-profile bug then failed on the cross-account
scenario, and the reason was this derivation itself. It has two holes, and both open only when one
account replaces another:

* **`authState` is an enum.** Sign out of A and into B and it reads `AUTHENTICATED` at both ends.
  A `distinctUntilChanged` downstream can erase the whole transition, so the `map` never re-runs
  and every read below stays keyed to A's uid while B is looking at the screen.
* **`currentUid` inside that `map` is an imperative read, not a dependency.** Even when the state
  does change, the lambda samples whatever Firebase holds at that instant — which during a
  sign-out/sign-in pair is a coin toss.

This is the general form of the exact race the brief drew. It was present in **all thirteen**
ViewModels, not only the social ones, because they all copied the same block.

The fix is one property on the repository: `AuthRepository.sessionUid: StateFlow<String?>`, kept by
`FirebaseAuthRepository` from the same `AuthStateListener` that already maintained `authState`, and
written *before* it so a collector woken by the state change cannot read a session that has already
been replaced. Every ViewModel's three lines collapse to:

```kotlin
private val uid: StateFlow<String?> = authRepository.sessionUid
```

Keyed on the uid itself, a new account is a new value: every flow built on it restarts, and there
is no instant at which one session's answer can land in another's UI. `currentUid` stays, and stays
correct, for *actions* — a tap should read the session as it is when the finger lands.

The test that found it distinguishes the two halves of the page, which is worth stating because it
is not obvious: on a session change the *viewer-scoped* state (do **I** follow this person) must
reset, while the profile's own name and posts should not. Ben is the same person whoever is
looking, and dropping his document would flicker the page for nothing.

---

## 8. Known trade-off: story expiry is filtered on the client

`FirestoreStoryRepository.observeStories` orders by `createdAt` and drops expired stories in
Kotlin, rather than asking Firestore for `whereGreaterThan("expiresAt", now)`.

This is deliberate, and it is the one place in the social surface where the server is not the
filter. The server-side version needs a composite index on (`expiresAt`, `createdAt`) — a new index
deploy — *and* the listener would have to be re-issued as `now` advances, because a range bound
baked into a query does not move while the query is open. A strip capped at a couple of dozen
documents costs nothing to filter locally, and an expired story is never rendered either way.

What this does **not** do is delete anything: an expired document stays in `stories` until its
author's next open removes it (`deleteMine`). Readers never delete — the rules forbid it. A
Firestore TTL policy on `expiresAt` is the right long-term answer and is a console setting, not
code. Until then the collection grows slowly with dead documents that nobody can see.

Revisit if the strip ever needs paging. At that point the index is worth its deploy.
