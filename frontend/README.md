# FxPulse frontend

React + TypeScript + Vite UI for the FxPulse backend (charts with Recharts).

## Run locally
```powershell
cd frontend
npm install
copy .env.example .env.local   # optional: defaults to http://localhost:8098
npm run dev                    # http://localhost:5173
```
The backend must be running and allow the origin `http://localhost:5173` (its default CORS setting).

## Scripts
- `npm run dev` – dev server with hot reload
- `npm run build` – type-check (`tsc -b`) and production build into `dist/`
- `npm run lint` – oxlint
- `npm test` – Vitest unit tests (hooks and utils, jsdom)
- `npm run preview` – serve the production build locally

## Structure
- `src/api/types.ts` – TypeScript mirrors of the backend response records
- `src/api/client.ts` – `fetch` wrapper; turns `ApiError` bodies into `ApiRequestError`
- `src/hooks/useApi.ts` – data/error/loading state with request cancellation
