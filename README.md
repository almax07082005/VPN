# VPN-over-VPN with Smart Routing

A two-hop VPN for users in Russia. A single connection that:

- **Keeps Russian services reachable on a Russian IP** (банк, госуслуги, ozon, VK, Yandex — they see you as a Russian user).
- **Tunnels everything else through a VM abroad** (YouTube, ChatGPT, GitHub — they see you as a foreign user).
- **Survives Russian DPI** by masquerading as plain TLS to `www.microsoft.com` via VLESS+Reality+Vision.

The routing decision is made **server-side per connection** from the sniffed hostname — clients don't need to configure anything.

```
  Client ──VLESS+Reality──▶  entry VM (Russia)  ──▶ direct egress       ──▶ ozon.ru, vk.com, госуслуги.рф
  (Streisand /                    │                                          (destination sees RU IP)
   Hiddify)                       │
                                  └── geosite match? ──no──▶ exit VM (abroad) ──▶ youtube.com, github.com, …
                                                                                   (destination sees exit IP)
```

- Entry hop: **VLESS + Reality + Vision** — looks like a TLS handshake to `www.microsoft.com`.
- Russia VM sniffs SNI/Host, matches it against `geosite:category-ru`, and either egresses direct or forwards to the exit VM over another VLESS+Reality tunnel.
- **No `geoip-ru` rule.** Matching is domain-only, so foreign services that happen to be hosted on Russian IPs still exit abroad (safer given how fast the RU block-list changes).

## Repository layout

```
VPN/
├── docker/
│   ├── common.sh                  # shared helpers baked into both images
│   └── vpn-supervisor             # PID-1 supervisor with SIGHUP reload
├── russia/                        # entry hop (deploy inside Russia)
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── entrypoint.sh              # first-boot logic: keys, .env, admin user
│   ├── vpn                        # in-container CLI: add/list/show/remove/rotate
│   └── config.json.tmpl           # sing-box template (smart routing lives here)
├── exit/                          # egress hop (deploy abroad — Hetzner, Vultr, DigitalOcean, anywhere that's not RU)
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── entrypoint.sh
│   └── config.json.tmpl
└── scripts/
    └── verify.sh                  # post-deploy sanity checks (runs from your laptop)
```

## Deploy

Prereqs per VM: any Linux distro with Docker Engine + the compose plugin, public IPv4 on port 443. That's it — no apt, no package installs, no host-side scripts.

### Order matters — deploy the exit VM first

#### 1. Exit VM (anywhere outside Russia)

```bash
git clone https://github.com/almax07082005/VPN.git && cd VPN/exit
docker compose up -d --build
docker compose logs vpn-exit          # grab the four handoff values
```

The container generates the Reality keypair on first boot, renders the sing-box config into the named volume, and starts listening on `:443`. The log banner prints four values — **save them**; the Russia container needs them to authenticate its tunnel:

- `EXIT_IP`
- `EXIT_PUBKEY`
- `EXIT_UUID`
- `EXIT_SHORT_ID`

They're also persisted to `deployment-info.txt` inside the container volume — `docker compose exec vpn-exit cat /var/lib/vpn/deployment-info.txt` to re-print.

#### 2. Russia VM

Create `russia/.env` with the four values:

```
EXIT_IP=<EXIT_IP>
EXIT_PUBKEY=<EXIT_PUBKEY>
EXIT_UUID=<EXIT_UUID>
EXIT_SHORT_ID=<EXIT_SHORT_ID>
```

Then:

```bash
git clone https://github.com/almax07082005/VPN.git && cd VPN/russia
# populate .env as above
docker compose up -d --build
docker compose logs vpn-russia        # scan the QR code and save the vless:// URI
```

The container probes reachability to the exit VM, generates its own Reality keypair, writes `/var/lib/vpn/.env` to the volume, creates the first user (`admin` by default — override with `INITIAL_USER=alice` in `.env`), and renders the routing config. The log output shows a QR code and a `vless://` URI — paste the URI into Hiddify/Streisand, or scan the QR with the phone app.

#### Re-running is safe

`docker compose up -d --build` is idempotent. Keys are generated only on first boot; every run after that just re-renders the config from the persisted state in the named volume and restarts the container. To start completely fresh, delete the volume: `docker compose down -v`.

## Client setup

Pick your platform. Every client imports the same `vless://` URI produced by the `vpn` CLI inside the Russia container. Nothing is platform-specific on the server side.

### iPhone / iPad — Streisand

Hiddify's iOS dev builds have a known bug that crashes their internal helper during profile import. **Streisand is the stable recommendation on iOS.**

1. Install **Streisand** from the App Store:
   <https://apps.apple.com/app/streisand/id6450534064>
