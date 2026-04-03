# Rotiprata API Reference (Backend)

Base URL: `http://localhost:8080/api`

Last audited: 2026-04-02
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
- `PUT /users/me`
- `GET /users/leaderboard`
- `GET /users/me/roles`
- `GET /users/me/preferences`
- `PUT /users/me/preferences`
- `POST /users/me/history`
- `GET /users/me/history`
- `DELETE /users/me/history/{id}`
- `GET /users/me/stats`
- `GET /users/me/badges`
- `GET /users/me/content`
- `GET /users/me/lessons/progress`
- `GET /users/me/hearts`
- `POST /users/me/chat`
- `GET /users/me/chat`
- `DELETE /users/me/chat`

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
- `GET /content/{contentId}/similar`
- `GET /content/{contentId}/media`
- `GET /content/{contentId}/quiz`
- `POST /content/{contentId}/quiz/submit`
- `POST /content/{contentId}/view`
- `POST /content/{contentId}/playback-events`
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
- `GET /admin/users`
- `GET /admin/users/{userId}`
- `PUT /admin/users/{userId}/role`
- `PUT /admin/users/{userId}/status`
- `DELETE /admin/users/{userId}/lessons/{lessonId}/progress`
- `GET /admin/moderation-queue`
- `PUT /admin/content/{contentId}/approve`
- `PUT /admin/content/{contentId}`
- `GET /admin/content/{contentId}/quiz`
- `PUT /admin/content/{contentId}/quiz`
- `PUT /admin/content/{contentId}/reject`
- `GET /admin/flags`
- `GET /admin/flags/{flagId}/reports`
- `PUT /admin/flags/{flagId}/resolve`
- `PUT /admin/flags/{flagId}/take-down`

### Admin Lessons and Quiz Builder (`LessonController`)
- `GET /admin/lessons`
- `GET /admin/lessons/{lessonId}`
- `PUT /admin/lessons/{lessonId}/move-category`
- `PUT /admin/lessons/path-order`
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
  - Feed/content items may include:
    - `stream_url` (currently mirrors `media_url`)
    - `stream_type` (`hls` when URL contains `.m3u8`, else `file`)
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

## Leaderboard Contract

- Endpoint: `GET /users/leaderboard`
- Auth: required (`Authorization: Bearer <accessToken>`)
- Query params:
  - `page` optional, default `1`
  - `pageSize` optional, default `20`, max `50`
  - `query` optional display-name substring filter
- Response:
  - `{ items, page, pageSize, hasNext, totalCount, query, currentUser }`
  - `items` and `currentUser` rows include:
    - `{ rank, userId, displayName, avatarUrl, xp, currentStreak, isCurrentUser }`
- Behavior:
  - Ranks users by `profiles.reputation_points desc`
  - Users with equal XP share the same rank
  - Stable ordering inside ties uses `display_name asc`, then `user_id asc`
  - Admin users are excluded from leaderboard ranking
  - `currentUser` returns the caller's true global rank even when `query` filters the visible table
  - Null XP is treated as `0`

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

## Admin Flag Contract

- Endpoint: `GET /admin/flags`
- Auth: required admin bearer token
- Response:
  - Returns one grouped item per flagged content, not one row per raw `content_flags` record.
  - Shape:
    - `{ id, content_id, status, created_at, report_count, notes_count, reasons, content }`
    - `id` is the most recent pending report id for that content and is used as the action id for existing admin endpoints.
    - `reasons` is the distinct list of report reasons collected for that content.
    - `notes_count` is the number of pending reports that include a non-empty reporter note.
- Behavior:
  - Includes only `status=pending` reports.
  - Groups all pending reports for the same `content_id`.
  - Sorts grouped items by latest report timestamp descending.
- Report detail endpoint:
  - `GET /admin/flags/{flagId}/reports?page=1&query=clipwatcher`
  - Returns `{ items, page, page_size, has_next, query }`
  - `items` contain `{ id, reported_by, reporter, reason, description, created_at }`
  - `reporter` contains `{ user_id, display_name, avatar_url }`
  - Page size is capped to 5 reports per request
  - `query` filters by reporter username/display name
- Action endpoints:
  - `PUT /admin/flags/{flagId}/resolve` resolves all pending reports for that flagged content group.
  - `PUT /admin/flags/{flagId}/take-down` rejects the content and resolves all pending reports for that flagged content group.

## Admin User Management Contract

