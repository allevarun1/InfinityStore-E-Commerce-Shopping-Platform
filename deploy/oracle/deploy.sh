#!/usr/bin/env bash
# Updates the server to the latest main and restarts the API, with automatic rollback.
#   ./deploy.sh           pull main; rebuild and restart only if the backend or this kit changed
#   ./deploy.sh --force   rebuild and restart even if nothing changed (first start, or after editing .env)
# Run by GitHub Actions on every push to main once CI passes (.github/workflows/deploy-oracle.yml), or by hand.
# The API is down for about a minute while the new version starts; the shop retries by itself meanwhile.
set -Eeuo pipefail

main() {
  # shellcheck source=lib.sh
  . "$(dirname "$(readlink -f "$0")")/lib.sh"
  local force="${1:-}"

  exec 9> /tmp/rekha-deploy.lock
  flock -n 9 || die "another deploy is running"

  local old new changed
  old="$(git -C "$REPO_DIR" rev-parse HEAD)"
  git -C "$REPO_DIR" fetch --quiet origin main
  new="$(git -C "$REPO_DIR" rev-parse origin/main)"

  if [ "$old" = "$new" ] && [ "$force" != --force ]; then
    log "already at ${new:0:7}, nothing to do"; return 0
  fi
  changed="$(git -C "$REPO_DIR" diff --name-only "$old" "$new" -- ecommerce-backend deploy/oracle | wc -l)"
  # .env and backups are git-ignored, so this never touches them.
  git -C "$REPO_DIR" reset --hard --quiet "$new"
  log "code: ${old:0:7} -> ${new:0:7}"

  if [ "$changed" -eq 0 ] && [ "$force" != --force ]; then
    log "only storefront/docs changed: API not restarted"; return 0
  fi

  # Keep the running image so a failed release can be undone in seconds.
  if docker image inspect rekha-api:latest > /dev/null 2>&1; then
    docker image tag rekha-api:latest rekha-api:rollback
  fi

  log "building the API image (a few minutes)"
  if ! compose build --pull api; then
    git -C "$REPO_DIR" reset --hard --quiet "$old"
    die "build failed: still running ${old:0:7}"
  fi

  log "starting"
  compose up -d --remove-orphans
  if wait_healthy 300; then
    log "healthy on ${new:0:7}"
    docker image prune -f > /dev/null
    return 0
  fi

  log "new version did not become healthy: rolling back to ${old:0:7}"
  compose logs --tail=80 api || true
  git -C "$REPO_DIR" reset --hard --quiet "$old"
  if docker image inspect rekha-api:rollback > /dev/null 2>&1; then
    docker image tag rekha-api:rollback rekha-api:latest
    compose up -d --no-build --remove-orphans
    wait_healthy 300 && log "rolled back, healthy on ${old:0:7}" || log "rollback did not become healthy either: check .env and 'docker compose logs api'"
  fi
  exit 1
}

# Everything runs inside main, which bash reads completely before starting: safe even though the
# git reset above rewrites this very file.
main "$@"
exit $?