2. Open Streisand → tap **+** in the top-right → pick either:
   - **Scan QR Code** — scan the QR from `docker compose logs vpn-russia` (or `docker compose exec vpn-russia vpn show <name>`).
   - **Paste from Clipboard** — copy the `vless://…` URI and paste it.
3. The profile appears in the list. Tap it once to select it, then tap the large **play/power button** at the bottom to connect. iOS will prompt once to install a VPN profile — approve it.
4. Verify it works:
   - In Safari, open <https://ifconfig.me> → should show the **exit VM's IP**.
   - Open <https://2ip.ru> → should show the **Russia VM's IP** (confirming the split-routing is active).

Alternate iOS client: the latest App Store build of **Hiddify** (not a `4.x dev` release) also works — same import flow.

### Android — Hiddify Next

1. Install **Hiddify Next** from one of:
   - Google Play: <https://play.google.com/store/apps/details?id=app.hiddify.com>
   - GitHub Releases APK: <https://github.com/hiddify/hiddify-next/releases/latest>
   - F-Droid: <https://f-droid.org/en/packages/app.hiddify.com/>
2. Open Hiddify → **+** → **Scan QR Code** or **Add from Clipboard** with the `vless://` URI.
3. Select the imported profile, tap **Connect**. Approve the Android VPN permission prompt.
4. (Optional — Android-only feature) Settings → **Per-App Proxy Mode** if you want to exclude specific apps from the tunnel.

### macOS (Apple Silicon) — Streisand

Same app as iOS, same App Store link — Streisand runs natively on Apple Silicon Macs (M1 / M2 / M3 / M4).

1. Install **Streisand** from the Mac App Store:
   <https://apps.apple.com/app/streisand/id6450534064>
2. Open Streisand → tap **+** → **Paste from Clipboard** (copy the `vless://` URI first) or **Scan QR Code**.
3. Select the profile, click the power button to connect. macOS will prompt once to install a VPN extension — approve it.

Intel Macs can't install Streisand. Use **Hiddify** instead: `brew install --cask hiddify`, or the DMG from <https://hiddify.com/>. Same paste-and-connect flow.

### Windows — Hiddify

1. Download Hiddify from <https://hiddify.com/> (Windows `.msi`) or the latest MSI at <https://github.com/hiddify/hiddify-next/releases/latest>.
2. Run the installer.
3. Copy the `vless://…` URI. Open Hiddify → **Add profile → Paste link** → **Connect**.
4. Windows will prompt for admin rights to create the VPN adapter — approve once.

## Managing users

All user management runs against the `vpn-russia` container. SSH into the Russia VM and:

```bash
cd ~/VPN/russia

docker compose exec vpn-russia vpn add alice --note "iPhone 15"   # new user; prints vless:// URI + QR
docker compose exec vpn-russia vpn list                           # tabular view of all users
docker compose exec vpn-russia vpn show alice                     # re-print URI + QR for an existing user
docker compose exec vpn-russia vpn rotate alice                   # new UUID, old client immediately revoked
docker compose exec vpn-russia vpn remove alice                   # full revoke
```

Each command re-renders `/etc/sing-box/config.json` inside the container from the per-user JSONs in `/var/lib/vpn/users/` and signals the supervisor to restart sing-box (~200 ms). The container itself doesn't restart — existing TCP flows from other clients are the only thing disrupted.

User files look like:

```json
{ "name": "alice", "uuid": "…", "created_at": "2026-04-23T07:54:34Z", "note": "iPhone 15" }
```

There is **no admin-vs-normal-user distinction** — every entry gets the same routing, same bandwidth, same everything. Administrative control is who has shell access to the Russia VM.

## Configuration

The most useful knobs live in the two config templates, which are baked into each image at build time:

| What | Where | Default |
|---|---|---|
| Covering site (the SNI that DPI sees) | both `config.json.tmpl` + in `vless://` URIs | `www.microsoft.com` |
| Russian DNS resolver | `russia/config.json.tmpl` | `77.88.8.8` (Yandex) |
| Foreign DNS resolver (over tunnel) | `russia/config.json.tmpl` | Cloudflare `https://1.1.1.1/dns-query` |
| Rule-set for RU-direct routing | `russia/config.json.tmpl` | `geosite-category-ru` (auto-updates every 24h) |
| Listen port | both `config.json.tmpl` | `443` |
| sing-box version | both `Dockerfile` (`FROM` line) | `ghcr.io/sagernet/sing-box:v1.10.3` |

To apply changes:

- **Template** (`config.json.tmpl`): edit, then `docker compose up -d --build` to rebuild the image and restart the container. The rebuilt entrypoint re-renders the config from the new template on startup.
- **Covering site**: changing it requires re-issuing every client profile (the SNI is baked into each `vless://` URI). Plan for downtime if clients are live.
- **Port**: also requires updating every client profile; don't unless `:443` is actually being blocked. Change the `ports:` line in the compose file and the `listen_port` in the template together.

