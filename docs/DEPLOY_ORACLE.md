# Moving the API from Render to Oracle Cloud Always Free

Plan written 2026-11-03, after checking Oracle's Always Free page, 2026 reports on the free-tier cut, the MySQL 8.0
end of life, Aiven's free plan, and this repository's current settings. Re-check Oracle's page on sign-up day:
<https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm>

## What moves and what stays

| Part | Today | After |
|---|---|---|
| Shop website | Cloudflare Pages | **Stays** (only `VITE_API_URL` changes) |
| API (Spring Boot) | Render free (sleeps) | **Oracle VM**, Docker, never sleeps |
| Database | Aiven free MySQL (1 GB cap, powered off when idle) | Phase A: stays on Aiven. Phase B (a week later): **MySQL 8.4 on the Oracle VM** |
| Photos and videos | Cloudflare R2 | **Stays** |
| HTTPS | Render | **Caddy** on the VM, free automatic certificate |
| Payments | Razorpay | Stays; add the new webhook address |

## Facts checked (November 2026)

- **Free Arm VM: 2 OCPU and 12 GB RAM in total** (Oracle cut it from 4/24 in June 2026; over-limit VMs were shut down
  after 18 Aug 2026). 200 GB disk, 10 TB traffic out per month, 20 GB Object Storage (backups).
- **Idle reclaim:** an Always Free VM can be reclaimed after 7 days with CPU (95th percentile), network and memory all
  under 20%. A quiet shop looks idle. **Upgrade to Pay As You Go** (still Rs 0 inside the free limits) and set a budget alert.
- **Card:** Visa or Mastercard **credit** card (or a debit card that works like a credit card) in your own name.
  RuPay, PIN-debit, prepaid and virtual cards are usually refused. Oracle bills in US dollars; a small verification
  charge is refunded.
- **Home region is permanent:** India West (Mumbai) or India South (Hyderabad). Free resources only exist there.
- **MySQL 8.0 reached end of life in April 2026.** The old kit used `mysql:8.0`; use **`mysql:8.4`** (LTS). 8.4 turns off
  `mysql_native_password`; the app's driver uses the default `caching_sha2_password`, so this is fine.
- **Aiven free MySQL: 1 GB storage** (cut from 5 GB in May 2025) and can be powered off when idle (the app's
  `AivenPowerGuard` switches it back on). This is the main reason to move the database too.
- Dumping from Aiven needs `--set-gtid-purged=OFF` (Aiven uses GTIDs; otherwise the import fails with ERROR 1840).
- Oracle blocks outgoing port 25, but order emails use Gmail on **587**: not affected.
- All images used (`maven:3.9-eclipse-temurin-17`, `eclipse-temurin:17-jre`, `mysql:8.4`, `caddy:2`) have Arm builds.

## The kit: deploy/oracle

| File | What it does |
|---|---|
| `setup-server.sh` | One-time server setup (safe to re-run): updates + automatic security updates, IST time zone, 4 GB swap, SSH keys-only, fail2ban, opens 80/443 in the VM firewall, Docker from Docker's apt repo, log caps, clones to `/opt/rekha/app`, creates `.env`, schedules the nightly backup |
| `.env.example` | **Every** setting the app reads, grouped, with the ones that must be copied from Render marked `[RENDER]` |
| `docker-compose.yml` | API + Caddy (HTTPS). Phase A: database stays on Aiven |
| `docker-compose.localdb.yml` | Adds MySQL 8.4 LTS for Phase B (turned on by `migrate-db.sh`) |
| `Caddyfile` | HTTPS for `API_DOMAIN`, certificate fetched and renewed automatically |
| `deploy.sh` | Pulls `main`; rebuilds and restarts only if the backend or kit changed; waits for HTTPS health; **rolls back automatically** to the previous image if the new one does not come up |
| `backup.sh` | Nightly dump of whichever database is in use, checked for completeness, 14 days on the server, **copied off the server to Oracle Object Storage** (30 days), optional failure email via healthchecks.io |
| `migrate-db.sh` | Phase B: Aiven to local MySQL; compares the row count of every table before switching; restarts on Aiven if anything differs |
| `restore.sh` | Disaster recovery: replace the local database with a backup file |
| `lib.sh` | Shared helpers; reads `.env` without executing it |

Automatic deploys: `.github/workflows/deploy-oracle.yml` runs `deploy.sh` on the server after every push to `main`
whose CI passed. It is off until the repository variable `ORACLE_DEPLOY=true` and three secrets are set (see the
file). The deploy key is locked on the server to `deploy.sh` only.

Fixed against the October kit: MySQL 8.0 (end of life) to 8.4; Aiven-first mode; the full settings list (it had 17
of about 70); `.env.example` was silently git-ignored and never reached the server; no automatic deploy; backups
only on the VM; no log size cap; Vercel references (the shop is on Cloudflare Pages). The keep-alive job now reads the
API address from the repository variable `API_URL`.

