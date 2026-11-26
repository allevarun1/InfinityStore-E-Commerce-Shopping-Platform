# Database migration plan: Aiven MySQL to TiDB Cloud Starter

Written 2026-11-26. Self-contained so work can continue on any device. **No secrets belong in this file.**

## Why we must move

- Aiven is retiring its free MySQL tier. New free services closed on 2026-11-06. **On 2026-12-21 Aiven powers off every remaining free MySQL service and deletes its data and backups.** There is no self-service recovery afterwards.
  Source: https://aiven.io/docs/products/mysql/howto/mysql-free-tier-migration-options
- Free Aiven storage is only 1 GB.
- Requirement from the owner: free forever, not Oracle, close to the API (Render, Singapore), enough space for the shop.

## Decision

**Target: TiDB Cloud Starter (MySQL-compatible), AWS Singapore (ap-southeast-1), free plan, with a small spending limit (about $5) as a safety net.**
**Fallback: Aiven Developer plan, $5/month**: 1 CPU, 1 GB RAM, 8 GB, always on, daily backups, upgrades in place and keeps the connection details, so zero migration risk. It is single node with no HA or SLA.

Rejected: Neon / Supabase (Postgres, would need a code rewrite and have only 1 GB / 500 MB), Oracle (excluded by owner), PlanetScale (no free tier), Turso / MongoDB Atlas (rewrite).

### TiDB Starter facts (checked 2026-11-26 from docs.pingcap.com and pingcap.com)

