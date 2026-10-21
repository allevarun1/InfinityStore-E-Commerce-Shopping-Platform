# Capacity model for the Oracle Always Free VM (2 OCPU Arm, 12 GB RAM, 200 GB disk, 10 TB egress/month).
# Measured: API 5-13 ms/request alone, 250-900 req/s at 25 concurrent on a 12-core PC; JVM RSS ~290 MB after load.
# Assumptions (stated, adjustable): 12 API calls per visit, 60 KB compressed API data per visit,
# +2 MB per visit if photos are served by the backend (uploaded images live in the database), conversion 2%, order value Rs 3,500.
# Arm VM at 2 OCPU is assumed ~5x slower per request than the 12-core dev PC -> safe sustained ~60 req/s, burst ~120 req/s.

CALLS = 12
API_KB = 60
IMG_MB = 2.0
CONV = 0.02
AOV = 3500
KB_PER_ORDER = 4.0        # order + items + customer + audit rows incl. indexes
MB_PER_UPLOADED_PRODUCT = 2.0   # 6 photos stored as data URLs (base64)
SAFE_RPS = 60
PEAK_FACTOR = 15          # sale-hour traffic vs the monthly average rate

print("visits/mo | orders/mo | revenue/mo (Rs lakh) | API calls/mo | avg req/s | peak req/s | CPU headroom | egress API-only GB | egress with backend photos GB | DB growth MB/mo (orders)")
for visits in [5_000, 20_000, 50_000, 200_000, 500_000, 1_000_000, 2_500_000]:
    orders = visits * CONV
    calls = visits * CALLS
    avg = calls / (30 * 86400)
    peak = avg * PEAK_FACTOR
    head = SAFE_RPS / peak
    eg_api = visits * API_KB / 1024 / 1024
    eg_img = visits * (API_KB / 1024 + IMG_MB) / 1024
    db = orders * KB_PER_ORDER / 1024
    print(f"{visits:>9,} | {orders:>9,.0f} | {orders*AOV/1e5:>8.1f} | {calls:>12,} | {avg:>6.2f} | {peak:>7.1f} | {head:>6.1f}x | {eg_api:>8.1f} | {eg_img:>8.1f} | {db:>8.1f}")

print()
print("RAM budget (GB): OS 0.4 + Docker 0.2 + Spring Boot JVM 0.5 + MySQL (1 GB buffer pool + overhead) 1.4 + Caddy 0.05 =", 0.4+0.2+0.5+1.4+0.05, "of 12")
print("Oracle 'idle' threshold for memory: 20% of 12 GB =", 12*0.2, "GB -> we sit below it, so rely on CPU/network or upgrade to PAYG")
print()
print("Disk (GB of 200): OS+Docker images+build cache ~15; MySQL data grows; backups = 14 daily dumps ~ (30% of DB) x 14")
for products_with_uploads, orders_total in [(30, 1_000), (100, 20_000), (500, 200_000), (2000, 1_000_000)]:
    db = products_with_uploads * MB_PER_UPLOADED_PRODUCT / 1024 + orders_total * KB_PER_ORDER / 1024 / 1024
    backups = db * 0.3 * 14
    print(f"  {products_with_uploads:>5} products w/ uploaded photos + {orders_total:>9,} lifetime orders: DB {db:5.2f} GB, backups {backups:5.2f} GB, total used {15+db+backups:6.1f} GB ({(15+db+backups)/200*100:.0f}% of 200)")

print()
print("Pay As You Go price check (A1 $0.01/OCPU-h, $0.0015/GB-h confirmed; disk $0.0255/GB-mo and egress ~$0.0085/GB are recalled, verify):")
h = 730
for ocpu, gb in [(2, 12), (4, 24), (4, 16), (8, 48)]:
    extra_o, extra_g = max(0, ocpu - 2), max(0, gb - 12)
    cost = extra_o * 0.01 * h + extra_g * 0.0015 * h
    print(f"  VM {ocpu} OCPU / {gb} GB RAM: extra over free = ${cost:5.1f}/month (whole VM at list price ${ocpu*0.01*h+gb*0.0015*h:5.1f})")
print(f"  +100 GB disk over 200 GB: ~${100*0.0255:.1f}/month ; +1 TB egress over 10 TB: ~${1024*0.0085:.1f}/month")
