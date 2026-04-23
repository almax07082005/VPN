# shellcheck shell=bash
#
# Shared helpers baked into the vpn-russia and vpn-exit container images.
# Sourced by entrypoint.sh and the russia/vpn CLI — not meant to run directly.

set -o errexit
set -o nounset
set -o pipefail

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

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

# --- Secret generation ----------------------------------------------------

# Sets globals REALITY_PRIVKEY and REALITY_PUBKEY.
generate_reality_keypair() {
  local out
  out="$(sing-box generate reality-keypair)"
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
  local host="$1" port="$2"
  timeout 5 bash -c ">/dev/tcp/$host/$port" 2>/dev/null
}

# --- Template rendering ---------------------------------------------------

render_template() {
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
  qrencode -t ANSIUTF8 -m 1
}

# --- sing-box reload ------------------------------------------------------

# Signal the PID-1 supervisor to restart sing-box. Writes a sentinel so the
# supervisor knows this is a reload (vs. a real crash that should propagate).
reload_sing_box() {
  touch /var/lib/vpn/.reload
  kill -HUP 1
}