- Free per instance per month: 5 GiB row storage, 5 GiB columnar, 50 million Request Units (RUs). First 5 instances per organisation are free, no card needed.
- Beyond free (only if a spending limit is set): about $0.20-0.38 per GiB-month storage, $0.10-0.16 per 1M RUs (Singapore's exact figure not confirmed).
- Network egress is billed inside RUs (1 RU per 1 KiB read on the public endpoint), not per GB.
- When the quota is used up: new connections are refused and existing ones throttled until the month resets or a spending limit is raised.
- Up to 400 concurrent connections (5,000 with a spending limit). AWS public endpoint drops idle connections after 340 seconds; keep-alives cannot prevent it, so keep connection lifetime to about 5 minutes or less.
- TLS is required. Version upgrades are automatic. No Data Migration, no Changefeed, no `IMPORT INTO`. Transactions up to 30 minutes. 256 MiB memory per query on free.
- Regions offered include Singapore (ap-southeast-1). Mumbai not listed.
- NOT found in the docs, so **verify during rehearsal**: foreign key enforcement, backup / point-in-time recovery for Starter, idle-instance deletion policy, single-row size limit (about 6 MiB on TiDB by default), collation support.

## What the code review found

- No native SQL anywhere. Only JPQL and Spring Data, so queries are portable.
- Money is `DECIMAL(12,2)`, dates are `LocalDateTime` (`DATETIME`), enums are strings.
- Media is in Cloudflare R2, so the database only holds text and numbers.
- `MysqlConnectionTuning` options are plain driver settings and work on TiDB.

| # | Difference | Impact in this app | Action |
|---|---|---|---|
| 1 | TiDB default collation is case-sensitive (`utf8mb4_bin`) | `users.username` and `users.email` rely on unique constraints and `findByUsername` / `findByEmail`. MySQL matches `Admin` and `admin`; TiDB would not, and duplicates by case become possible. | Create the database as `utf8mb4` with `utf8mb4_general_ci`. Verify Starter accepts it. Add a login-case test. |
| 2 | Auto-increment IDs are allocated in blocks | All 24 tables use `IDENTITY`. IDs may have gaps and arrive out of order. Code sorts by id (fine). | Check how order numbers are shown. After import, insert a row per table and confirm IDs continue above the maximum. |
| 3 | Row locks | Checkout locks product, user and coupon rows (`PESSIMISTIC_WRITE`) to stop overselling. TiDB defaults to pessimistic transactions. | Concurrency test: 20 parallel orders for 5 units must sell exactly 5. |
| 4 | Large text columns | `products.image_url` and `gallery` are `LONGTEXT`; old rows may hold base64 photos (there is a query for `data:%`). | Before the dump, find rows over 5 MB and move them to R2 or shrink them. |
| 5 | Foreign keys | Hibernate creates them. Older TiDB versions accept but do not enforce them. | Check at rehearsal. The app still works if unenforced. |
| 6 | Connection lifetime | Hikari default max lifetime is 30 min; AWS idles out at 340 s. | `spring.datasource.hikari.max-lifetime=240000`. The existing 120 s keep-alive is already below 340 s. |
| 7 | TLS and port | TLS mandatory; TiDB uses port 4000 and prefixes the username. | Use `sslMode=VERIFY_IDENTITY`. The TiDB console shows the exact JDBC URL. |
| 8 | `ddl-auto=update` on every boot | A slow or failed `ALTER` on TiDB would delay startup. | Let Hibernate create the empty schema once on staging, import data only, then switch production to `DDL_AUTO=validate`. |
| 9 | Tests run on H2 (`MODE=MySQL`) | They cannot catch any of the above. | Run the integration suite once against a throwaway TiDB staging instance. Never run it against production (`create-drop`). |

Code and config to change: remove `config/AivenPowerGuard.java` and its call in `EcommerceApplication`; remove the Aiven token and mentions from `render.yaml`, `.github/workflows/keep-alive.yml`, `docs/HOSTING.md`, `README.md`.

## Phases and schedule (cut-over target 2026-12-03 to 12-05; hard deadline 2026-12-21)

**Phase 0: Safety backup (by 11-27).** Run a read-only `mysqldump --single-transaction --routines --triggers` of Aiven. Store it encrypted, outside the repo. Do not upload dumps as GitHub artifacts: they contain customer data and artifacts on public repos are downloadable. Run the dump again just before cut-over.

**Phase 1: Staging (11-27 to 11-28).** Owner creates the TiDB Cloud account and a free Starter cluster in Singapore for staging (up to 5 free instances, so staging never touches production). Create the database with `utf8mb4` / `utf8mb4_general_ci`. Create a separate SQL user for the app.

**Phase 2: Code and tests (11-28 to 12-02).**
1. Add the Hikari `max-lifetime` and TLS URL handling in a branch.
2. Let Hibernate create the schema on staging; diff columns, types, indexes and uniques against the Aiven schema.
3. Run the integration suite against staging.
4. Add tests: stock concurrency, case-insensitive login, unique email by case.
5. Remove `AivenPowerGuard`; update docs.

**Phase 3: Rehearsal (12-02 to 12-03).**
1. Import a real dump into staging, data only (`--no-create-info --complete-insert`, foreign key checks off).
2. Compare per-table row counts, `SUM(total_price)` on orders, `MAX(id)` per table, and a checksum of key columns.
3. Point a spare Render deploy at staging and walk through browse, sign-in, cart, a COD order, admin edit, media.
4. Insert a row per table to confirm auto-increment continues.
5. Time the import (expected: minutes).

**Phase 4: Cut-over (quiet hour, IST).**
1. Create the production TiDB instance with the same settings.
2. Suspend the Render API. Cloudflare keeps serving the cached catalogue (stale-if-error up to 1 h).
3. Take the final Aiven dump, import it, and run the same count and checksum checks.
4. Owner pastes the new `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` into Render. Never share them in chat.
5. Resume the API and smoke-test. Planned downtime: about 10-20 minutes.

**Phase 5: Observe and roll back if needed (to 12-14).**
- Rollback: restore the three old Render variables. Orders placed after cut-over would be missing from Aiven, so check carefully in the first hour.
- Watch RU usage in the TiDB console, error rates, and checkout speed. Set a spending limit of about $5 if usage nears the free cap.

**Phase 6: Backups and clean-up (by 12-18).**
1. Add a scheduled dump (at least weekly), encrypted, to a private R2 bucket. TiDB Starter backups are not documented, so do not rely on them.
2. Keep one final encrypted Aiven dump permanently.
3. Remove the Aiven variables from Render, delete the Aiven API token, update docs. Aiven shuts down by itself on 12-21.

## Cost summary

| Option | Cost |
|---|---|
| TiDB Starter free | $0 |
| TiDB with a spending limit | $0 within the free quota; usage-based beyond it (illustration: 100M RUs a month would be roughly $5-8) |
| Aiven Developer (fallback) | $5/month, 8 GB |

Expect $0 for the first year or more; a busy shop might reach about $5-15/month later.

## Decision rule

Rehearse first. If staging passes the tests (collation, locks, foreign keys, row sizes) and RU usage looks safe, go live on TiDB free. Otherwise upgrade the existing Aiven service to Developer ($5/month), which needs no migration.

## Owner's tasks (the assistant cannot do these)

- Create the TiDB Cloud account and clusters, choose passwords, and paste the production credentials into Render.
- Provide the Aiven connection details locally, or run the dump command provided.

## Progress log

- 2026-11-26: plan written. Nothing changed yet. Next step: Phase 0 dump script and oversized-row check.
