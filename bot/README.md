# vpn-bot

Two-bot Telegram control plane for the VPN. Runs on the Russia VM in its own compose stack alongside `vpn-russia`. Spring Boot 4 + Java 21, Postgres 16, [pengrad/java-telegram-bot-api](https://github.com/pengrad/java-telegram-bot-api) on long polling.

There are two distinct Telegram bots running inside the same JVM:

- a **public bot** anyone can DM `/start` to request access; gets the broadcasts; admin can `/start` it too and is treated like any other user there.
- an **admin bot** that only the whitelisted Telegram user id (`ADMIN_TG_ID`) is allowed to talk to. All admin commands live here. Anyone else gets silently ignored.

Each bot has its own token and its own /commands list registered with @BotFather.

## Flow

1. A user DMs `/start` to the **public bot**. Their request is recorded with status `PENDING`.
2. The admin bot DMs the admin with the new user's local id, Telegram username, and the exact admin commands ready to tap-to-copy.
3. Admin runs `/admin approve <id> <alias>` on the **admin bot**:
   - The bot checks `vpn list` inside `vpn-russia`. If the alias already exists it runs `vpn rotate <alias>` (issuing a new UUID, invalidating the old client), otherwise `vpn add <alias>`.
   - The user row is set to `APPROVED` and the user gets a "you're in" DM on the public bot.
   - The admin gets back, on the admin bot: a header line, the rendered `vless://…` URI inside a tap-to-copy code block, and a PNG QR code rendered by `qrencode` inside the russia container.
4. Admin runs `/admin deny <id>` or `/admin remove <id>`:
   - The bot looks up the alias from the DB, then runs `vpn remove <alias>` against `vpn-russia` so the access key is actually revoked, not just marked DENIED.
   - `deny` keeps the row with status `DENIED` so the user can't re-register.
   - `remove` hard-deletes the row, leaving the Telegram user free to `/start` again.
5. Admin runs `/send <text>` on the admin bot — every `APPROVED` user receives the text via the public bot. The admin gets a one-line `sent / total, failed` report.

There are no inline buttons; everything is text commands. Both bots use long polling — no public webhook, no port to expose to the internet. Port `8080` is only reachable inside the `vpn-bot-net` Docker network for the actuator healthcheck.

## VPN integration

The bot reaches into the colocated `vpn-russia` container with `docker exec`, going through the host's `/var/run/docker.sock` (mounted into the bot container by `compose.yaml`). The container name is pinned to `vpn-russia` in `VpnService` to match `russia/docker-compose.yml`. This means the bot must run on the **same host** as `vpn-russia` — split them across VMs and the integration breaks.

Mounting the host docker socket is effectively root-on-host inside the bot container; that's an intentional trade-off for a single-tenant ops bot.

## Run

```sh
cp .env.example .env
$EDITOR .env                       # fill BOT_TOKEN, ADMIN_BOT_TOKEN, ADMIN_TG_ID, POSTGRES_PASSWORD
docker compose up -d --build
docker compose logs -f vpn-bot
```

`compose.yaml` uses strict `${VAR:?...}` interpolation — missing env in `.env` fails at `docker compose up` instead of producing a half-broken bot.

## Commands

### Public bot — anyone (admin included)

```
/start                              register; you'll receive announcements once approved
```

### Admin bot — `ADMIN_TG_ID` only, all others ignored

```
/admin approve <id> <alias>         approve a pending user; provisions or rotates the VLESS user
                                    on vpn-russia and DMs the URI + QR back to admin
/admin deny <id>                    mark user DENIED (kept in db, can't re-register);
                                    revokes the VPN user on vpn-russia if one exists
/admin remove <id>                  hard-delete the row (user can re-register from scratch);
                                    revokes the VPN user on vpn-russia if one exists
/admin list                         list all users + summary line: Total: N (approved=X, pending=Y, denied=Z)
/send <text>                        broadcast text via the public bot to every APPROVED user
/sendto <id1>,<id2>,... <text>      send text to the listed users (any status); unknown ids are reported back
```

The `<id>` is the bot's local sequential id from `bot_user.id`, **not** the Telegram user id. Use `/admin list` to see it. The new-pending notification already pre-fills the right id into the command templates and renders them as tap-to-copy code blocks in MarkdownV2.

`deny` vs `remove`: `deny` keeps the row with status `DENIED` so the unique constraint on `tg_user_id` blocks re-registration. `remove` deletes the row entirely, so the same Telegram user can `/start` again and land back in the queue.

## Broadcast pacing

`BroadcastService` sleeps `bot.broadcast-pacing-ms` (default `50` ms, set in `application.yaml`) between sends to stay under Telegram's per-bot rate limit (~30 msg/s). Failures are logged but don't abort the run; the reply to the admin reports `sent / total, failed`.

## State

Postgres data lives in the named volume `vpn-bot-postgres-data`. `docker compose down` keeps it; `docker compose down -v` deletes it. Schema is managed by Flyway (`src/main/resources/db/migration/`), and JPA runs in `validate` mode — schema drift fails startup rather than auto-migrating.

The Postgres container exposes `5433:5432` on the host for ad-hoc inspection (`psql -h 127.0.0.1 -p 5433 -U vpnbot vpnbot`); the bot itself reaches it over the internal Docker network as `vpn-bot-postgres:5432`.

## Layout

```
bot/
├── Dockerfile                     # multi-stage: gradle bootJar -> temurin 21 jre + docker CLI
├── compose.yaml                   # vpn-bot + vpn-bot-postgres on vpn-bot-net; mounts /var/run/docker.sock
├── .env.example                   # BOT_TOKEN, ADMIN_BOT_TOKEN, ADMIN_TG_ID, POSTGRES_PASSWORD
└── src/main/
    ├── resources/
    │   ├── application.yaml       # spring + bot.* config, flyway, actuator health
    │   └── db/migration/          # V1__create_bot_user_table.sql
    └── java/almax/bot/
        ├── BotApplication.java
        ├── config/                # BotProperties, two TelegramBot beans (publicBot @Primary, adminBot)
        ├── telegram/
        │   ├── BotLifecycle.java          # SetMyCommands + setUpdatesListener for both bots
        │   ├── UpdateRouter.java          # picks the first UpdateHandler whose supports() returns true
        │   ├── PublicUpdateHandler.java   # marker: routed to the public bot
        │   ├── AdminUpdateHandler.java    # marker: routed to the admin bot
        │   ├── AdminGuard.java            # isAdmin(msg) check against ADMIN_TG_ID
        │   ├── TgMarkdown.java            # MarkdownV2 escape + inline-code helpers
        │   └── handlers/                  # /start (public), /admin /send /sendto (admin)
        ├── user/                  # BotUser entity, UserStatus, UserService, UserRepository
        ├── broadcast/             # BroadcastService — sends via the public bot
        ├── notify/                # AdminNotifier — admin DMs via adminBot, user "you're in" DM via publicBot
        └── vpn/                   # VpnService — `docker exec vpn-russia vpn …` for add/rotate/remove/list
```
