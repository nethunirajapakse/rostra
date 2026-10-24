# Rostra frontend

React 19, Vite, TypeScript, Tailwind CSS 4, React Router, TanStack Query and Zustand.

## Run it

```bash
npm install
npm run dev        # http://localhost:5173
```

The backend must be running (gateway on `:8080`, plus auth, auction, bidding and notification services).
To point at another gateway: `GATEWAY_URL=http://host:8080 npm run dev`.

In development the browser only talks to the Vite server, which forwards the API paths (`/auth`, `/auctions`,
`/bids`, `/me`, and the `/ws` WebSocket) to the gateway. Everything is therefore same-origin, so the HttpOnly
auth cookies and the gateway's `XSRF-TOKEN` cookie work without any CORS setup. Page routes were chosen not to
clash with those API paths (`/lot/:id`, `/sell`, `/mine`, `/inbox`, `/signin`, `/signup`).

## How it fits together

| Concern | Where | Notes |
| --- | --- | --- |
| Server state (auctions, bids, notifications) | TanStack Query, `src/api/*` | Cached, polled while an auction is open, refreshed by live pushes |
| Client state | Zustand, `src/store/*` | `session` (who is signed in), `toasts`, `live` (WebSocket status) |
| HTTP | `src/lib/api.ts` | Sends `X-XSRF-TOKEN` on unsafe requests; on a 401 it calls `/auth/refresh` once (shared by concurrent requests) and retries |
| Live updates | `src/lib/live.ts` | One WebSocket for the signed-in user, reconnects with backoff, refreshes the affected queries and shows a toast |
| Routing | `src/App.tsx` | Public pages, plus `RequireAuth` for `/sell`, `/mine`, `/inbox` |

Tokens are never readable by JavaScript (HttpOnly cookies). The only thing stored in `localStorage` is a
"signed in before" hint, so anonymous visitors do not trigger two pointless 401 calls on every page load.

## Scripts

- `npm run dev` start the dev server
- `npm run build` typecheck and build to `dist/`
- `npm run lint` ESLint
