# Rotiprata API Reference (Backend)

Base URL: `http://localhost:8080/api`

This document lists:
- Endpoints that already exist in the Spring Boot backend.
- Endpoints the frontend expects (from `frontend/src/lib/api.ts`) that are still missing.

## Why PostgREST
- The backend proxies Supabase PostgREST so RLS is enforced.
- Every data call forwards the user JWT + anon key.
- For media processing and moderation, the backend also uses a Supabase service role key.

## Implemented Endpoints

Auth
- `POST /auth/login`  
  Body: `{ email, password }`  
  Returns: tokens + user info.  
  Supabase backing: `POST /auth/v1/token?grant_type=password`
- `POST /auth/register`  
  Body: `{ email, password, displayName, isGenAlpha?, redirectTo? }`  
  Returns: tokens or email confirmation required flag.  
  Supabase backing: `POST /auth/v1/signup`
- `POST /auth/forgot-password`  
  Body: `{ email, redirectTo? }`  
  Sends password recovery email.  
  Note: `redirectTo` must be allowlisted in Supabase Auth settings.  
  Supabase backing: `POST /auth/v1/recover`
- `POST /auth/reset-password`  
  Body: `{ accessToken, password }`  
  Resets password using Supabase access token.  
  Supabase backing: `PUT /auth/v1/user`
- `POST /auth/logout`  
  Header: `Authorization: Bearer <token>`  
  Logs out from Supabase.  
  Supabase backing: `POST /auth/v1/logout`
- `GET /auth/login/google`  
  Query: `redirectTo?`  
  Returns 302 redirect to Supabase OAuth.  
  Supabase backing: `GET /auth/v1/authorize`
- `GET /auth/username-available`  
  Query: `displayName` (or `username` for backward-compat)  
  Returns `{ available, normalized }` based on `display_name_registry` view.
- `GET /auth/me`  
  Header: `Authorization: Bearer <token>`  
  Returns or creates the profile mapped to the JWT.  
  Supabase backing: `GET /rest/v1/profiles?user_id=eq.<jwt.sub>`

Users
- `GET /users/me`  
  Header: `Authorization: Bearer <token>`  
  Returns the current user profile.  
  Supabase backing: `GET /rest/v1/profiles?user_id=eq.<jwt.sub>`
- `GET /users/me/roles`  
  Header: `Authorization: Bearer <token>`  
  Returns a list of role strings.  
  Supabase backing: `GET /rest/v1/user_roles?user_id=eq.<jwt.sub>&select=role`
- `GET /users/me/preferences`  
  Header: `Authorization: Bearer <token>`  
  Returns theme preference (`light|dark|system`).  
  Supabase backing: `GET /rest/v1/profiles?user_id=eq.<jwt.sub>&select=theme_preference`
- `PUT /users/me/preferences`  
  Header: `Authorization: Bearer <token>`  
  Body: `{ themePreference }`  
  Updates theme preference.  
  Supabase backing: `PATCH /rest/v1/profiles?user_id=eq.<jwt.sub>`
- `POST /users/me/history`  
  Header: `Authorization: Bearer <token>`  
  Body: `{ contentId?, lessonId? }`  
  Saves a browsing history entry.

Content (draft + async media)
- `POST /content/media/start`  
  Header: `Authorization: Bearer <token>`  
  Multipart: `file`  
  Starts async upload + conversion. Returns `{ contentId, status, pollUrl }`.  
- `POST /content/media/start-link`  
  Header: `Authorization: Bearer <token>`  
  Body: `{ sourceUrl }`  
  Starts async link ingest + conversion (TikTok/Instagram Reels only).  
- `GET /content/{id}/media`  
  Header: `Authorization: Bearer <token>`  
  Returns `{ status, hlsUrl, thumbnailUrl, errorMessage }`.  
- `PATCH /content/{id}`  
  Header: `Authorization: Bearer <token>`  
  Body: Draft fields to update while media is processing (supports tags).  
- `POST /content/{id}/submit`  
  Header: `Authorization: Bearer <token>`  
  Body: `{ title, description, contentType, categoryId?, learningObjective?, originExplanation?, definitionLiteral?, definitionUsed?, olderVersionReference?, tags? }`  
  Finalizes draft if media is ready. Inserts into moderation queue.  

Search
- `GET /search`  
  Query: `query`, `filter` (optional)  
  Filters: `video`, `lesson`  
  Returns approved video content and published lessons.

Tags
- `GET /tags`  
  Query: `query`  
  Returns matching tag strings.

Categories
- `GET /categories`  
  Returns category list.

## Missing Endpoints (Required by Frontend)

Feed and content
- `GET /feed?page=...`
- `GET /trending`
- `GET /recommendations`
- `GET /content/{id}/quiz`
- `POST /content/{id}/view`
- `POST /content/{id}/vote`
- `POST /content/{id}/save`
- `POST /content/{id}/flag`

Lessons
- `GET /lessons`
- `GET /lessons/search?q=...`
- `GET /lessons/{id}`
- `GET /lessons/{id}/sections`
- `POST /lessons/{id}/enroll`
- `POST /lessons/{id}/save`
- `PUT /lessons/{id}/progress`

User data
- `GET /users/me/history`
- `DELETE /users/me/history`
- `GET /users/me/lessons/progress`
- `GET /users/me/stats`
- `GET /users/me/achievements`

Admin
- `GET /admin/stats`
- `GET /admin/moderation-queue`
- `GET /admin/flags`
- `PUT /admin/content/{id}/approve`
- `PUT /admin/content/{id}/reject`
- `PUT /admin/flags/{id}/resolve`
- `POST /admin/lessons`
- `POST /admin/lessons/{id}/quiz`

## Notes for Developers
- All authenticated endpoints require `Authorization: Bearer <accessToken>`.
- Frontend mocks can mask missing endpoints. To force real backend calls: set `VITE_USE_MOCKS=false`.

