#!/usr/bin/env bash
# Shared helpers for deploy.sh, backup.sh, migrate-db.sh and restore.sh. Sourced, not run.

KIT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$KIT_DIR/../.." && pwd)"
ENV_FILE="$KIT_DIR/.env"
BACKUP_DIR="${BACKUP_DIR:-/opt/rekha/backups}"
MYSQL_IMAGE="mysql:8.4"
NETWORK="rekha_default"

log() { printf '%s  %s\n' "$(date '+%F %T')" "$*"; }
die() { log "ERROR: $*" >&2; exit 1; }

# Reads one value from .env without executing the file (values may contain spaces). Strips surrounding quotes.
envget() {
  [ -f "$ENV_FILE" ] || die "$ENV_FILE is missing (copy .env.example and fill it in)"
  local line value
  line="$(grep -E "^[[:space:]]*$1=" "$ENV_FILE" | tail -n 1 || true)"
  value="${line#*=}"
  value="${value%$'\r'}"
  if [[ "$value" =~ ^\"(.*)\"$ ]] || [[ "$value" =~ ^\'(.*)\'$ ]]; then value="${BASH_REMATCH[1]}"; fi
  printf '%s' "$value"
}

compose() { (cd "$KIT_DIR" && docker compose "$@"); }

using_local_db() { [[ "$(envget COMPOSE_FILE)" == *localdb* ]]; }

# Sets DB_HOST DB_PORT DB_NAME DB_USER DB_PASS DB_SSL for the database the API currently uses.
# Pass "external" to force the Aiven database from DB_URL, "local" for MySQL on this server, "local-root" for the
# same as its root user (imports need it). Local connections use TLS (MySQL 8.4 makes its own certificate), which
# its default caching_sha2_password login needs for client programs that have no cached key.
db_target() {
  local which="${1:-auto}"
  if [ "$which" = local-root ]; then
    DB_HOST=db; DB_PORT=3306; DB_NAME=rekha; DB_USER=root
    DB_PASS="$(envget LOCAL_DB_ROOT_PASSWORD)"; DB_SSL=REQUIRED
  elif [ "$which" = local ] || { [ "$which" = auto ] && using_local_db; }; then
    DB_HOST=db; DB_PORT=3306; DB_NAME=rekha; DB_USER=shop
    DB_PASS="$(envget LOCAL_DB_PASSWORD)"; DB_SSL=REQUIRED
  else
    local url rest hostport
    url="$(envget DB_URL)"
    [[ "$url" == jdbc:mysql://* ]] || die "DB_URL must look like jdbc:mysql://HOST:PORT/NAME?..."
    rest="${url#jdbc:mysql://}"
    hostport="${rest%%/*}"
    DB_NAME="${rest#*/}"; DB_NAME="${DB_NAME%%\?*}"
    DB_HOST="${hostport%%:*}"
    DB_PORT="${hostport##*:}"; [ "$DB_PORT" = "$hostport" ] && DB_PORT=3306
    DB_USER="$(envget DB_USERNAME)"; DB_PASS="$(envget DB_PASSWORD)"; DB_SSL=REQUIRED
  fi
  [ -n "$DB_PASS" ] || die "database password is empty in .env"
}

# Runs a MySQL client program (mysql or mysqldump) against the current db_target, on the compose network
# so the local "db" service resolves. The password goes in through the environment, never on a command line.
mysql_client() {
  local prog="$1"; shift
  docker run --rm -i --network "$NETWORK" -e MYSQL_PWD="$DB_PASS" "$MYSQL_IMAGE" \
    "$prog" -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" --ssl-mode="$DB_SSL" "$@"
}

# Writes a gzip'd, complete dump of the current db_target to $1 and checks it finished.
# --set-gtid-purged=OFF: Aiven uses GTIDs, and without it the import fails (ERROR 1840).
# --no-tablespaces: managed databases do not grant the PROCESS privilege that tablespace dumps need.
# The app has no stored routines or events, so those are not dumped (on Aiven they need extra privileges).
dump_db() {
  local out="$1"
  mysql_client mysqldump --single-transaction --quick --triggers --no-tablespaces \
    --set-gtid-purged=OFF --default-character-set=utf8mb4 "$DB_NAME" | gzip -c > "$out.part"
  gzip -t "$out.part" || die "dump is not a valid gzip file"
  zcat "$out.part" | tail -n 1 | grep -q "Dump completed" || die "dump did not finish (no 'Dump completed' line)"
  mv "$out.part" "$out"
}

# Prints "table count" for every table of the current db_target, sorted.
table_counts() {
  local tables t
  tables="$(mysql_client mysql -N -B -e "SELECT table_name FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_type='BASE TABLE' ORDER BY table_name" )"
  for t in $tables; do
    printf '%s %s\n' "$t" "$(mysql_client mysql -N -B -e "SELECT COUNT(*) FROM \`$DB_NAME\`.\`$t\`")"
  done
}

# Waits until the local MySQL container reports healthy.
wait_db() {
  local i id
  for i in $(seq 1 60); do
    id="$(compose ps -q db)"
    if [ -n "$id" ] && [ "$(docker inspect -f '{{.State.Health.Status}}' "$id")" = healthy ]; then return 0; fi
    sleep 5
  done
  die "local MySQL did not become healthy: docker compose logs db"
}

# Waits until the API answers through Caddy over HTTPS (proves the API, the database and the certificate).
wait_healthy() {
  local domain deadline
  domain="$(envget API_DOMAIN)"
  deadline=$(( $(date +%s) + ${1:-240} ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if curl -fsS -m 10 --resolve "$domain:443:127.0.0.1" "https://$domain/api/store/info" > /dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  return 1
}
