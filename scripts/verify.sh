#!/usr/bin/env bash
#
# Post-deploy sanity checks. Run this from your laptop.
#
# Ports/SSH checks can run any time. Tunnel checks assume Hiddify (or another
# VLESS+Reality client) is connected to the Russia VM using the QR printed by
# bootstrap.sh / user.sh.
#
# Usage:
#   bash scripts/verify.sh --ru-ip 1.2.3.4 --nl-ip 5.6.7.8 \
#        [--ru-ssh root@ru.example] [--nl-ssh root@nl.example]

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "$HERE/common.sh"

RU_IP=""
NL_IP=""
RU_SSH=""
NL_SSH=""

while (($#)); do
  case "$1" in
    --ru-ip)   RU_IP="$2";  shift 2;;
    --nl-ip)   NL_IP="$2";  shift 2;;
    --ru-ssh)  RU_SSH="$2"; shift 2;;
    --nl-ssh)  NL_SSH="$2"; shift 2;;
    -h|--help)
      sed -n '3,12p' "$0"; exit 0;;
    *) log_err "unknown arg: $1"; exit 1;;
  esac
done

[[ -n $RU_IP && -n $NL_IP ]] || die "--ru-ip and --nl-ip are required"

PASS=0
FAIL=0
check() {
  local name="$1"; shift
  if "$@"; then
    log_ok "$name"; ((PASS++))
  else
    log_err "$name"; ((FAIL++))
  fi
}

# --- 1. Port reachability -------------------------------------------------
echo
log_info "--- port reachability ---"
check "RU :443 open" probe_tcp "$RU_IP" 443
check "NL :443 open" probe_tcp "$NL_IP" 443

# --- 2. SSH + container liveness ------------------------------------------
if [[ -n $RU_SSH ]]; then
  echo
  log_info "--- Russia VM container liveness ---"
  check "RU sing-box running" \
    bash -c "ssh -o StrictHostKeyChecking=accept-new -n '$RU_SSH' \
             'docker compose -f /root/vpn/russia/docker-compose.yml ps --status running | grep -q sing-box \
              || docker ps --filter name=sing-box --filter status=running --format {{.Names}} | grep -q sing-box'"
fi
if [[ -n $NL_SSH ]]; then
  echo
  log_info "--- Netherlands VM container liveness ---"
  check "NL sing-box running" \
    bash -c "ssh -o StrictHostKeyChecking=accept-new -n '$NL_SSH' \
             'docker ps --filter name=sing-box --filter status=running --format {{.Names}} | grep -q sing-box'"
fi

# --- 3. Tunnel smoke tests ------------------------------------------------
#
# These require the laptop to be actively connected to the tunnel via
# Hiddify (or another VLESS+Reality client). We can't dial through the
# tunnel from this script directly — rely on the OS-level VPN state.
echo
log_info "--- tunnel smoke tests (requires Hiddify connected) ---"

EGRESS_IP="$(curl -fsS --max-time 8 https://api.ipify.org || true)"
if [[ -z $EGRESS_IP ]]; then
  log_err "could not reach api.ipify.org — tunnel may be down"
  ((FAIL++))
else
  if [[ $EGRESS_IP == "$NL_IP" ]]; then
    log_ok "general egress IP = $EGRESS_IP (matches NL — correct)"; ((PASS++))
  elif [[ $EGRESS_IP == "$RU_IP" ]]; then
    log_err "general egress IP = $EGRESS_IP (matches RU — routing is NOT forwarding to NL)"
    ((FAIL++))
  else
    log_warn "general egress IP = $EGRESS_IP (neither RU nor NL — tunnel may be disconnected)"
    ((FAIL++))
  fi
fi

RU_EGRESS_JSON="$(curl -fsS --max-time 8 'https://api.2ip.ru/geo.json' || true)"
if [[ -z $RU_EGRESS_JSON ]]; then
  log_warn "could not reach api.2ip.ru — skipping RU-direct probe"
else
  RU_EGRESS_IP="$(echo "$RU_EGRESS_JSON" | jq -r .ip 2>/dev/null || true)"
  RU_COUNTRY="$(echo "$RU_EGRESS_JSON" | jq -r .country_code 2>/dev/null || true)"
  if [[ $RU_EGRESS_IP == "$RU_IP" ]]; then
    log_ok "RU-direct: egress to api.2ip.ru is $RU_EGRESS_IP (RU VM, country=$RU_COUNTRY) — correct"; ((PASS++))
  else
    log_err "RU-direct: api.2ip.ru saw egress $RU_EGRESS_IP — expected $RU_IP. geosite rule is not matching."
    ((FAIL++))
  fi
fi

# --- 4. Manual checks reminder --------------------------------------------
cat <<EOF

Manual checks to run in a browser (tunnel still connected):
  • https://ifconfig.me           — expect NL IP
  • https://2ip.ru                — expect RU IP
  • https://dnsleaktest.com       — expect only Cloudflare/Yandex resolvers
  • https://youtube.com           — should play
  • https://vk.com                — should not warn about a "foreign IP"
  • https://www.gosuslugi.ru      — should accept login

On the Russia VM:
  sudo docker compose logs --tail 100 sing-box
  sudo tcpdump -i eth0 -n 'port 443 and host <your-home-ip>'   # TLS looks like microsoft.com

EOF

echo
log_info "summary: $PASS passed, $FAIL failed"
[[ $FAIL -eq 0 ]]
