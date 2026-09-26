# Routes

### android

- `ACTIVITY` `/MainActivity` `[inferred]`

### express

- `POST` `/otp/send` [auth] `[inferred]`
- `POST` `/otp/verify` [auth, db] `[inferred]`
- `GET` `/health` `[inferred]`
- `POST` `/ai/meal/analyze` [auth] `[inferred]`
- `POST` `/verification/approve` [auth] `[inferred]`
- `POST` `/verification/reject` [auth] `[inferred]`
- `POST` `/posts/:postId/like` params(postId) [auth] `[inferred]`
- `POST` `/posts/:postId/comments` params(postId) [auth] `[inferred]`
- `POST` `/conversations/:conversationId/messages` params(conversationId) [auth] `[inferred]`
- `POST` `/admin/grant` [auth] `[inferred]`
