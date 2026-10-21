# Deploying the backend on Oracle Cloud Always Free

Researched October 2026 against Oracle's own page
(<https://docs.oracle.com/en-us/iaas/Content/FreeTier/resourceref.htm>). Re-check it before you sign up: free tiers change.

## What "free" really means here

| Item | Always Free allowance (Oracle docs) |
|---|---|
| Arm VM (Ampere A1) | **2 OCPUs and 12 GB RAM in total** (some older guides still say 4 and 24; that is outdated) |
| Small AMD VMs | 2 x (1/8 OCPU, 1 GB RAM, 50 Mbps). Too weak for this backend |
| Disk | 200 GB total (boot and block volumes), 5 backups |
| Traffic out | 10 TB per month |
| Database | A free MySQL HeatWave system (50 GB) exists, but we run MySQL in Docker on the VM instead: simpler and not tied to availability |
| Object storage | 20 GB (Always Free only): used for backups |

Catches, in order of how much they can hurt:

1. **Idle reclaim.** Oracle may reclaim an Always Free VM that is idle for 7 days (95th-percentile CPU under 20% **and**
   network under 20% **and**, for Arm, memory under 20%). A small store will look idle. Upgrading the account to
   **Pay As You Go** (a card on file, you still pay nothing while inside the free limits) is the usual way to stay safe;
   set a budget alert of 1 dollar. Confirm the current rule in the console before relying on it.
2. **"Out of host capacity".** Creating the Arm VM sometimes fails. Retry later, try another availability domain, or
   upgrade to Pay As You Go.
3. **Sign-up can be refused** (card or region checks). There is no documented reason; it is luck and card type.
4. **Home region is permanent.** Pick **India South (Mumbai)** or **India South (Hyderabad)** for shoppers in India.
5. **Backups are your job.** A free VM can vanish; keep database dumps off the VM.
6. The 2026 allowance cut means going above 2 OCPU / 12 GB may be billed. Stay inside it.

## Plan (about 1.5 hours)

1. **Account.** cloud.oracle.com, Always Free sign-up, home region Mumbai.
2. **VM.** Compute > Create instance > image *Ubuntu 22.04* > shape *VM.Standard.A1.Flex*, 2 OCPU, 12 GB, boot volume 100 GB.
   Add your SSH public key. Note the public IP.
3. **Open the ports twice.** (a) VCN security list: allow TCP 80 and 443 from 0.0.0.0/0. (b) On the VM, Oracle's Ubuntu image
   blocks them with iptables as well:
   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```
4. **Domain with HTTPS.** Vercel serves the storefront over https, so the API must be https too. Point a name at the VM
   (a real domain `api.yourstore.com`, or a free `yourname.duckdns.org`). Caddy fetches the certificate by itself.
5. **Install Docker.** `curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker ubuntu`
6. **Get the code and configure.**
   ```bash
   git clone https://github.com/allevarun1/InfinityStore-E-Commerce-Shopping-Platform.git && cd InfinityStore-E-Commerce-Shopping-Platform
   cp deploy/oracle/.env.example deploy/oracle/.env   # then edit every value
   ```
7. **Start.** `cd deploy/oracle && docker compose up -d --build` (the first build takes a few minutes on Arm).
8. **Check.** `curl https://api.yourstore.com/api/store/info`
9. **Frontend.** In Vercel set `VITE_API_URL=https://api.yourstore.com/api` and redeploy. In `.env` set
   `CORS_ORIGINS` and `FRONTEND_URL` to your Vercel URL.
10. **Backups.** `crontab -e` and add `0 3 * * * /home/ubuntu/InfinityStore-E-Commerce-Shopping-Platform/deploy/oracle/backup.sh`.
    Download a copy to your own computer weekly until you add Object Storage uploads.
11. **Updates.** `git pull && docker compose up -d --build`.

## Moving to a paid host later

Nothing here is Oracle-specific: it is a Docker image, a MySQL database and environment variables. To move, dump the
database (`backup.sh`), restore it on the new host, set the same variables, and change the DNS record. About an hour.

## Measured speed (this repository, October 2026)

See the table in the chat reply this file came with; the short version: the API answers in 5 to 13 ms per request on a
12-core development PC and sustains roughly 250 to 900 requests per second at 25 concurrent users. The 2-OCPU Arm VM will be
several times slower per request but far above what an early-stage store needs.

## When do we outgrow the free tier? (model in tools/capacity_model.py)

Run `python tools/capacity_model.py` after changing the assumptions at the top. Headline numbers with the defaults
(12 API calls per visit, 2% of visits order, Rs 3,500 per order, a 2-OCPU Arm VM assumed ~5x slower than a 12-core PC):

* **RAM**: about 2.5 GB used of 12 GB. Never the limit.
* **Disk**: about 15 GB for the system plus the database. Even 2,000 products with uploaded photos and a million orders
  use about 55 GB of 200 GB including 14 days of backups. Never the first limit.
* **Traffic**: 10 TB per month is about 5 million visits even if photos are served by the backend. Not the first limit.
* **CPU is the first real limit**: roughly 500,000 to 1,000,000 visits a month (or a flash-sale spike of about 35 requests a second).
* **The bigger risk is the single VM**, not the quota: one machine, one disk, backups on the same box.

Cost once you pass the free limits (A1 compute list price: $0.01 per OCPU-hour and $0.0015 per GB-hour; verify the disk and
traffic prices on Oracle's price list): going from 2 to 4 OCPU and 12 to 24 GB costs about $28 a month.
