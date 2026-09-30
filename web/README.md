# Ereuna web

The public site (landing, privacy, terms, account deletion) and the signed-in web app
(search, maps, papers, library, settings). Plain ES modules built with Vite; no framework.

## Run locally

```bash
cd backend && python3 -m uvicorn app.main:app --port 8000   # API
cd web && npm install && npm run dev                        # http://localhost:5173
```

In development Vite proxies `/v1` to `http://localhost:8000`.

## Build for deployment

```bash
VITE_API_BASE=https://api.example.com VITE_CONTACT_EMAIL=privacy@example.com npm run build
```

- `VITE_API_BASE` — where the API is served. Leave empty when the site and API share an origin.
- `VITE_CONTACT_EMAIL` — shown on the privacy, terms and account-deletion pages. Set it before launch.

`dist/` is a static site; any static host works (Vercel, Netlify, Cloudflare Pages, or the
API server itself). Routes are hash-based (`/#/privacy`), so no server rewrites are needed.

## Layout

- `src/pages/` — one module per route (`landing`, `auth`, `legal`, `search`, `map`, `paper`, `library`, `settings`).
- `src/components/` — hero radar, demo map, SVG illustrations, citation graph, footer, brand mark.
- `src/compare.js` — comparison-table columns; a port of the Android app's `CompareColumns.kt`, keep them in step.
- `src/api.js` — backend client, session handling (a 401 signs out and returns to sign-in), SSE over fetch.

The illustrations are inline SVG drawn with the theme's CSS variables, so they follow light
and dark mode. Quotes used on the landing page are verbatim from the named papers; keep it that way.
