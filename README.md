# Rotiprata

Rotiprata is a full-stack learning platform inspired by TikTok-style feeds to teach Gen Alpha brainrot and cultural trends. This repository contains a Spring Boot backend (Supabase PostgREST-backed) and a React frontend wired to the Java API.

## What''s included
- JHipster JDL model in `rotiprata.jdl`
- Spring Boot backend with PostgREST-backed data access
- Supabase configuration bindings in `SupabaseProperties`
- React frontend in `frontend/` with API client + mock fallback

## Backend package layout
- `com.rotiprata.api`: REST controllers
- `com.rotiprata.api.dto`: API request/response DTOs
- `com.rotiprata.application`: application services and use cases
- `com.rotiprata.domain`: Domain DTOs and enums
- `com.rotiprata.infrastructure.supabase`: Supabase Auth client + DTOs
- `com.rotiprata.config`: Spring configuration and properties
- `com.rotiprata.security`: JWT helpers and validators

## API docs
See `docs/api.md` for the current backend endpoints and the remaining endpoints expected by the frontend.

## One-step dev start (recommended)
These scripts install prerequisites (Java 17, Maven, Node.js), install media tooling, then start
the backend and frontend in separate shells.

Windows:
```powershell
.\scripts\dev-start.ps1
```

macOS:
```bash
./scripts/dev-start.sh
```

## Manual setup (advanced / troubleshooting)

## Environment variables (backend)
Copy `.env.template` to `.env` and fill in values:

The backend loads `.env` automatically at startup.
Set `FRONTEND_URL` to the public frontend base URL so auth emails can redirect back to your UI.
Optionally set `SUPABASE_REST_URL` to override the default `SUPABASE_URL/rest/v1`.
Set `SUPABASE_SERVICE_ROLE_KEY` for admin lookups used to detect duplicate emails.
**Do not leak or expose this key** (keep it server-side only and never commit it to the repo).

## Media tooling (required for video processing)
The backend requires `ffmpeg`, `ffprobe`, and `yt-dlp`. Startup will fail if they are missing.

Install scripts:
- macOS / Linux: `scripts/install-media-tools.sh`
- Windows: `scripts/install-media-tools.ps1`

```bash
./scripts/install-media-tools.sh
```

```powershell
.\scripts\install-media-tools.ps1
```

## macOS prerequisites (manual)
```bash
brew install maven
```

If your openJDK is not running version 17:
```bash
brew install openjdk@17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
```

## Run the backend (development)
```bash
mvn spring-boot:run
```

The backend runs on `http://localhost:8080` by default.

## Frontend setup
From the repo root(in another teminal):

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:5173` and proxies `/api` to `http://localhost:8080`.

### Frontend environment flags
- `VITE_API_BASE_URL`: Java backend base URL (default `http://localhost:8080/api`)
- `VITE_USE_MOCKS`: `true` | `false` | `auto`
  - `true`: always use dummy data
  - `false`: always call backend
  - `auto`: try backend first, fall back to dummy data
  - For real auth flows, set this to `false`.

## Next steps
- Implement real API controllers/services in the backend for the endpoints declared in `frontend/src/lib/api.ts`.
- Replace dummy data as real endpoints come online.
