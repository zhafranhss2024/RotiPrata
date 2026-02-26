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
- `GET /auth/login/google`
- `GET /auth/username-available`
- `GET /auth/me`

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
- `GET /feed`
- `GET /search`

### Categories and Tags (`CategoryController`, `TagController`)
- `GET /categories`
- `GET /tags`

### Content (`ContentController`)
- `POST /content/media/start`
- `POST /content/media/start-link`
- `PATCH /content/{contentId}`
- `POST /content/{contentId}/submit`
- `GET /content/{contentId}/media`
- `POST /content/{contentId}/view`
- `POST /content/{contentId}/like`
- `DELETE /content/{contentId}/like`
- `POST /content/{contentId}/vote` (deprecated alias)
- `DELETE /content/{contentId}/vote` (deprecated alias)
- `POST /content/{contentId}/save`
- `DELETE /content/{contentId}/save`
- `POST /content/{contentId}/share`
- `GET /content/{contentId}/comments`
- `POST /content/{contentId}/comments`
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

## Frontend Parity Checklist (`frontend/src/lib/api.ts`)

### Feed / Explore
- `GET /feed?page=...` -> implemented
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
- `GET /content/{id}/media` -> implemented
- `PATCH /content/{id}` -> implemented
- `POST /content/{id}/submit` -> implemented
- `GET /content/{id}/quiz` -> missing
- `POST /content/{id}/view` -> implemented
- `POST /content/{id}/like` -> implemented
- `DELETE /content/{id}/like` -> implemented
- `POST /content/{id}/vote` -> implemented (deprecated alias)
- `DELETE /content/{id}/vote` -> implemented (deprecated alias)
- `POST /content/{id}/save` -> implemented
- `DELETE /content/{id}/save` -> implemented
- `POST /content/{id}/share` -> implemented
- `GET /content/{id}/comments` -> implemented
- `POST /content/{id}/comments` -> implemented
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
- `GET /auth/me` -> implemented

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
- `GET /content/{id}/quiz`
- `GET /users/me/achievements`

## Compatibility Notes

- `PUT /users/me/preferences` backend DTO uses `themePreference` (camelCase).  
  Frontend currently sends `theme_preference` in `frontend/src/lib/api.ts`.
- Learner quiz endpoints do not expose `correct_answer`; grading is server-side.
- `PUT /lessons/{lessonId}/progress` exists for backward compatibility; section completion + quiz flow is the primary path.
