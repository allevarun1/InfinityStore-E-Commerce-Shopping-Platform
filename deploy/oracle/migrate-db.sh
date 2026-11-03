#!/usr/bin/env bash
# Phase B: moves the database from Aiven onto this server, safely.
#   1. starts MySQL 8.4 here (empty)          4. compares the row count of EVERY table on both sides
#   2. stops the API (shop pauses ~5-10 min)  5. only if all match: switches .env to the local database and starts
#   3. copies Aiven -> here                   6. on any problem: restarts the API on Aiven, unchanged
# Aiven is left untouched, so going back is: set COMPOSE_FILE=docker-compose.yml in .env, then ./deploy.sh --force.
set -Eeuo pipefail

main() {
  # shellcheck source=lib.sh
  . "$(dirname "$(readlink -f "$0")")/lib.sh"
  using_local_db && die "already on the local database"
  [ -n "$(envget LOCAL_DB_PASSWORD)" ] && [ -n "$(envget LOCAL_DB_ROOT_PASSWORD)" ] \
    || die "set LOCAL_DB_PASSWORD and LOCAL_DB_ROOT_PASSWORD in .env first (openssl rand -hex 24)"

  local both="docker-compose.yml:docker-compose.localdb.yml" dump
  mkdir -p "$BACKUP_DIR"
  dump="$BACKUP_DIR/migrate-aiven-$(date +%F-%H%M).sql.gz"

  log "starting MySQL 8.4 on this server"
  # The db-only commands use both files; the API keeps running on Aiven until the switch below.
  export COMPOSE_FILE="$both"
  compose up -d db
  wait_db
  unset COMPOSE_FILE
  db_target local-root
  local existing
  existing="$(mysql_client mysql -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='rekha'")"
  [ "$existing" = 0 ] || die "the local database already has $existing tables: not overwriting it (see restore.sh)"

  log "stopping the API: the shop pauses now"
  compose stop api
  restart_on_aiven() { log "restarting the API on Aiven (nothing changed)"; compose up -d api; }
  trap 'restart_on_aiven' ERR

  db_target external
  log "copying from Aiven ($DB_HOST)"
  dump_db "$dump"
  local aiven_counts; aiven_counts="$(table_counts)"

  db_target local-root
  log "importing into MySQL on this server"
  zcat "$dump" | mysql_client mysql --default-character-set=utf8mb4 "$DB_NAME"
  local local_counts; local_counts="$(table_counts)"

  if [ "$aiven_counts" != "$local_counts" ]; then
    diff <(echo "$aiven_counts") <(echo "$local_counts") || true
    false  # triggers restart_on_aiven
  fi
  log "all $(echo "$local_counts" | wc -l) tables match:"
  echo "$local_counts" | sed 's/^/    /'

  trap - ERR
  sed -i "s|^COMPOSE_FILE=.*|COMPOSE_FILE=$both|" "$ENV_FILE"
  log "switched .env to the local database; starting the API"
  compose up -d --remove-orphans
  if wait_healthy 300; then
    log "done. The shop now uses MySQL on this server. Remove AIVEN_API_TOKEN from .env; keep Aiven 2 weeks, then delete it."
  else
    log "API is not healthy on the local database: switching back to Aiven"
    sed -i "s|^COMPOSE_FILE=.*|COMPOSE_FILE=docker-compose.yml|" "$ENV_FILE"
    compose up -d --remove-orphans
    exit 1
  fi
}

main "$@"
