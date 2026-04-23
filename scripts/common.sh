# shellcheck shell=bash
#
# Shared helpers sourced by bootstraps and user.sh.
# Not executable on its own — intended for `source "$SCRIPTS/common.sh"`.

set -o errexit
set -o nounset
set -o pipefail

# --- Pinned versions ------------------------------------------------------

readonly SING_BOX_IMAGE="ghcr.io/sagernet/sing-box:v1.10.3"
readonly DEFAULT_COVERING_SITE="www.microsoft.com"

# --- Logging --------------------------------------------------------------

_c_reset=$'\033[0m'
_c_blue=$'\033[1;34m'
_c_yellow=$'\033[1;33m'
_c_red=$'\033[1;31m'
_c_green=$'\033[1;32m'

log_info()  { printf '%s[info]%s %s\n'  "$_c_blue"   "$_c_reset" "$*"; }
log_ok()    { printf '%s[ ok ]%s %s\n'  "$_c_green"  "$_c_reset" "$*"; }
log_warn()  { printf '%s[warn]%s %s\n'  "$_c_yellow" "$_c_reset" "$*" >&2; }
log_err()   { printf '%s[err ]%s %s\n'  "$_c_red"    "$_c_reset" "$*" >&2; }
die()       { log_err "$*"; exit 1; }

# --- Preconditions --------------------------------------------------------

require_root() {
  [[ $EUID -eq 0 ]] || die "must be run as root (use sudo)"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

ensure_pkg() {
  # ensure_pkg <apt-package> [binary-name]
  local pkg="$1" bin="${2:-$1}"
  if ! command -v "$bin" >/dev/null 2>&1; then
    log_info "installing $pkg"
    DEBIAN_FRONTEND=noninteractive apt-get update -yq >/dev/null
    DEBIAN_FRONTEND=noninteractive apt-get install -yq "$pkg" >/dev/null
  fi
}

# --- Docker install (idempotent) ------------------------------------------

install_docker() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    log_ok "docker + compose already installed"
    return 0
  fi
  log_info "installing docker via get.docker.com"
  ensure_pkg curl
  curl -fsSL https://get.docker.com | sh
  systemctl enable --now docker >/dev/null
  # Compose plugin comes bundled with the get.docker.com script on Ubuntu/Debian.
  docker compose version >/dev/null 2>&1 \
    || die "docker compose plugin missing after install"
  log_ok "docker installed: $(docker --version)"
}

# --- Secret generation ----------------------------------------------------

# Sets globals REALITY_PRIVKEY and REALITY_PUBKEY.
generate_reality_keypair() {
  local out
  out="$(docker run --rm "$SING_BOX_IMAGE" generate reality-keypair)"
  REALITY_PRIVKEY="$(awk '/PrivateKey/ {print $2}' <<<"$out")"
  REALITY_PUBKEY="$(awk '/PublicKey/  {print $2}' <<<"$out")"
  [[ -n $REALITY_PRIVKEY && -n $REALITY_PUBKEY ]] \
    || die "failed to parse reality keypair from sing-box: $out"
}

generate_uuid() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen | tr '[:upper:]' '[:lower:]'
  else
    cat /proc/sys/kernel/random/uuid
  fi
}

generate_short_id() {
  # 8 hex chars — valid Reality short_id length 0..16.
  openssl rand -hex 4
}

# --- Network --------------------------------------------------------------

detect_public_ip() {
  local ip
  ip="$(curl -fsS --max-time 5 https://api.ipify.org || true)"
  [[ -n $ip ]] || ip="$(curl -fsS --max-time 5 https://ifconfig.me || true)"
  [[ -n $ip ]] || die "could not detect public IP"
  printf '%s' "$ip"
}

probe_tcp() {
  # probe_tcp <host> <port> — true if reachable within 5s.
  local host="$1" port="$2"
  timeout 5 bash -c ">/dev/tcp/$host/$port" 2>/dev/null
}

# --- Template rendering ---------------------------------------------------

render_template() {
  # render_template <template> <output>
  # Substitutes ${VAR} references using envsubst — vars must already be exported.
  local tmpl="$1" out="$2"
  require_cmd envsubst
  envsubst <"$tmpl" >"$out"
}

# --- Client URI -----------------------------------------------------------

# build_vless_uri <uuid> <host> <port> <reality_pubkey> <short_id> <sni> <label>
build_vless_uri() {
  local uuid="$1" host="$2" port="$3" pbk="$4" sid="$5" sni="$6" label="$7"
  local label_enc
  label_enc="$(printf '%s' "$label" | jq -sRr @uri)"
  printf 'vless://%s@%s:%s?encryption=none&security=reality&sni=%s&fp=chrome&pbk=%s&sid=%s&spx=%%2F&type=tcp&flow=xtls-rprx-vision#%s\n' \
    "$uuid" "$host" "$port" "$sni" "$pbk" "$sid" "$label_enc"
}

render_qr() {
  # Prints an ANSI QR for stdin text. Requires qrencode.
  ensure_pkg qrencode
  qrencode -t ANSIUTF8 -m 1
}

# --- sing-box control -----------------------------------------------------

reload_sing_box() {
  # Called from a directory that has a docker-compose.yml with service "sing-box".
  # sing-box doesn't reliably reload on SIGHUP — restart is fast (~200 ms).
  docker compose restart sing-box >/dev/null
  log_ok "sing-box restarted"
}

# --- Firewall -------------------------------------------------------------

open_port_443() {
  if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q 'Status: active'; then
    ufw allow 443/tcp >/dev/null
    log_ok "ufw: opened 443/tcp"
  fi
}
