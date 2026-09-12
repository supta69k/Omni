---
name: omni-social
description: Omni's social layer — feed, posts, likes, comments, reposts, follow graph, profiles, user search, stories, and direct messaging, with the real Firestore schema and the identity rules that keep accounts from leaking into each other. Use before touching any social or messaging code in this project.
---

# Omni social

Built on [[omni-architecture]]'s repository triplet and `AuthRepository.sessionUid`. Everything below is
read off the actual code — do not invent fields.

## Identity — read this before anything else

- The **viewer** is `sessionUid`. Every read keys on it via `flatMapLatest`; `authState` cannot express a
  session change and must never be a flow key.
- The **subject** is whatever uid the row carries: `post.authorId`, `comment.authorId`, `story.authorId`,
  `conversation.otherUid`. Resolve names and photos from *that* uid, never from the viewer. Rendering
  every comment with the logged-in user's identity is the classic failure here.
- A profile page's uid travels beside the router's `screen` as `profileUid`; `isMe` is
  `profileUid == sessionUid` and is what hides the Message button on your own page.

## Feed

`FeedRepository` (impl `FirestoreFeedRepository`, fake `PreviewFeedRepository`) over `posts/{postId}`.
Two segments in `FeedViewModel`:

- **Discover** — the collection ordered by `createdAt` desc, paged.
- **Following** — a *query*, not a filter: `observeFollowing(sessionUid).map { it - me }` feeds chunked
  `whereIn("authorId", chunk)` (30-value cap). Your own posts are excluded by never being requested.

`combine` retains each source's last value, so a tab switch can deliver the other tab's page — the
`forThisTab` gate in `FeedViewModel` is what prevents that. Keep it.

## Likes, comments, reposts

Subcollections of the post: `likes/{uid}`, `reposts/{uid}`, `comments/{commentId}`. Because they are
subcollections, writing one does **not** re-fire a `posts` listener — hence `toggleLike`'s optimistic
override. A follow, by contrast, writes into the collection `observeFollowing` already watches, so it
needs no override.

Comment document: `authorId`, `text`, `createdAt` (server timestamp). The author's name and photo are
resolved live from `users/{authorId}` by the ViewModel, not stored on the comment.

## Follow graph

Two mirrored subcollections, both written by the follower: `users/{me}/following/{target}` and
`users/{target}/followers/{me}`. `FollowRepository.observeFollowing(uid)` is the single source of truth
for follow state — the pill, the Following tab, the story rail and the message picker all read it.

## Stories

`stories/{id}` with `authorId`, `mediaUrl`, `caption`, `createdAt`, `expiresAt`.
`Story.isLive(now) = expiresAt == 0L || now < expiresAt`; `StoryLifetimeMillis` is 24h.
**Multiple stories per user are supported** — `createStory` is `collection.add(...)`, with no
one-per-user constraint. `StoriesViewModel` groups by `authorId` so a user occupies exactly one rail
tile, orders the rail newest-author-first, and plays oldest-first inside a tile.
Visibility is `following + me`, filtered client-side from one capped listener (a
`whereGreaterThan("expiresAt", now)` would need a composite index *and* re-issuing at every rollover).

**Add Story and View Story are two separate tap targets in the same tile.** Binding both to one
destination is a known regression: once a story existed the tile stopped being an add affordance.

## Messaging

`conversationIdOf(a, b)` = the two uids sorted and joined with `_`, so a pair has exactly one thread.
`openConversation` is a single merge-safe `set(conversationIdentityMap(...), merge = true)` with **no
read first** — a `get` on a document that does not exist makes `resource` null, so
`allow read: if … request.auth.uid in resource.data.participants` errors and *denies*. That read was the
bug that broke the Message button; do not reintroduce it.

`conversationIdentityMap` carries `participants` (sorted), `participantNames` and `participantPhotos`,
and deliberately **none** of the summary fields, so merging onto a live thread cannot blank it.
Messages live in `conversations/{cid}/messages/{mid}` with `senderId`, `text`, `createdAt`, `readBy`;
they are immutable by rule. Thread summaries and unread counts are maintained client-side in the same
batch as the message, because Blaze is unavailable and there is no `onMessageCreated` function.

Resolving "the other participant" must work regardless of array order: pick the entry that is not
`sessionUid`. Never assume `participants[0]`.

## User search

Firestore has no substring operator. The only vocabulary is a prefix scan:
`orderBy("name").startAt(term).endAt(term + '')`, byte-ordered and therefore case-sensitive.
Do not download `users` and filter on the client.

## Performance rules

Comments: the selected post only. Messages: this user's conversations only. Stories: active only.
Following: followed authors only. Live listeners only where real-time actually matters; reuse cached
profile data otherwise.
