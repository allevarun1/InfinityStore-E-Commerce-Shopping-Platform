#!/usr/bin/env bash
# Nightly database backup (cron, set up by setup-server.sh). Works in both phases: it dumps whichever database the
# API is using (Aiven in Phase A, MySQL on this server in Phase B).
#  - Keeps 14 days of dumps in /opt/rekha/backups on the server.
#  - Copies each dump OFF the server to Oracle Object Storage (BACKUP_S3_* in .env), keeping 30 days there.
#  - Optional BACKUP_PING_URL (healthchecks.io): you get an email if a backup fails or stops running.
set -Eeuo pipefail

main() {
  # shellcheck source=lib.sh
  . "$(dirname "$(readlink -f "$0")")/lib.sh"
  local ping file
  ping="$(envget BACKUP_PING_URL)"
  trap '[ -n "$ping" ] && curl -fsS -m 10 "$ping/fail" > /dev/null 2>&1; log "backup FAILED"' ERR

  mkdir -p "$BACKUP_DIR"
  db_target auto
  file="$BACKUP_DIR/rekha-$(date +%F-%H%M).sql.gz"
  log "dumping $DB_NAME from $DB_HOST"
  dump_db "$file"
  log "saved $(du -h "$file" | cut -f1) to $file"
  find "$BACKUP_DIR" -name 'rekha-*.sql.gz' -mtime +14 -delete

  local endpoint
  endpoint="$(envget BACKUP_S3_ENDPOINT)"
  if [ -n "$endpoint" ]; then
    local bucket; bucket="$(envget BACKUP_S3_BUCKET)"
    rclone() {
      docker run --rm -v "$BACKUP_DIR":/data:ro \
        -e RCLONE_CONFIG_OCI_TYPE=s3 -e RCLONE_CONFIG_OCI_PROVIDER=Other \
        -e RCLONE_CONFIG_OCI_ENDPOINT="$endpoint" \
        -e RCLONE_CONFIG_OCI_REGION="$(envget BACKUP_S3_REGION)" \
        -e RCLONE_CONFIG_OCI_ACCESS_KEY_ID="$(envget BACKUP_S3_ACCESS_KEY)" \
        -e RCLONE_CONFIG_OCI_SECRET_ACCESS_KEY="$(envget BACKUP_S3_SECRET_KEY)" \
        -e RCLONE_CONFIG_OCI_FORCE_PATH_STYLE=true -e RCLONE_CONFIG_OCI_NO_CHECK_BUCKET=true \
        rclone/rclone:1.68.2 "$@"
    }
    rclone copy "/data/$(basename "$file")" "oci:$bucket/db/"
    rclone delete --min-age 30d "oci:$bucket/db/"
    log "copied off the server to bucket $bucket"
  else
    log "WARNING: BACKUP_S3_* not set: this backup exists on the server only"
  fi

  [ -n "$ping" ] && curl -fsS -m 10 "$ping" > /dev/null 2>&1 || true
  log "backup done"
}

main "$@"
