# SEO layer (Release 1: technical foundation)

Cloudflare Pages Functions give search engines and social crawlers real HTML and real HTTP status codes. The React app is unchanged: its `index.html` is fetched from the static assets and each page's head and page copy are injected into it.

| File | Role |
|---|---|
| `functions/_middleware.js` | Runs on every page request (not on `_routes.json` exclusions). |
| `functions/sitemap.xml.js` | `/sitemap.xml` from the live catalogue. |
| `seo/handler.js` | Routing, status codes, API calls, sitemap. |
| `seo/render.js` | Head tags, JSON-LD, page copy, injection into the shell. |
| `seo/meta.js` | Titles, descriptions, structured data. Also used by the React app on client-side navigation. |
| `seo/security.js` | Fallback security headers (a test keeps them identical to `public/_headers`). |
| `public/_routes.json` | Keeps static files (`/assets/*`, images, icons) out of the Functions quota. |
| `public/robots.txt` | Has the `Sitemap:` line. |
| `seo/seo.test.mjs`, `seo/check.mjs`, `seo/dev-server.mjs` | Unit tests, end-to-end checker, local Pages stand-in. |

## Behaviour

| URL | Status | Notes |
|---|---|---|
| `/` | 200 | Catalogue-based description, product links, Organization + WebSite JSON-LD |
| `/products/{id}` (published) | 200 | Title, description, canonical, Open Graph, Product + Offer + BreadcrumbList, visible price and stock |
| `/products/{id}` (not found) | 404 | The API answers 404 for both never-existed and unpublished/deleted products |
| `/products/{id}` (API answers 410) | 410 | Not produced today; wired for when the API distinguishes deletion |
| `/shipping` `/contact` `/privacy` `/terms` | 200 | Indexable, canonical, own description |
| `/cart` `/checkout` `/login` `/orders` `/admin` `/track` ... | 200 | `noindex` (meta + `X-Robots-Tag`); the app needs them to load |
| anything else | 404 | The app still loads and shows its own "not found" screen |
| API unreachable, product page | 503 + `Retry-After: 120` | Never 404, so an outage cannot drop pages from search |
| API unreachable, home page | 200 | Generic, accurate head |
| API unreachable, sitemap | 503 | Never an empty sitemap |
| `/x/`, `/index.html` | 301 | To the clean URL, on the same host |

**Only `rekhashoppingmall.com` and `www.rekhashoppingmall.com` are indexable.** Every other host (a preview such as `seo-release-1.<project>.pages.dev`, or the production `*.pages.dev` alias) is served with `X-Robots-Tag: noindex` and `<meta name="robots" content="noindex, nofollow">`; its canonical still points at the real site. `robots.txt` deliberately does not disallow those hosts, because a crawler that cannot fetch a page cannot see its noindex.

**Page copy.** The first HTML contains the product's name, price, availability and description as ordinary visible content, styled with the shop's own fonts. React replaces it when the app mounts (no duplicate content). On a throttled connection the plain copy shows until the app takes over; measured about 3.7 s at 200 KB/s with a 4x slower CPU, and under a second on a normal connection.

