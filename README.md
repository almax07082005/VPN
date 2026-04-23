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
├── russia/                        # entry VM (deploy inside Russia)
│   ├── bootstrap.sh               # one-shot installer
│   ├── user.sh                    # add / list / show / remove / rotate clients
│   ├── users/                     # per-user UUID records (secrets)
│   ├── config/config.json.tmpl    # sing-box template (smart routing lives here)
│   └── docker-compose.yml
├── exit/                          # exit VM (deploy abroad — Hetzner, Vultr, DigitalOcean, anywhere that's not RU)
│   ├── bootstrap.sh
│   ├── config/config.json.tmpl
│   └── docker-compose.yml
├── scripts/
│   ├── common.sh                  # shared bash helpers
│   └── verify.sh                  # post-deploy sanity checks
└── clients/
    └── README.md                  # platform-specific troubleshooting reference
```

## Deploy

Prereqs per VM: fresh Ubuntu 22.04+ / Debian 12+, root or sudo, public IPv4 on port 443.

### Order matters — deploy the exit VM first

#### 1. Exit VM (anywhere outside Russia)

```bash
git clone https://github.com/almax07082005/VPN.git && cd VPN/exit
sudo bash bootstrap.sh
```

The script installs Docker, generates the Reality keypair, renders the sing-box config, and brings up the container. When it finishes it prints four values; **save them** — the Russia bootstrap needs them:

- `EXIT_SERVER_IP`
- `EXIT_REALITY_PUBKEY`
- `EXIT_USER_UUID`
- `EXIT_SHORT_ID`

They're also written to `exit/deployment-info.txt` (mode 600, gitignored).

#### 2. Russia VM

```bash
git clone https://github.com/almax07082005/VPN.git && cd VPN/russia
sudo bash bootstrap.sh \
  --exit-ip       <EXIT_SERVER_IP> \
  --exit-pubkey   <EXIT_REALITY_PUBKEY> \
  --exit-uuid     <EXIT_USER_UUID> \
  --exit-short-id <EXIT_SHORT_ID>
```

The script verifies reachability to the exit VM, generates its own Reality keypair, renders the routing config, starts the container, and then creates the first user (`admin`) — printing a QR code and a `vless://` URI in the terminal.

#### Both scripts are idempotent

Re-running either bootstrap refreshes the config and restarts sing-box; it does not regenerate server keys unless you delete that VM's `.env` first.

## Client setup

Pick your platform. Every client imports the same `vless://` URI produced by `user.sh`. Nothing is platform-specific on the server side.

### iPhone / iPad — Streisand

Hiddify's iOS dev builds have a known bug that crashes their internal helper during profile import. **Streisand is the stable recommendation on iOS.**

1. Install **Streisand** from the App Store:
   <https://apps.apple.com/app/streisand/id6450534064>
2. Open Streisand → tap **+** in the top-right → pick either:
   - **Scan QR Code** — scan the QR printed in the terminal by `bootstrap.sh` or `user.sh show <name>`.
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

See `clients/README.md` for additional troubleshooting notes and platform quirks.

## Managing users

All user management runs on the **Russia VM** via `russia/user.sh`. SSH in and:

```bash
cd ~/VPN/russia

sudo ./user.sh add alice --note "iPhone 15"   # new user; prints vless:// URI + QR
sudo ./user.sh list                           # tabular view of all users
sudo ./user.sh show alice                     # re-print URI + QR for an existing user
sudo ./user.sh rotate alice                   # new UUID, old client immediately revoked
sudo ./user.sh remove alice                   # full revoke
```

Each command re-renders `russia/config/config.json` from the users in `russia/users/*.json` and restarts the sing-box container (~200 ms). Every mutation takes effect in a few seconds.

User files look like:

```json
{ "name": "alice", "uuid": "…", "created_at": "2026-04-23T07:54:34Z", "note": "iPhone 15" }
```

There is **no admin-vs-normal-user distinction** — every entry gets the same routing, same bandwidth, same everything. Administrative control is who has SSH to the Russia VM.

## Configuration

The most useful knobs live in the two config templates:

| What | Where | Default |
|---|---|---|
| Covering site (the SNI that DPI sees) | both `config.json.tmpl` + in `vless://` URIs | `www.microsoft.com` |
| Russian DNS resolver | `russia/config/config.json.tmpl` | `77.88.8.8` (Yandex) |
| Foreign DNS resolver (over tunnel) | `russia/config/config.json.tmpl` | Cloudflare `https://1.1.1.1/dns-query` |
| Rule-set for RU-direct routing | `russia/config/config.json.tmpl` | `geosite-category-ru` (auto-updates every 24h) |
| Listen port | both `config.json.tmpl` | `443` |
| sing-box version | both `docker-compose.yml` | `ghcr.io/sagernet/sing-box:v1.10.3` |

To apply changes:

- **Template** (`config.json.tmpl`): edit, then re-run the VM's `bootstrap.sh` (or, on the Russia VM, any `user.sh` command will also re-render).
- **Covering site**: changing it requires re-issuing every client profile (the SNI is baked into each `vless://` URI). Plan for downtime if clients are live.
- **Port**: also requires updating every client profile; do not unless `:443` is actually being blocked.

