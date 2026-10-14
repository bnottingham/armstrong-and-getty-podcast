#!/bin/zsh
# Start/stop/status for the self-hosted OpenSEO instance that backs the
# `openseo` MCP server in this repo's .mcp.json. See tools/seo/README.md.
#
# The OpenSEO checkout is a sibling of this repo, NOT part of it, and is shared
# with the Renew repo (same instance, same port):
#   /Users/brett/Documents/github/open-seo
#
# DataForSEO credentials live in the macOS Keychain (service "dataforseo-api",
# account = API login, password = plain API password). `sync-key` derives
# OpenSEO's DATAFORSEO_API_KEY (single-layer base64 of login:password) from it,
# so a fresh clone of open-seo never loses the key again.

set -euo pipefail

OPENSEO_DIR="${OPENSEO_DIR:-$(cd "$(dirname "$0")/../../.." && pwd)/open-seo}"
PORT="${OPENSEO_PORT:-3001}"
LOG="$OPENSEO_DIR/.logs/dev-server.log"
KEYCHAIN_SERVICE="dataforseo-api"

running_pid() {
  # vite dev serves the app; match the process listening on the port.
  lsof -ti tcp:"$PORT" -s tcp:LISTEN 2>/dev/null | head -1 || true
}

keychain_login() {
  security find-generic-password -s "$KEYCHAIN_SERVICE" 2>/dev/null \
    | sed -n 's/.*"acct"<blob>="\(.*\)"/\1/p'
}

start() {
  if [[ -n "$(running_pid)" ]]; then
    echo "OpenSEO already running on port $PORT (pid $(running_pid))"
    return 0
  fi
  mkdir -p "$OPENSEO_DIR/.logs"
  (cd "$OPENSEO_DIR" && nohup pnpm run dev > "$LOG" 2>&1 &)
  echo "Starting OpenSEO from $OPENSEO_DIR ..."
  for _ in {1..45}; do
    sleep 1
    if curl -sf "http://localhost:$PORT/api/health" > /dev/null 2>&1; then
      echo "OpenSEO up: http://localhost:$PORT (health: /api/health, MCP: /mcp)"
      return 0
    fi
  done
  echo "OpenSEO did not become healthy in 45s — check $LOG" >&2
  return 1
}

stop() {
  local pid
  pid="$(running_pid)"
  if [[ -z "$pid" ]]; then
    echo "OpenSEO is not running on port $PORT"
    return 0
  fi
  kill "$pid"
  for _ in {1..10}; do
    [[ -z "$(running_pid)" ]] && break
    sleep 1
  done
  echo "Stopped OpenSEO (pid $pid)"
}

sync_key() {
  local login env_file tmp
  login="$(keychain_login)"
  if [[ -z "$login" ]]; then
    echo "No Keychain item '$KEYCHAIN_SERVICE' — see tools/seo/README.md (one-time setup)." >&2
    return 1
  fi
  env_file="$OPENSEO_DIR/.env.local"
  touch "$env_file"
  tmp="$(mktemp "$OPENSEO_DIR/.env.local.XXXXXX")"
  grep -v '^DATAFORSEO_API_KEY=' "$env_file" > "$tmp" || true
  # Single-layer base64 of login:password. Never echo the value.
  printf 'DATAFORSEO_API_KEY=%s\n' \
    "$(printf '%s:%s' "$login" "$(security find-generic-password -s "$KEYCHAIN_SERVICE" -w)" | base64)" \
    >> "$tmp"
  chmod 600 "$tmp"
  mv "$tmp" "$env_file"
  echo "Wrote DATAFORSEO_API_KEY for $login to $env_file (restart OpenSEO to pick it up)"
}

case "${1:-status}" in
  start) start ;;
  stop) stop ;;
  restart) stop; start ;;
  sync-key) sync_key ;;
  status)
    pid="$(running_pid)"
    if [[ -n "$pid" ]]; then
      echo "OpenSEO running on port $PORT (pid $pid)"
      curl -s "http://localhost:$PORT/api/health" | head -c 600; echo
    else
      echo "OpenSEO is not running on port $PORT"
    fi
    ;;
  logs) tail -n "${2:-50}" "$LOG" ;;
  *)
    echo "Usage: $0 {start|stop|restart|status|sync-key|logs [n]}" >&2
    exit 2
    ;;
esac
