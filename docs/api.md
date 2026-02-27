# Rotiprata API Reference (Backend)

Base URL: `http://localhost:8080/api`

Last audited: 2026-02-26  
Audit source: controller mappings in `src/main/java/com/rotiprata/api/*Controller.java` and frontend calls in `frontend/src/lib/api.ts`.

## Security and Error Contract

- Public endpoints (no JWT required):
  - `GET /auth/login/google`
  - `GET /auth/username-available`
  - `GET /categories`
  - `POST /auth/login`
  - `POST /auth/register`
  - `POST /auth/forgot-password`
  - `POST /auth/reset-password`
- All other `/api/**` endpoints require `Authorization: Bearer <accessToken>`.
- Standard API error envelope:
  - `{ code, message, fieldErrors?, retryAfterSeconds? }`
- Validation errors return `400` with `code=validation_error` and `fieldErrors`.

## Implemented Endpoints (Controller-Verified)

### Auth (`AuthController`)
- `POST /auth/login`
- `POST /auth/register`
- `POST /auth/forgot-password`
- `POST /auth/reset-password`
- `POST /auth/logout`
- `POST /auth/streak/touch`
- `GET /auth/login/google`
- `GET /auth/username-available`

### Users (`UserController`)
- `GET /users/me`
- `GET /users/me/roles`
- `GET /users/me/preferences`
- `PUT /users/me/preferences`
- `POST /users/me/history`
- `GET /users/me/history`
- `DELETE /users/me/history`
- `GET /users/me/stats`
- `GET /users/me/lessons/progress`
- `GET /users/me/hearts`

### Feed and Search (`FeedController`, `BrowsingController`)
- `GET /feed` (cursor-based pagination: `cursor`, `limit`)
- `GET /search`

### Categories and Tags (`CategoryController`, `TagController`)
- `GET /categories`
- `GET /tags`

### Content (`ContentController`)
- `POST /content/media/start`
- `POST /content/media/start-link`
- `PATCH /content/{contentId}`
- `POST /content/{contentId}/submit`
- `GET /content/{contentId}`
- `GET /content/{contentId}/media`
- `GET /content/{contentId}/quiz`
- `POST /content/{contentId}/quiz/submit`
- `POST /content/{contentId}/view`
- `POST /content/{contentId}/like`
- `DELETE /content/{contentId}/like`
- `POST /content/{contentId}/save`
- `DELETE /content/{contentId}/save`
- `POST /content/{contentId}/share`
- `GET /content/{contentId}/comments`
- `POST /content/{contentId}/comments`
- `DELETE /content/{contentId}/comments/{commentId}`
- `POST /content/{contentId}/flag`

### Lessons Learner Flow (`LessonController`)
- `GET /lessons`
- `GET /lessons/feed`
- `GET /lessons/hub`
- `GET /lessons/search`
- `GET /lessons/{lessonId}`
- `GET /lessons/{lessonId}/sections`
- `GET /lessons/{lessonId}/progress`
- `GET /lessons/{lessonId}/quiz/state`
- `POST /lessons/{lessonId}/quiz/answer`
- `POST /lessons/{lessonId}/quiz/restart`
- `POST /lessons/{lessonId}/sections/{sectionId}/complete`
- `POST /lessons/{lessonId}/enroll`
- `POST /lessons/{lessonId}/save`
- `PUT /lessons/{lessonId}/progress` (legacy compatibility endpoint)

### Admin Moderation and Content (`AdminController`)
- `GET /admin/stats`
- `GET /admin/moderation-queue`
- `PUT /admin/content/{contentId}/approve`
- `PUT /admin/content/{contentId}`
- `GET /admin/content/{contentId}/quiz`
- `PUT /admin/content/{contentId}/quiz`
- `PUT /admin/content/{contentId}/reject`
- `GET /admin/flags`
- `PUT /admin/flags/{flagId}/resolve`

### Admin Lessons and Quiz Builder (`LessonController`)
- `GET /admin/lessons`
- `GET /admin/lessons/{lessonId}`
- `POST /admin/lessons/draft`
- `PUT /admin/lessons/{lessonId}/draft/step/{stepKey}`
- `POST /admin/lessons/{lessonId}/publish`
- `POST /admin/lessons`
- `PUT /admin/lessons/{lessonId}`
- `DELETE /admin/lessons/{lessonId}`
- `GET /admin/lessons/{lessonId}/quiz`
- `GET /admin/quiz/question-types`
- `POST /admin/lessons/{lessonId}/quiz`
- `PUT /admin/lessons/{lessonId}/quiz`

## Feed Contract (Cursor-Based)

- Endpoint: `GET /feed`
- Query params:
  - `cursor` (optional): opaque base64url token from previous response `nextCursor`
  - `limit` (optional): default `20`, max `50`
- Response:
  - `{ items: Content[], hasMore: boolean, nextCursor: string | null }`
  - Each `Content` item may include `hls_url` (string | null) for HLS manifests.
- Ordering:
  - Stable descending sort on `(created_at desc, id desc)`
- Pagination behavior:
  - First request uses no cursor
  - Next request uses the returned `nextCursor`
  - `page` parameter is no longer supported
- Error behavior:
  - Invalid cursor returns `400` with API error envelope (`code=validation_error`)

## Login Streak Contract

- Endpoint: `POST /auth/streak/touch`
- Auth: required (`Authorization: Bearer <accessToken>`)
- Request body (optional):
  - `{ "timezone": "Asia/Singapore" }`
- Response:
  - `{ currentStreak, longestStreak, lastActivityDate, touchedToday }`
