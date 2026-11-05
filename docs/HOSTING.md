# Hosting options and what they cost

**Current setup (November 2026):** storefront on **Cloudflare Pages** (https://rekha-shopping-mall.pages.dev), API on
**Render** free (moving to Oracle Cloud Always Free: `docs/DEPLOY_ORACLE.md`), MySQL on **Aiven** free, photos and
videos on **Cloudflare R2**. The Vercel and Railway notes below are the options that were considered, not what runs.

## Photos and videos on Cloudflare R2 (one-time setup)

Uploads go to R2 and load from `https://rekha-shopping-mall.pages.dev/media/{id}/{checksum}`, served by the Pages
Function in `ecommerce-frontend/functions/media/`, so they never pass through the API server. Not using `r2.dev`:
that address is rate-limited and meant only for testing.

1. Cloudflare > **R2** > Create bucket `rekha-media` (location: Asia-Pacific). Leave public access **off**.
2. Pages project `rekha-shopping-mall` > Settings > **Bindings** > Add > R2 bucket: variable name `MEDIA`, bucket
   `rekha-media` (Production). Settings > Variables: add `VITE_MEDIA_URL=https://rekha-shopping-mall.pages.dev`.
   Retry the latest deployment so both take effect.
3. R2 > **Manage API tokens** > Create: *Object Read & Write*, only bucket `rekha-media`. Note the Access Key ID,
   Secret Access Key and the Account ID.
4. Render API > Environment: `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET=rekha-media`,
   `R2_PUBLIC_URL=https://rekha-shopping-mall.pages.dev`. On restart the API moves existing database files to R2 by
   itself (log: "Moving N uploaded file(s) from the database to R2"); old `/api/media/...` links redirect.

Check: `curl -sI https://rekha-shopping-mall-api.onrender.com/api/media/<id>/<checksum>` answers `302` to
`.../media/<id>/<checksum>`, which answers `200` with the file. Free limits: 10 GB stored, 10 million reads a month,
no download fees; Pages Functions 100,000 requests a day (browsers keep each file for a year, so repeat views are free).

Prices change often. Check each provider's pricing page before committing; the points below are about
*how the pieces fit*, which does not change.

## What has to run somewhere

| Piece | What it is | Needs |
|---|---|---|
| Storefront | React app, static files after `npm run build` | Any static host |
| API | Spring Boot (Java 17), always on, keeps a scheduler running (Autopilot) | A server or container, ~512 MB RAM minimum |
| Database | MySQL | A managed MySQL or one on the same server |

## Can it all go on Vercel?

**Only the storefront.** Vercel runs static sites and short serverless functions. The Spring Boot API is a
long-running Java server with a scheduler and database connections, so it cannot run there, and Vercel
has no MySQL. `ecommerce-frontend/vercel.json` is ready for the storefront: SPA routing, the same security
headers and caching as the Railway setup.

Two things to know before choosing Vercel for the storefront:
- Vercel's free **Hobby** plan is for non-commercial use. A shop that takes orders belongs on a paid plan.
  Cloudflare Pages and Netlify have free tiers that allow commercial sites.
- A storefront on one domain and an API on another needs `CORS_ORIGINS` (API) and `VITE_API_URL`
  (storefront build) set to each other's addresses. Already supported; no code change.

## Lowest-cost setups, cheapest first

1. **One small VPS (about the price of a coffee or two a month).** Run the API and MySQL on it with Docker
   (`ecommerce-backend/Dockerfile`), put Caddy in front for free HTTPS, and host the storefront on
   Cloudflare Pages or Netlify for free. Cheapest and you control everything, but you do the updates and
   backups yourself.
2. **Container platform for the API (Render, Fly.io, Koyeb) + managed MySQL (Aiven or TiDB Serverless free
   tiers) + free static host.** Little server work. Free tiers often sleep when idle, which makes the first
   visitor wait and **pauses Autopilot**, so budget for a paid always-on instance before real launch.
3. **Stay on Railway** (storefront + API + MySQL in one place, deploys with one command). Usage-based, simple,
   and what is already set up. Your trial has expired, so a paid plan is needed.

**Recommendation:** launch on option 3 or 2 with a paid always-on API (a few dollars a month), put the
storefront on a free commercial-friendly static host, and move to option 1 only if you want the lowest bill
and are comfortable with server upkeep. Whichever you pick, take a MySQL backup before every release.

## Moving between hosts

Nothing in the code is tied to Railway. Move by: (1) dump MySQL, (2) restore it on the new database,
(3) set the same environment variables (see `.env.example`), (4) point `VITE_API_URL` and `CORS_ORIGINS`
at the new addresses, (5) deploy.

## Not verified here

`vercel.json` is valid JSON and mirrors the tested `serve.json`, but no Vercel deploy was made.
`Dockerfile` was not built (no Docker on the build machine). Test both once before relying on them.
