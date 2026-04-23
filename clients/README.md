# Client setup — Hiddify

Hiddify is a cross-platform VLESS+Reality client with native support for the Vision flow this deployment uses. Pick your platform below, install, and scan the QR printed by `russia/bootstrap.sh` (or `russia/user.sh add <name>` / `show <name>`).

Everywhere: after scanning, **pick the new profile and tap Connect.** There is no per-app split-tunnel config to touch — the routing happens on the Russia VM.

## iOS / iPadOS

1. Install **Hiddify** from the App Store: <https://apps.apple.com/app/hiddify-proxy-vpn/id6596777532>.
2. Open Hiddify → tap the **+** button in the top-right → **Scan QR code**.
3. Point the camera at the QR printed in the terminal after `bootstrap.sh` / `user.sh add`.
4. The new profile appears in the list. Tap it, then tap the big **Connect** button.
5. iOS will ask permission to install a VPN profile — approve it once.

Verify: open Safari → visit `https://ifconfig.me` → should show the **exit** IP. Then visit `https://2ip.ru` → should show the **Russia** IP.

## Android

1. Install **Hiddify Next** from Google Play: <https://play.google.com/store/apps/details?id=app.hiddify.com>, or grab the APK from <https://github.com/hiddify/hiddify-next/releases>.
2. Open Hiddify → **+** → **Scan QR code**.
3. Scan, select the profile, tap **Connect**.
4. Approve the VPN permission prompt.

## macOS

1. Download Hiddify from <https://hiddify.com/> (direct `.dmg`) or install via Homebrew: `brew install --cask hiddify`.
2. Open the app.
3. Paste the `vless://…` URI with **⌘V** directly into the app, or use **Import from clipboard** after copying it from the terminal.
4. Click **Connect**.
5. macOS asks for admin permission to install a system VPN extension — enter your password once.

## Windows

1. Download the installer from <https://hiddify.com/> (`.msi`) and run it.
2. Open Hiddify.
3. Paste the `vless://…` URI (copy it from the terminal via SSH) into **Add profile → Paste link**.
4. Click **Connect**.
5. Windows prompts for admin rights to create the VPN adapter — approve once.

## Connection name / display label

The QR encodes a profile name like `vpn2vpn-<user>` (e.g. `vpn2vpn-admin`). You can rename it in Hiddify without affecting the connection.

## Troubleshooting

| Symptom | Check |
|---|---|
| Hiddify "connects" but nothing loads | `ifconfig.me` returns neither RU nor exit IP → sing-box on RU is down. SSH in and `docker compose ps` / `docker compose logs`. |
| YouTube loads, VK complains about foreign IP | Russia-direct routing broke. Check `docker compose logs sing-box` on the RU VM for `rule_set` download errors. |
| VK loads, YouTube fails | exit leg is down. SSH to the exit VM, check `docker compose ps`. Also test `nc -zv <EXIT_IP> 443` from the RU VM. |
| Keeps disconnecting on mobile data | Some Russian ISPs throttle long-lived TLS flows. Switch SIM or try the `dl.google.com` covering site (edit `config.json.tmpl`, re-bootstrap). |
| DPI blocked the SNI | Rotate covering site in both `config.json.tmpl` files. Keep the client URIs in sync — `www.microsoft.com` is in the QR too. |
| Can't scan QR cleanly from SSH terminal | Run `./user.sh show <name>` and copy the `vless://` URI directly — Hiddify accepts pasted URIs on every platform. |