- List endpoint:
  - `GET /admin/users?query=kai`
  - Auth: required admin bearer token
  - Response rows:
    - `{ userId, displayName, email, avatarUrl, reputationPoints, currentStreak, longestStreak, lastActivityDate, totalHoursLearned, roles, status, createdAt, lastSignInAt }`
  - Notes:
    - `status` is derived from auth suspension state and is currently `active` or `suspended`
    - `query` matches `displayName`, `email`, and `userId`

- Detail endpoint:
  - `GET /admin/users/{userId}`
  - Response:
    - `{ summary, suspendedUntil, activity, postedContent, likedContent, savedContent, comments, lessonProgress, badges, browsingHistory, searchHistory, chatHistory }`
  - `activity` contains:
    - `{ postedContentCount, likedContentCount, savedContentCount, commentCount, enrolledLessonCount, completedLessonCount, badgeCount, browsingCount, searchCount, chatMessageCount }`
  - `comments` contain:
    - `{ id, contentId, contentTitle, body, author, createdAt, updatedAt }`
  - `lessonProgress` contain:
    - `{ id, lessonId, lessonTitle, status, progressPercentage, currentSection, startedAt, completedAt, lastAccessedAt }`
  - `browsingHistory` contains rows from `public.browsing_history`
  - `searchHistory` contains rows from `public.search_history`
  - `chatHistory` contains rows from `public.user_chatbot_history`

- Role update endpoint:
  - `PUT /admin/users/{userId}/role`
  - Request body:
    - `{ "role": "admin" }` or `{ "role": "user" }`
  - Notes:
    - Self-demotion from the last admin account is blocked

- Status update endpoint:
  - `PUT /admin/users/{userId}/status`
  - Request body:
    - `{ "status": "active" }` or `{ "status": "suspended" }`
  - Notes:
    - Suspension is applied through the Supabase admin auth API
    - Self-suspension is blocked

- Lesson reset endpoint:
  - `DELETE /admin/users/{userId}/lessons/{lessonId}/progress`
  - Behavior:
    - Deletes `user_lesson_progress`
    - Deletes `user_lesson_quiz_attempts`
    - Deletes `user_quiz_results` for quizzes attached to the lesson
    - Deletes `user_lesson_rewards`
    - Decrements `profiles.reputation_points` by the lesson reward XP
    - Deletes `user_achievements` only when that badge is no longer backed by any remaining `user_lesson_rewards` row for the same `badge_name`
    - Decrements `lessons.completion_count` when a reward row existed for that lesson reset

## Similar Videos Contract

- Endpoint: `GET /content/{contentId}/similar`
- Auth: required (`Authorization: Bearer <accessToken>`)
- Query params:
  - `limit` (optional): default `6`, max `6`
- Response:
  - `Content[]`
- Behavior:
  - Uses the current content's exact `content_tags` to rank related videos.
  - Excludes the current content from the response.
  - Restricts results to approved, submitted, playable videos.
  - Returns only exact tag matches; if there are none, returns an empty list.
  - Returns at most 6 items.

## Playback Event Contract

- Endpoint: `POST /content/{contentId}/playback-events`
- Auth: required (`Authorization: Bearer <accessToken>`)
- Status: `202 Accepted` (best-effort ingestion)
- Request body fields (all optional):
  - `startupMs`, `stallCount`, `stalledMs`, `watchMs`
  - `playSuccess`, `autoplayBlockedCount`
  - `networkType`, `userAgent`

## Frontend Parity Checklist (`frontend/src/lib/api.ts`)

### Feed / Explore
- `GET /feed?cursor=...&limit=...` -> implemented (cursor-only contract)
- `GET /trending` -> missing
- `GET /search?query=...&filter=...` -> implemented
- `GET /recommendations` -> missing

### User profile / utility
- `GET /users/me` -> implemented
- `PUT /users/me` -> implemented
- `GET /users/leaderboard?page=...&pageSize=...&query=...` -> implemented
- `GET /users/me/roles` -> implemented
- `GET /users/me/preferences` -> implemented
- `PUT /users/me/preferences` -> implemented
- `GET /users/me/history` -> implemented
- `POST /users/me/history` -> implemented
- `DELETE /users/me/history/{id}` -> implemented
- `GET /users/me/stats` -> implemented
- `GET /users/me/lessons/progress` -> implemented
- `GET /users/me/hearts` -> implemented
- `GET /users/me/badges` -> implemented
- `GET /users/me/content?collection=posted|saved|liked` -> implemented
- `POST /users/me/chat` -> implemented
- `GET /users/me/chat` -> implemented
- `DELETE /users/me/chat` -> implemented

