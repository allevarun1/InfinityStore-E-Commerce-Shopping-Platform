#!/usr/bin/env bash
# Disaster recovery (Phase B): replaces the local database with a backup file.
#   ./restore.sh /opt/rekha/backups/rekha-2026-11-20-0230.sql.gz --yes
# On a brand-new server: run setup-server.sh, put the old .env back, download the dump from the
# Object Storage bucket (db/ folder), then run this. The shop is paused while it runs.
set -Eeuo pipefail

main() {
  # shellcheck source=lib.sh
  . "$(dirname "$(readlink -f "$0")")/lib.sh"
  local file="${1:-}"
  [ -f "$file" ] || die "usage: ./restore.sh BACKUP.sql.gz --yes"
  [ "${2:-}" = --yes ] || die "this REPLACES all data in the local database; add --yes to confirm"
  using_local_db || die "COMPOSE_FILE does not include docker-compose.localdb.yml: restore is for the local database"
  gzip -t "$file" || die "not a valid gzip file"

  compose up -d db
  wait_db
  log "stopping the API"
  compose stop api
  db_target local-root
  mysql_client mysql -e "DROP DATABASE IF EXISTS rekha; CREATE DATABASE rekha CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL ON rekha.* TO 'shop'@'%';"
  log "importing $file"
  zcat "$file" | mysql_client mysql --default-character-set=utf8mb4 rekha
  table_counts | sed 's/^/    /'
  compose up -d --remove-orphans
  wait_healthy 300 && log "restored and healthy" || die "API did not become healthy: check 'docker compose logs api'"
}

main "$@"
