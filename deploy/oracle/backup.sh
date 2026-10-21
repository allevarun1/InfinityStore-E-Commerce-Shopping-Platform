#!/bin/bash
# Nightly MySQL dump, kept for 14 days in ./backups. Copy these off the server too (a free VM can be reclaimed).
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p backups
set -a; . ./.env; set +a
docker compose exec -T db mysqldump -uroot -p"$DB_ROOT_PASSWORD" --single-transaction infinitystore | gzip > "backups/infinitystore-$(date +%F).sql.gz"
find backups -name '*.sql.gz' -mtime +14 -delete