**Structured data.** Product (name, description, images, SKU only when the product has one, URL), Offer (live price, availability, seller), shipping (the shop's live flat fee, only when there is no free-shipping threshold), BreadcrumbList; Organization + WebSite on the home page. **Not published:** brand, ratings, reviews, fabric, and any return policy (`MerchantReturnPolicy` is omitted until the real policy has been legally reviewed).

## Data freshness

HTML is never cached (`Cache-Control: no-cache`). The product data comes from the API, cached in two places:

| Layer | Time | Notes |
|---|---|---|
| Cloudflare edge, this layer (`API_CACHE_SECONDS`) | up to 20 s | Per full URL, so `/api/products/23` and `/api/products/24` are separate entries; the list and store info have their own. Only 2xx answers are kept; a 404 or 5xx is never cached. `0` turns it off. |
| API server (`CatalogCache`) | up to 60 s | Cleared when the admin edits a product. Stock sold through orders may take up to the TTL to show. |

So the **price and stock in this HTML can be up to about 20 s older than the API, and the API's list up to 60 s older than the database for stock changes caused by orders.** The price a customer is charged is always computed by the backend at checkout, never taken from this HTML.

## Environment variables (Pages > Settings > Variables and Secrets)

| Name | Type | Default | Purpose |
|---|---|---|---|
| `API_ORIGIN` | plain, optional | `https://api.rekhashoppingmall.com` | Where product data is read from (GET only) |
| `ORIGIN_SECRET` | **Secret (encrypted)** | unset | Only if the function's own API calls are refused by the origin lock. Same value as `ORIGIN_SECRET` on Render. Set it as type **Secret**, never plain text, and never in git or chat. |
| `API_CACHE_SECONDS` | plain, optional | `20` | Upstream cache; `0` disables |
| `SEO_DISABLED` | plain | unset | `1` turns the layer off (rollback switch) |
| `VITE_API_URL` | plain, **build time** | none | Required for the storefront build, as today. Set it for **Preview** too |
| `NODE_VERSION` | plain, build time | `20` | As today |

Preview and Production have **separate** variable lists in Pages. Anything set for Production must be repeated under Preview.

Debug header: product responses carry `X-SEO-Upstream: ok`; on a 503 it says which call failed (`error (list 403, product 403)`; status `0` means timeout or network). No secret is ever put in a header.

## Test locally (no deployment, no packages)

```
cd ecommerce-frontend
npm ci
VITE_API_URL=https://api.rekhashoppingmall.com/api npm run build
npm run test:seo                       # unit tests
node seo/dev-server.mjs &              # local Pages stand-in on :8788 (reads the live API, GET only)
node seo/check.mjs http://127.0.0.1:8788
```

## Deploy

1. Push the `seo-release-1` branch only. Pages builds it as a **preview**. Do not merge to `main` yet.
2. In Pages, under **Preview**, set `VITE_API_URL` (and `NODE_VERSION=20`). Redeploy the preview if it built before you did.
3. Run `node seo/check.mjs https://<preview-url>`. It checks every product, the status codes, the sitemap, `robots.txt`, security headers, and that the preview is noindex.
4. If product pages return 503, read `X-SEO-Upstream`. A `403` means the origin lock refused the function: add `ORIGIN_SECRET` as a **Secret** under Preview, redeploy the preview, and re-run the check.
5. Do one test-mode Razorpay payment on the preview (the payment code is unchanged; this is a smoke test).
6. After approval, merge to `main`. Run `node seo/check.mjs https://rekhashoppingmall.com`.
7. In Google Search Console, submit `https://rekhashoppingmall.com/sitemap.xml`. Keep `/api/store/sitemap.xml` until the new one reads successfully.

## Roll back

Fastest first:

1. **Cloudflare dashboard:** Workers & Pages > project > Deployments > pick the last good deployment > *Rollback to this deployment*. Instant; no build.
2. **Kill switch:** set `SEO_DISABLED=1` and redeploy. Page requests and `/sitemap.xml` are handed back to the static site, exactly as before this release.
3. **Source:** `git revert` the release commit and push `main`. This also removes the `Sitemap:` line from `robots.txt`.

If you roll back, remove the sitemap from Search Console.

## Remaining work (outside Release 1)

- 410 for deleted products needs the API to tell "deleted" from "never existed" (a backend change).
- Product names repeat (five are "FANCY WORK SAREE'S"), so titles carry "(Item N)". Distinct names are the real fix; they live in the admin.
- Return policy: the published text says every sale is final. Structured data omits it until it is reviewed; see the release notes for what a review should cover.
- Social cards use the product photo from the API host; WhatsApp and Facebook prefer JPEG under about 300 KB (the photos are WebP, up to 500 KB).
