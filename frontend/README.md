# Rotiprata Frontend

React + Vite UI for the Rotiprata platform. This frontend is wired to the Java backend API and can fall back to dummy data when the backend is not running.

## Prerequisites
- Node.js 18+
- npm

## Configure env
Copy `.env.example` to `.env` and adjust as needed:

```bash
cp .env.example .env
```

Key settings:
- `VITE_API_BASE_URL`: Java backend base URL (default `http://localhost:8080/api`)
- `VITE_USE_MOCKS`: `true` | `false` | `auto`
  - `true`  : always use dummy data (no backend required)
  - `false` : always call backend
  - `auto`  : try backend first, fall back to dummy data if it fails

## Run in development
```bash
npm install
npm run dev
```

The Vite dev server runs on `http://localhost:5173` and proxies `/api` to the backend at `http://localhost:8080`.

## Notes
- API calls are implemented in `src/lib/api.ts`.
- Dummy data lives in `src/mocks/` and is labeled clearly.