Checked on the development PC: script syntax, YAML, and the `.env` reader and database selection against a sample
`.env`. Not yet run: Docker itself (no Docker on that PC). The first real run is on the VM in Step 5, while Render
still serves the shop.

## Settings that differ from the app's built-in defaults

The live shop relies on values set on Render. If they are missing on Oracle the app silently uses its defaults:
`COD_ENABLED` (default **true**, live: false), `FREE_SHIPPING_THRESHOLD` (default 999, live: 0), `SHIPPING_FEE`
(default 49, live: 99), `PAYMENT_PROVIDER` (default none, live: razorpay), `SEED_DEMO_CATALOG`. They are pre-filled in
`.env.example`, but compare every line with Render. A line that is present but **empty** replaces a default with
nothing: comment it out instead.

## Two traps to avoid on the day

- **Same `JWT_SECRET` as Render.** A new one signs every customer out (sessions last 30 days). Copy it exactly.
- **Two APIs running at once send duplicate emails.** Render and Oracle both run the schedulers (payment
  reconcile, cart reminders, daily digest, clean-up). Keep the overlap short and **suspend** the Render service
  right after the switch (suspend, not delete, so rollback stays one click).

## The plan

### Step 1. Your part: accounts (30 minutes to 1 day)
1. Buy a domain, e.g. `rekhashoppingmall.in` or `.com` (about Rs 500-1,200 a year). Put its DNS on Cloudflare (free),
   where the shop already lives.
2. Sign up at cloud.oracle.com: home region **Mumbai or Hyderabad**, Visa/Mastercard credit card.
3. **Upgrade to Pay As You Go** at once, and add a budget alert of US$1.

### Step 2. Your part, with me guiding: the server (15 minutes)
1. Create instance: image **Canonical Ubuntu 24.04** (aarch64), shape **VM.Standard.A1.Flex, 2 OCPU, 12 GB**,
   boot volume 100 GB, public IP on. Use an SSH key made on this PC (`ssh-keygen -t ed25519`).
   "Out of host capacity" means retry later or another availability domain.
2. Networking > the VCN's security list: allow TCP **80** and **443** from 0.0.0.0/0.
3. Cloudflare DNS: `api` A record to the VM's IP, **DNS only** (grey cloud) so Caddy can get the certificate.

### Step 3. My part: prepare the server (about 30 minutes, over SSH from this PC)
```bash
ssh ubuntu@<IP>
curl -fsSL https://raw.githubusercontent.com/allevarun1/InfinityStore-E-Commerce-Shopping-Platform/main/deploy/oracle/setup-server.sh -o setup.sh
bash setup.sh        # then log out and back in
```

### Step 4. Done: the kit is fixed (see "The kit" above)

### Step 5. Together: start on Oracle while Render keeps serving (about 1 hour)
1. You fill in `/opt/rekha/app/deploy/oracle/.env` yourself (`nano`), copying from Render (secrets never pass
   through chat). Same `JWT_SECRET`, same Aiven `DB_URL`, same Razorpay and R2 values.
2. Start: `/opt/rekha/app/deploy/oracle/deploy.sh --force` (first Arm build about 5-10 minutes). It waits until
   `https://api.<domain>/api/store/info` answers.
3. Check sarees, photos from R2, admin sign-in, customer sign-in, Google sign-in, and a Razorpay **test** payment end
   to end (the order turns Paid only after payment).
4. Razorpay dashboard > Webhooks: add `https://api.<domain>/api/payments/razorpay/webhook` with the same secret
   and the same events (payment.authorized, payment.captured, order.paid).
5. Backups: create the bucket and a Customer secret key in Oracle, fill `BACKUP_S3_*`, run `./backup.sh` once and
   see the file in the bucket.
6. Automatic deploys: make a deploy key, add the three GitHub secrets and `ORACLE_DEPLOY=true` (steps in
   `.github/workflows/deploy-oracle.yml`), then run the workflow once by hand.

### Step 6. Switch (10 minutes, no downtime)
1. Cloudflare Pages: set `VITE_API_URL=https://api.<domain>/api` and redeploy. Both APIs use the same Aiven
   database, so nothing is lost while the switch spreads.
2. **Suspend** the Render service. Point UptimeRobot at `https://api.<domain>/api/store/info` and set the
   repository variable `API_URL=https://api.<domain>/api` for the keep-alive job.
3. Watch logs and orders for 24 hours.

Rollback (while the database is still on Aiven): resume Render, set `VITE_API_URL` back, redeploy. About 5 minutes.

### Step 7. A week later: move the database onto the VM (30 minutes, 5-10 minute pause)
1. Pick a quiet time. Put two new passwords in `.env` (`LOCAL_DB_PASSWORD`, `LOCAL_DB_ROOT_PASSWORD`,
   from `openssl rand -hex 24`).
2. `./migrate-db.sh`: starts MySQL 8.4, pauses the API, copies from Aiven, compares **every** table's row count,
   and only then switches `.env` and restarts. On any difference it restarts on Aiven unchanged.
