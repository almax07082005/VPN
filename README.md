# VPN-over-VPN with Smart Routing

Two-hop VPN that lets a client in Russia reach the whole internet while keeping Russian services reachable on a Russian IP.

```
  Client ──VLESS+Reality──▶  Russia VM  ──▶ direct egress    ──▶ ozon.ru, vk.com, госуслуги.рф
  (Hiddify)                      │
                                 └── geosite match? ──no──▶ Netherlands VM ──▶ youtube.com, github.com, …
```

- Entry hop is **VLESS + Reality (Vision flow)** — survives Russian DPI by masquerading as TLS to `www.microsoft.com`.
- Russian VM decides per-connection (by sniffed SNI/Host) whether to egress direct or forward to the Netherlands.
- Only domains in the `geosite:category-ru` rule-set go direct. Everything else — including foreign services on Russian IPs and newly-banned sites — exits via Netherlands.

## Repository layout

```
VPN/
├── russia/                        # Russian VM
│   ├── bootstrap.sh               # one-shot installer
│   ├── user.sh                    # add/remove/rotate clients
│   ├── users/                     # per-user UUID records (treat as secrets)
│   ├── config/config.json.tmpl    # sing-box template
│   └── docker-compose.yml
├── netherlands/                   # Netherlands VM
│   ├── bootstrap.sh
│   ├── config/config.json.tmpl
│   └── docker-compose.yml
├── scripts/
│   ├── common.sh                  # shared bash helpers
│   └── verify.sh                  # post-deploy sanity checks
└── clients/
    └── README.md                  # Hiddify install walkthrough
```

## Deploy (order matters — Netherlands first)

Both VMs must be fresh Ubuntu 22.04+ or Debian 12+, with sudo.

### 1 — Netherlands VM

```bash
git clone <this-repo>.git vpn && cd vpn/netherlands
sudo bash bootstrap.sh
```

At the end it prints four values — save them; you'll paste them into the RU bootstrap:

- `NL_SERVER_IP`
- `NL_REALITY_PUBKEY`
- `NL_USER_UUID`
- `NL_SHORT_ID`

### 2 — Russia VM

```bash
git clone <this-repo>.git vpn && cd vpn/russia
sudo bash bootstrap.sh \
  --nl-ip       <NL_SERVER_IP> \
  --nl-pubkey   <NL_REALITY_PUBKEY> \
  --nl-uuid     <NL_USER_UUID> \
  --nl-short-id <NL_SHORT_ID>
```

When it finishes you'll see a QR code and a `vless://` URI for the `admin` user.

### 3 — Client

Install [Hiddify](https://hiddify.com/) on iOS / Android / macOS / Windows and scan the QR. See `clients/README.md` for per-platform notes.

## Managing users (terminal CLI)

All user management happens on the **Russia** VM via `./user.sh`:

```bash
cd /path/to/vpn/russia

sudo ./user.sh add alice --note "iPhone 15"   # creates user, prints vless:// + QR
sudo ./user.sh list                            # tabular view
sudo ./user.sh show alice                      # re-print QR
sudo ./user.sh rotate alice                    # new UUID, revokes old client
sudo ./user.sh remove alice                    # revoke entirely
```

Changes take effect within a few seconds — the script hot-reloads sing-box.

## Verify

With the tunnel active on your client:

```bash
bash scripts/verify.sh
```

Hits `api.ipify.org` (expects NL IP), `api.2ip.ru/geo.json` (expects RU IP), a DNS-leak probe, and runs basic CLI smoke tests over SSH. See the plan for the full 8-step list.

## Design notes

- **No `geoip-ru` rule.** Matching is domain-only. Russian IPs hosting a foreign (and likely blocked) service still exit via NL. Given how fast the RU block-list changes, direct egress should be opt-in per confirmed RU service, not by IP range.
- **DNS is split.** `.ru`-geosite domains resolve via `77.88.8.8` (Yandex); everything else resolves via Cloudflare DoH tunneled through the NL hop — no leaks, no timing side-channel.
- **One Reality keypair per server.** The keypair authenticates the server; per-user UUIDs authenticate users. Rotating a user is cheap (UUID swap). Rotating server keys requires re-issuing every client QR.
- **Rule-set auto-refreshes every 24 h** (sing-box default), so newly-tracked Russian services get classified without a redeploy.
