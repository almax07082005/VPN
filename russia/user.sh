#!/usr/bin/env bash
#
# Manage VLESS+Reality users on the Russia VM.
#
#   sudo ./user.sh add <name> [--note "…"]
#   sudo ./user.sh list
#   sudo ./user.sh show <name>
#   sudo ./user.sh remove <name>
#   sudo ./user.sh rotate <name>
#
# Source of truth: users/<name>.json — one file per user.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/.." && pwd)"
# shellcheck source=../scripts/common.sh
source "$REPO_ROOT/scripts/common.sh"

USERS_DIR="$HERE/users"
CONFIG_OUT="$HERE/config/config.json"
CONFIG_TMPL="$HERE/config/config.json.tmpl"
ENV_FILE="$HERE/.env"

usage() {
  cat <<EOF
Usage:
  sudo ./user.sh add     <name> [--note "…"]
  sudo ./user.sh list
  sudo ./user.sh show    <name>
  sudo ./user.sh remove  <name>
  sudo ./user.sh rotate  <name>
EOF
  exit 1
}

_load_env() {
  [[ -f $ENV_FILE ]] || die ".env not found — run bootstrap.sh first"
  set -o allexport; # shellcheck disable=SC1091
  source "$ENV_FILE"
  set +o allexport
  for v in RU_SERVER_IP RU_REALITY_PRIVKEY RU_REALITY_PUBKEY RU_SHORT_ID \
           NL_SERVER_IP NL_REALITY_PUBKEY NL_USER_UUID NL_SHORT_ID; do
    [[ -n ${!v:-} ]] || die ".env is missing $v — re-run bootstrap.sh"
  done
  export RU_REALITY_PRIVKEY RU_SHORT_ID \
         NL_SERVER_IP NL_REALITY_PUBKEY NL_USER_UUID NL_SHORT_ID
}

_valid_name() {
  [[ $1 =~ ^[a-zA-Z0-9_.-]+$ ]] \
    || die "invalid user name: '$1' (allowed: letters, digits, _ . -)"
}

_user_file()   { printf '%s/%s.json' "$USERS_DIR" "$1"; }
_user_exists() { [[ -f $(_user_file "$1") ]]; }

_build_users_json() {
  # Emits a compact JSON array suitable for inbounds[0].users.
  mkdir -p "$USERS_DIR"
  shopt -s nullglob
  local files=( "$USERS_DIR"/*.json )
  shopt -u nullglob
  if (( ${#files[@]} == 0 )); then
    printf '[]'
    return
  fi
  jq -cs 'map({name, uuid, flow: "xtls-rprx-vision"})' "${files[@]}"
}

_render_config() {
  require_cmd envsubst
  require_cmd jq
  local users_json
  users_json="$(_build_users_json)"
  RU_USERS_JSON="$users_json" \
    envsubst '${RU_USERS_JSON} ${RU_REALITY_PRIVKEY} ${RU_SHORT_ID} ${NL_SERVER_IP} ${NL_REALITY_PUBKEY} ${NL_USER_UUID} ${NL_SHORT_ID}' \
    < "$CONFIG_TMPL" > "$CONFIG_OUT.tmp"
  # Validate JSON before replacing the live file.
  jq empty "$CONFIG_OUT.tmp"
  mv "$CONFIG_OUT.tmp" "$CONFIG_OUT"
  chmod 600 "$CONFIG_OUT"
}

_apply() {
  # Render config, then either start the stack or hot-reload it.
  _render_config
  if (cd "$HERE" && docker compose ps --status running | grep -q sing-box); then
    (cd "$HERE" && reload_sing_box)
  else
    log_info "starting sing-box"
    (cd "$HERE" && docker compose up -d)
  fi
}

_vless_uri_for() {
  # _vless_uri_for <name> → echoes vless:// URI
  local name="$1" uuid
  uuid="$(jq -r .uuid "$(_user_file "$name")")"
  build_vless_uri \
    "$uuid" "$RU_SERVER_IP" 443 \
    "$RU_REALITY_PUBKEY" "$RU_SHORT_ID" \
    "$DEFAULT_COVERING_SITE" \
    "vpn2vpn-$name"
}

_print_client() {
  local name="$1" uri
  uri="$(_vless_uri_for "$name")"
  echo
  echo "=============== client profile: $name ==============="
  echo "$uri" | render_qr
  echo "$uri"
  echo "===================================================="
}

cmd_add() {
  local name="$1"; shift || true
  _valid_name "$name"
  local note=""
  while (($#)); do
    case "$1" in
      --note) note="$2"; shift 2;;
      *) die "unknown flag: $1";;
    esac
  done

  if _user_exists "$name"; then
    die "user '$name' already exists — use rotate to re-issue"
  fi

  local uuid created
  uuid="$(generate_uuid)"
  created="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  jq -n --arg n "$name" --arg u "$uuid" --arg c "$created" --arg note "$note" \
    '{name:$n, uuid:$u, created_at:$c, note:$note}' \
    > "$(_user_file "$name")"
  chmod 600 "$(_user_file "$name")"

  _apply
  log_ok "user '$name' added"
  _print_client "$name"
}

cmd_list() {
  mkdir -p "$USERS_DIR"
  shopt -s nullglob
  local files=( "$USERS_DIR"/*.json )
  shopt -u nullglob
  if (( ${#files[@]} == 0 )); then
    echo "(no users yet — run: sudo ./user.sh add <name>)"
    return
  fi
  printf '%-20s %-10s %-22s %s\n' NAME UUID_PREFIX CREATED_AT NOTE
  printf '%-20s %-10s %-22s %s\n' -------------------- ---------- ---------------------- ----
  for f in "${files[@]}"; do
    jq -r '[.name, (.uuid[0:8]), .created_at, (.note // "")] | @tsv' "$f" \
      | awk -F '\t' '{printf "%-20s %-10s %-22s %s\n", $1, $2, $3, $4}'
  done
}

cmd_show() {
  local name="$1"
  _valid_name "$name"
  _user_exists "$name" || die "no such user: $name"
  _print_client "$name"
}

cmd_remove() {
  local name="$1"
  _valid_name "$name"
  _user_exists "$name" || die "no such user: $name"
  rm -f "$(_user_file "$name")"
  _apply
  log_ok "user '$name' removed"
}

cmd_rotate() {
  local name="$1"
  _valid_name "$name"
  _user_exists "$name" || die "no such user: $name"
  local new_uuid
  new_uuid="$(generate_uuid)"
  local f; f="$(_user_file "$name")"
  jq --arg u "$new_uuid" '.uuid = $u' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
  chmod 600 "$f"
  _apply
  log_ok "user '$name' rotated — old client is now invalid"
  _print_client "$name"
}

main() {
  (($# > 0)) || usage
  require_root
  local sub="$1"; shift
  _load_env
  case "$sub" in
    add)     (($# >= 1)) || usage; cmd_add    "$@";;
    list)    cmd_list;;
    show)    (($# == 1)) || usage; cmd_show   "$1";;
    remove)  (($# == 1)) || usage; cmd_remove "$1";;
    rotate)  (($# == 1)) || usage; cmd_rotate "$1";;
    -h|--help) usage;;
    *) log_err "unknown subcommand: $sub"; usage;;
  esac
}

main "$@"