### Content
- `POST /content/media/start` -> implemented
- `POST /content/media/start-link` -> implemented
- `GET /content/{id}` -> implemented
- `GET /content/{id}/similar?limit=...` -> implemented
- `GET /content/{id}/media` -> implemented
- `PATCH /content/{id}` -> implemented
- `POST /content/{id}/submit` -> implemented
- `GET /content/{id}/quiz` -> implemented
- `POST /content/{id}/quiz/submit` -> implemented
- `POST /content/{id}/view` -> implemented
- `POST /content/{id}/playback-events` -> implemented
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
- `GET /lessons/hub` -> implemented (category-based response)
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
- `GET /admin/users` -> implemented
- `GET /admin/users/{id}` -> implemented
- `PUT /admin/users/{id}/role` -> implemented
- `PUT /admin/users/{id}/status` -> implemented
- `DELETE /admin/users/{id}/lessons/{lessonId}/progress` -> implemented
- `GET /admin/moderation-queue` -> implemented
- `GET /admin/flags` -> implemented (grouped by `content_id` for admin review)
- `GET /admin/flags/{id}/reports` -> implemented (5-per-page reporter list with username search)
- `PUT /admin/content/{id}/approve` -> implemented
- `PUT /admin/content/{id}` -> implemented
- `PUT /admin/content/{id}/reject` -> implemented
- `PUT /admin/flags/{id}/resolve` -> implemented (resolves all pending reports for the grouped content item)
- `PUT /admin/flags/{id}/take-down` -> implemented
- `GET /admin/lessons` -> implemented
- `GET /admin/lessons/{id}` -> implemented
- `PUT /admin/lessons/{id}/move-category` -> implemented
- `PUT /admin/lessons/path-order` -> implemented
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

## Compatibility Notes

- Feed items now include `is_liked` and `is_saved` flags per user session.
- Feed items and `GET /content/{contentId}` now include creator enrichment when profile exists:
  - `creator: { user_id, display_name, avatar_url }`
  - UI should keep fallback `@anonymous` when creator/display name is missing
- `GET /content/{contentId}/similar` returns a capped related-video list for Learn More and the `/content/:id` related queue flow.
- Comment deletion authorization:
  - `DELETE /content/{contentId}/comments/{commentId}` allows admins to delete any comment.
  - Non-admin users can delete only comments they authored.
- `PUT /users/me`:
  - Authenticated only.
  - User id comes from the JWT subject on the server, not from request body data.
  - Supports `display_name` and `is_gen_alpha`.
- `GET /users/me/badges` returns lesson badges derived from earned lesson rewards plus locked published lesson badges.
- `GET /users/me/content?collection=posted|saved|liked` powers the profile content tabs:
  - `posted` includes the user's own uploads across statuses and content types
  - `saved` and `liked` return approved submitted video content only
- Admin user detail surfaces:
  - saved posts from `content_saves`
  - browsing history from `browsing_history`
  - search history from `search_history`
  - chatbot history from `user_chatbot_history`
- Current schema-driven safeguards are enforced in application logic rather than DB uniqueness:
  - active lesson quiz attempt reuse
  - one lesson reward per user/lesson
  - duplicate pending content-flag prevention per user/content
  - search history update-vs-insert behavior
- `/users/me` may include `timezone` for login streak day-boundary preference.
- `PUT /users/me/preferences` backend DTO uses `themePreference` (camelCase).  
  Frontend currently sends `theme_preference` in `frontend/src/lib/api.ts`.
- Learner quiz endpoints do not expose `correct_answer`; grading is server-side.
- `PUT /lessons/{lessonId}/progress` exists for backward compatibility; section completion + quiz flow is the primary path.
- `GET /lessons/hub` now returns `summary` plus `categories[]`, where each category includes `categoryId`, `name`, `type`, `color`, `isVirtual`, and ordered `lessons[]`.
- Real categories are returned even when they have no lessons; legacy published lessons without `category_id` are grouped into a synthetic `Uncategorized` category.
- Lesson authoring now persists `category_id` on `lessons`; `path_order` is assigned automatically when a lesson is published and then managed from the Manage Lessons path board.
- `PUT /admin/lessons/path-order` accepts `{ categoryId, lessonIds }` and rewrites contiguous `path_order` values inside the selected category bucket.
- `PUT /admin/lessons/{lessonId}/move-category` accepts `{ sourceCategoryId, targetCategoryId, sourceLessonIds, targetLessonIds }`, updates `category_id`, and normalizes path order in both affected categories.
