# Rotiprata

Rotiprata is a full-stack learning platform inspired by TikTok-style feeds to teach Gen Alpha brainrot and cultural trends. This repository contains a Spring Boot backend (Supabase PostgREST-backed) and a React frontend wired to the Java API.

## What''s included
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

## Run with Docker (recommended)
Use Docker Compose to start the full application stack.

Prerequisites:
- Docker
- Docker Compose
- A repo-root `.env` file copied from `.env.template`

Setup:
```bash
cp .env.template .env
```

## Environment variables (backend)
Copy `.env.template` to `.env` and fill in values:

The backend loads `.env` automatically at startup.
Set `FRONTEND_URL` to the public frontend base URL so auth emails can redirect back to your UI.
Optionally set `SUPABASE_REST_URL` to override the default `SUPABASE_URL/rest/v1`.
Set `SUPABASE_SERVICE_ROLE_KEY` for admin lookups used to detect duplicate emails.
**Do not leak or expose this key** (keep it server-side only and never commit it to the repo).
Set `ALLOWED_ORIGINS` only when the frontend is on a different origin; same-origin Docker deployments can leave it unset.

Start the app:
```bash
docker compose build --no-cache && docker compose up
```

Open the app:
- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080`

Useful Docker commands:
```bash
docker compose ps
docker compose logs -f
docker compose logs -f backend
docker compose logs -f frontend
docker compose down
```

## Media tooling (required for video processing)
The backend requires `ffmpeg`, `ffprobe`, and `yt-dlp`.
For Docker on Ubuntu, these are installed in the backend image automatically.
For native-host development, you can optionally override `FFMPEG_PATH`, `FFPROBE_PATH`, and `YTDLP_PATH` in `.env`.
Do not set those path overrides for Ubuntu Docker deployments; the container uses Linux-native binaries from `PATH`.

## Docker deployment notes
`docker-compose.yml` starts both services:
- `backend` on port `8080`
- `frontend` on port `5173`

The frontend is served by nginx and proxies API requests to `/api`, so you should normally open the site through the frontend URL instead of hitting the backend directly in the browser.

The backend service explicitly uses Linux-native media tooling (`ffmpeg`, `ffprobe`, `yt-dlp`) so developer `.env` files with Windows paths do not leak into Ubuntu containers.

## Run without Docker (fallback)
Only use this path if Docker is unavailable on your machine.

These scripts install prerequisites (Java 17, Maven, Node.js), install media tooling, then start the backend and frontend in separate shells.

Windows:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev-start.ps1
```

macOS:
```bash
xattr -dr com.apple.quarantine ./scripts/dev-start.sh 2>/dev/null; bash ./scripts/dev-start.sh
```

Install scripts:
- macOS / Linux: `scripts/install-media-tools.sh`
- Windows: `scripts/install-media-tools.ps1`

```bash
./scripts/install-media-tools.sh
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-media-tools.ps1
```

## Native manual setup (advanced / troubleshooting)
Only use this if Docker is unavailable and the fallback startup scripts do not work.

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

## Recommendation coverage
Run the backend recommendation-only test suite with JaCoCo reporting:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\recommendation-jacoco.ps1
```

Open `target/site/jacoco/index.html` to inspect the HTML report.
The script prints a warning below `50%` recommendation coverage and aims for `70%`, but it does not fail the build on coverage alone.

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