Secrets generated at bootstrap live in `<vm-dir>/.env` (mode 600, gitignored). Keep these files. Losing them means regenerating keys and re-issuing every profile.

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
| Client "connects" but nothing loads | `ssh <russia-vm> "cd ~/VPN/russia && docker compose logs --tail 50 sing-box"` |
| Everything loads but `2ip.ru` shows the exit IP | Russia-direct routing isn't matching. Logs will show the `geosite-category-ru` rule-set download; if it failed, check outbound connectivity from Russia VM to GitHub. |
| `vk.com` loads but YouTube fails | Exit leg is down. `ssh <exit-vm> "cd ~/VPN/exit && docker compose ps"` and check `:443` is open. |
| iOS Hiddify "Failed to add profile" with localhost error | You're on a Hiddify `dev` build. Install Streisand (see above) or an App Store stable Hiddify release. |
| Keeps disconnecting on mobile data | Some carriers throttle long-lived TLS flows. Try a different SIM, or switch the covering site from `www.microsoft.com` to `dl.google.com` in both templates and re-bootstrap. |
| SNI itself is being DPI-blocked | Rotate the covering site in both templates. You'll have to re-issue every client URI (the SNI is embedded in each one). |

## Uninstall / teardown

Port `:443` on each VM is exposed by two things:

1. **sing-box listening on `:443`** (via `network_mode: host` — no Docker port mapping, the process binds the host's socket directly). Stopping the container closes the listener; at that point nothing on the VM answers on `:443` regardless of any firewall state.
2. **The inbound allow rule** — on most cloud VMs (Hetzner, Vultr, DO, AWS, Beget, …) this lives in the **provider's cloud firewall panel**, not in local `ufw`. The bootstrap scripts never touch cloud firewalls. If `ufw` happens to be active, `open_port_443` also adds a local allow rule there; if `ufw` is inactive (the default on most cloud images — including the hetzner and beget2 VMs here), it's a no-op.

So a complete teardown is: stop the container → remove the files → **remove the inbound `:443` rule in your cloud provider's firewall UI** → (optionally) remove the ufw rule.

### Exit VM

```bash
ssh <exit-vm>
cd ~/VPN/exit && sudo docker compose down -v                # stop sing-box, close :443 on the host
sudo rm -rf ~/VPN                                           # repo + .env + deployment-info.txt + rendered config
sudo docker image rm ghcr.io/sagernet/sing-box:v1.10.3 2>/dev/null || true
# belt-and-suspenders — only does anything if ufw was active:
command -v ufw >/dev/null && sudo ufw delete allow 443/tcp 2>/dev/null || true
```

Then in the provider's web console, delete the inbound `:443` rule:
- **Hetzner** → Cloud Console → Firewalls → (your firewall) → Inbound rules → delete the `443/tcp` rule.
- **DigitalOcean** → Networking → Firewalls → inbound rules.
- **Vultr** → Products → Firewall → delete the rule.
- **AWS** → EC2 → Security Groups → revoke inbound.

### Russia VM

```bash
ssh <russia-vm>
cd ~/VPN/russia && sudo docker compose down -v
sudo rm -rf ~/VPN                                           # repo + .env + users/*.json + rendered config
sudo docker image rm ghcr.io/sagernet/sing-box:v1.10.3 2>/dev/null || true
command -v ufw >/dev/null && sudo ufw delete allow 443/tcp 2>/dev/null || true
```

Then close `:443` in Beget's panel (Служба → Правила брандмауэра / Firewall rules) or whichever RU host you used.

### Verify the port is closed

From any machine with network access to the VM:

```bash
nc -zv <VM_IP> 443 2>&1
# expected: "Connection refused" (listener gone) or a connect timeout (firewall still blocks it at the provider)
```

If you still see "Connected", either sing-box is somehow still running (`docker ps`) or you stopped at one VM but not both.

### Remove Docker too (optional)

Docker is left in place — harmless and reusable. To uninstall it:

```bash
sudo apt-get purge -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo rm -rf /var/lib/docker /etc/docker
```

### Clients

Delete the profile from Streisand / Hiddify on each device. That stops that device from connecting, but the UUID remains valid server-side until you also run `sudo ./user.sh remove <name>` on the Russia VM (or tear down the Russia VM entirely, as above).

## Design notes

- **No `geoip-ru` rule.** Matching is domain-only. Russian IPs hosting a foreign (likely blocked) service still egress via the exit VM. Given how fast the RU block-list changes, direct egress should be opt-in per confirmed RU service, not by IP range.
- **DNS is split.** `.ru`-geosite domains resolve via `77.88.8.8` (Yandex); everything else resolves via Cloudflare DoH tunneled through the exit hop. No leaks, no timing side-channel.
- **One Reality keypair per server.** The keypair authenticates the server; per-user UUIDs authenticate users. Rotating a user is cheap (UUID swap, one `user.sh rotate`). Rotating server keys requires re-issuing every client profile.
- **Rule-set auto-refreshes every 24h** (sing-box default) so new Russian services get classified without a redeploy.
- **VLESS+Reality+Vision** is the Russia-side entry protocol because it currently survives Russian DPI best of anything mainstream. If this ever stops working, the fallback path is AmneziaWG (obfuscated WireGuard) as a second inbound — not built yet, but a straightforward extension.