3. Test again. Remove `AIVEN_API_TOKEN` from `.env` and run `./deploy.sh --force`.
4. The nightly backup now dumps the local database. Test a restore once a month (`restore.sh` on a copy).
5. Keep Aiven for two weeks as a fallback, then delete it and Render.

### Step 8. Optional, same week: the shop on your own domain
`www.<domain>` on Cloudflare Pages; add it to `CORS_ORIGINS` and `FRONTEND_URL`, Google sign-in's authorised
origins, Firebase's authorised domains and the Razorpay website details (helps KYC).

## Time

| When | What | Who | Time |
|---|---|---|---|
| Day 1 | Domain, Oracle sign-up, Pay As You Go | You | 30 min (+ waiting for Oracle) |
| Done | Fix the kit | Me | done |
| Day 1-2 | Create the VM, open ports, DNS | You, guided | 15 min |
| Day 1-2 | Harden server, start app, full tests | Me (you paste secrets) | 1.5-2 h |
| Day 1-2 | Switch, suspend Render | Together | 10 min |
| Day 8 | Move database to the VM | Me | 30 min |
| Day 22 | Delete Render and Aiven | You | 5 min |

About half a day of real work, spread over 1-2 days, plus the database move a week later.

## Monthly cost

| Item | Per month |
|---|---|
| Oracle VM, disk, traffic, backups (inside free limits, Pay As You Go) | Rs 0 |
| Cloudflare Pages, Cloudflare DNS, R2 (first 10 GB) | Rs 0 |
| MySQL 8.4 on the VM | Rs 0 |
| Gmail order emails, UptimeRobot, GitHub | Rs 0 |
| Domain | about Rs 50-100 |
| **Total fixed** | **about Rs 50-100** |
| Razorpay | no monthly fee; about 2% per payment plus GST on the fee |

Beyond the free limits, extra Arm capacity is about US$0.013 per OCPU-hour and US$0.002 per GB-hour
(roughly US$28 a month to double to 4 OCPU / 24 GB). Not needed for a long time.

## Risks that remain

- One server: if it fails, the shop is down until restored. Off-server backups and UptimeRobot alerts cover this.
- Oracle can change the free tier again (it did in 2026). The setup is plain Docker + MySQL, so moving to any other
  host takes about an hour.
- Sign-up can be refused for no stated reason; then the fallback is Render's paid plan (US$7 a month, no sleep).

## If the Arm server can't be had (status on 2026-11-04)

Network, ports, SSH key, API key and the retry script (`~/.oci/get_server.py` on the dev PC) are ready. The only
thing missing is the server itself: Hyderabad (the account's home region, only one zone) has had no free Arm
capacity, and free-tier accounts are served last. Try the options in this order and stop at the first that works.
Nothing is lost while trying, because the shop keeps running on Render + Aiven the whole time.

1. **Pay As You Go upgrade** (best). Billing > Upgrade and Manage Payment > add the card > Upgrade. Always Free
   items stay free; set a US$1 budget alert straight away. Then run the retry script: paid accounts usually get
   a server within minutes to hours.
2. **If the upgrade is refused:** first correct the account address to exactly what the card's bank has
   (it currently mixes Sircilla with "KANCHIPURAM, TAMIL NADU 631604"), then retry. Try a different Visa or
   Mastercard credit card belonging to the account holder; debit, RuPay and prepaid cards are often refused.
   If it still fails, open Oracle's support chat (Help ? in the console) and ask why the upgrade fails.
3. **Stay free and keep retrying.** Run the retry script around the clock (a Windows scheduled task that
   starts it at boot and keeps the PC from sleeping). Free-tier capacity in a busy region often appears within
   days, sometimes a couple of weeks. It needs no money but has no time guarantee.
4. **Temporary: Oracle's free AMD micro server** (VM.Standard.E2.1.Micro, 1 GB memory, usually available).
   It can run the API only if the Docker image is built by GitHub Actions instead of on the server, the Java
   heap is capped (about 350 MB) and the 4 GB swap is on, with the database staying on Aiven. It will be slow
   under load. Use it only as a stop-gap, then move to the Arm server when the retry script gets one.
5. **A small paid server in India, same kit.** `setup-server.sh` and the Compose files run on any Ubuntu 24.04
   machine; only the Oracle console steps differ (open 80/443 in the provider's firewall instead). Pick at
   least 2 GB memory. Rough prices (check on the day): AWS Lightsail Mumbai or DigitalOcean Bangalore, about
   US$10-12 a month for 2 GB; 4 GB is about US$20-24.
6. **Stay on Render** and remove the sleep with its paid plan (about US$7 a month), keeping Aiven for the
   database. Least work; no move at all.

Decision rule: if 1 works, done. If not, start 3 at once (free, runs in the background) and, if the sleeping
free Render service is hurting sales, cover the wait with 6 (cheapest, no migration) rather than 4 or 5.