- Behavior:
  - Updates login streak once per day per user.
  - Day boundary uses valid request timezone first, then stored profile timezone, then UTC fallback.
  - This endpoint is idempotent for same-day repeated calls (`touchedToday=true`).

## Comment Delete Contract

- Endpoint: `DELETE /content/{contentId}/comments/{commentId}`
- Auth: required (`Authorization: Bearer <accessToken>`)
- Authorization:
  - Admin can delete any comment.
  - Non-admin user can delete only their own comment.
- Behavior:
  - Comment is soft-deleted (`is_deleted=true`) and content `comments_count` is refreshed.
- Common responses:
  - `204` success
  - `403` trying to delete another user's comment without admin role
  - `404` comment/content not found

## Frontend Parity Checklist (`frontend/src/lib/api.ts`)

### Feed / Explore
- `GET /feed?cursor=...&limit=...` -> implemented (cursor-only contract)
- `GET /trending` -> missing
- `GET /search?query=...&filter=...` -> implemented
- `GET /recommendations` -> missing

### User profile / utility
- `GET /users/me` -> implemented
- `GET /users/me/roles` -> implemented
- `GET /users/me/preferences` -> implemented
- `PUT /users/me/preferences` -> implemented
- `GET /users/me/history` -> implemented
- `POST /users/me/history` -> implemented
- `DELETE /users/me/history` -> implemented
- `GET /users/me/stats` -> implemented
- `GET /users/me/lessons/progress` -> implemented
- `GET /users/me/hearts` -> implemented
- `GET /users/me/achievements` -> missing

### Content
- `POST /content/media/start` -> implemented
- `POST /content/media/start-link` -> implemented
- `GET /content/{id}` -> implemented
- `GET /content/{id}/media` -> implemented
- `PATCH /content/{id}` -> implemented
- `POST /content/{id}/submit` -> implemented
- `GET /content/{id}/quiz` -> implemented
- `POST /content/{id}/quiz/submit` -> implemented
- `POST /content/{id}/view` -> implemented
- `POST /content/{id}/like` -> implemented
- `DELETE /content/{id}/like` -> implemented
- `POST /content/{id}/save` -> implemented
- `DELETE /content/{id}/save` -> implemented
- `POST /content/{id}/share` -> implemented
- `GET /content/{id}/comments` -> implemented
- `POST /content/{id}/comments` -> implemented
- `DELETE /content/{id}/comments/{commentId}` -> implemented
- `POST /content/{id}/flag` -> implemented

### Lessons learner flow
- `GET /lessons` -> implemented
- `GET /lessons/feed` -> implemented
- `GET /lessons/hub` -> implemented
- `GET /lessons/search` -> implemented
- `GET /lessons/{id}` -> implemented
- `GET /lessons/{id}/sections` -> implemented
- `GET /lessons/{id}/progress` -> implemented
- `POST /lessons/{id}/sections/{sectionId}/complete` -> implemented
- `GET /lessons/{id}/quiz/state` -> implemented
- `POST /lessons/{id}/quiz/answer` -> implemented
- `POST /lessons/{id}/quiz/restart` -> implemented
- `POST /lessons/{id}/enroll` -> implemented
- `POST /lessons/{id}/save` -> implemented
- `PUT /lessons/{id}/progress` -> implemented (legacy)

### Auth
- `POST /auth/login` -> implemented
- `POST /auth/register` -> implemented
- `POST /auth/logout` -> implemented
- `POST /auth/forgot-password` -> implemented
- `POST /auth/reset-password` -> implemented
- `GET /auth/login/google` -> implemented
- `GET /auth/username-available` -> implemented

### Admin
- `GET /admin/stats` -> implemented
- `GET /admin/moderation-queue` -> implemented
- `GET /admin/flags` -> implemented
- `PUT /admin/content/{id}/approve` -> implemented
- `PUT /admin/content/{id}` -> implemented
- `PUT /admin/content/{id}/reject` -> implemented
- `PUT /admin/flags/{id}/resolve` -> implemented
- `GET /admin/lessons` -> implemented
- `GET /admin/lessons/{id}` -> implemented
- `POST /admin/lessons/draft` -> implemented
- `PUT /admin/lessons/{id}/draft/step/{step}` -> implemented
- `POST /admin/lessons/{id}/publish` -> implemented
- `POST /admin/lessons` -> implemented
- `PUT /admin/lessons/{id}` -> implemented
- `DELETE /admin/lessons/{id}` -> implemented
- `GET /admin/lessons/{id}/quiz` -> implemented
- `GET /admin/quiz/question-types` -> implemented
- `POST /admin/lessons/{id}/quiz` -> implemented
- `PUT /admin/lessons/{id}/quiz` -> implemented

## Missing Endpoints (Required for Full Frontend Parity)

- `GET /trending`
- `GET /recommendations`
- `GET /users/me/achievements`

## Compatibility Notes

- Feed items now include `is_liked` and `is_saved` flags per user session.
- Feed items and `GET /content/{contentId}` now include creator enrichment when profile exists:
  - `creator: { user_id, display_name, avatar_url }`
  - UI should keep fallback `@anonymous` when creator/display name is missing
- Comment deletion authorization:
  - `DELETE /content/{contentId}/comments/{commentId}` allows admins to delete any comment.
  - Non-admin users can delete only comments they authored.
- `/users/me` may include `timezone` for login streak day-boundary preference.
- `PUT /users/me/preferences` backend DTO uses `themePreference` (camelCase).  
  Frontend currently sends `theme_preference` in `frontend/src/lib/api.ts`.
- Learner quiz endpoints do not expose `correct_answer`; grading is server-side.
- `PUT /lessons/{lessonId}/progress` exists for backward compatibility; section completion + quiz flow is the primary path.