Secrets generated at first boot live in the named volume (`vpn-russia-data` / `vpn-exit-data`) at `/var/lib/vpn/.env` (mode 600). Don't delete the volumes casually — losing the keys means regenerating them and re-issuing every client profile. Back up with `docker run --rm -v vpn-russia-data:/data -v "$PWD":/backup alpine tar czf /backup/vpn-russia-data.tgz -C /data .`.

## Verify

With a client connected:

```bash
# From your laptop (or any host with the tunnel active):
bash scripts/verify.sh --ru-ip <RU_SERVER_IP> --exit-ip <EXIT_SERVER_IP>
```

The script checks port reachability, queries `api.ipify.org` (expects exit VM IP), queries `api.2ip.ru/geo.json` (expects Russia VM IP — confirms the split works), and prints a list of manual browser checks.

Manual smoke test:
- <https://ifconfig.me> → exit VM IP
- <https://2ip.ru> → Russia VM IP
- <https://youtube.com>, <https://chatgpt.com> → load normally
- <https://vk.com>, <https://www.gosuslugi.ru> → load without "foreign IP" warnings
- <https://dnsleaktest.com> → only Cloudflare and/or Yandex resolvers visible

## Troubleshooting

| Symptom | First thing to check |
|---|---|
| Client "connects" but nothing loads | `ssh <russia-vm> "docker logs --tail 50 vpn-russia"` |
| Everything loads but `2ip.ru` shows the exit IP | Russia-direct routing isn't matching. Logs will show the `geosite-category-ru` rule-set download; if it failed, check outbound connectivity from Russia VM to GitHub. |
| `vk.com` loads but YouTube fails | Exit leg is down. `ssh <exit-vm> "docker ps --filter name=vpn-exit"` and check `:443` is open. |
| iOS Hiddify "Failed to add profile" with localhost error | You're on a Hiddify `dev` build. Install Streisand (see above) or an App Store stable Hiddify release. |
| Keeps disconnecting on mobile data | Some carriers throttle long-lived TLS flows. Try a different SIM, or switch the covering site from `www.microsoft.com` to `dl.google.com` in both templates and rebuild. |
| SNI itself is being DPI-blocked | Rotate the covering site in both templates. You'll have to re-issue every client URI (the SNI is embedded in each one). |

## Uninstall / teardown

Everything the app creates lives inside Docker or the cloned repo — no host-level packages, no firewall rules, no systemd units. Teardown only reverses those.

### Exit VM

```bash
ssh <exit-vm>
cd ~/VPN/exit
docker compose down -v    # stop vpn-exit, drop vpn-exit-data (wipes keys)
rm -rf ~/VPN
```

### Russia VM

```bash
ssh <russia-vm>
cd ~/VPN/russia
docker compose down -v                          # stop vpn-russia, drop vpn-russia-data
docker network rm vpn-net 2>/dev/null || true   # only if no sibling containers still use it
rm -rf ~/VPN
```

### Optional — reclaim disk

```bash
docker image rm vpn-russia:local vpn-exit:local ghcr.io/sagernet/sing-box:v1.10.3 2>/dev/null || true
```

### Keep the container, wipe just the state

Regenerate keys without rebuilding the image:

```bash
docker compose down -v    # drops only the named volume
docker compose up -d      # first-boot logic runs again on the fresh volume
```

### Clients

Delete the profile from Streisand / Hiddify on each device. That stops that device from connecting, but the UUID remains valid server-side until you also run `docker compose exec vpn-russia vpn remove <name>` (or tear down the Russia container entirely).

## Design notes

- **No `geoip-ru` rule.** Matching is domain-only. Russian IPs hosting a foreign (likely blocked) service still egress via the exit VM. Given how fast the RU block-list changes, direct egress should be opt-in per confirmed RU service, not by IP range.
- **DNS is split.** `.ru`-geosite domains resolve via `77.88.8.8` (Yandex); everything else resolves via Cloudflare DoH tunneled through the exit hop. No leaks, no timing side-channel.
- **One Reality keypair per server.** The keypair authenticates the server; per-user UUIDs authenticate users. Rotating a user is cheap (UUID swap, one `vpn rotate`). Rotating server keys requires re-issuing every client profile.
- **Rule-set auto-refreshes every 24h** (sing-box default) so new Russian services get classified without a redeploy.
- **VLESS+Reality+Vision** is the Russia-side entry protocol because it currently survives Russian DPI best of anything mainstream. If this ever stops working, the fallback path is AmneziaWG (obfuscated WireGuard) as a second inbound — not built yet, but a straightforward extension.
