# vpn-bot

Broadcast-only Telegram bot for the VPN project. Runs on the Russia VM in its own compose stack alongside `vpn-russia`. Spring Boot 4 + Java 21, Postgres 16, [pengrad/java-telegram-bot-api](https://github.com/pengrad/java-telegram-bot-api) on long polling.

## Flow

1. A user DMs `/start` to the bot. Their request is recorded with status `PENDING`.
2. The single admin (set via `ADMIN_TG_ID`) gets a DM with the new user's local id, Telegram username, and the exact admin commands ready to copy-paste.
3. Admin runs `/admin approve <id> <alias>` — the user is set to `APPROVED` and gets a DM confirming they're in.
4. Admin runs `/send <text>` — every `APPROVED` user receives the text. The admin gets a one-line `sent / total, failed` report back.

There are no inline buttons; everything is text commands. The bot uses long polling — no public webhook, no port to expose to the internet. Port `8080` is only reachable inside the `vpn-bot-net` Docker network for the actuator healthcheck.

## Run

```sh
cp .env.example .env
$EDITOR .env                       # fill BOT_TOKEN, ADMIN_TG_ID, POSTGRES_PASSWORD
docker compose up -d --build
docker compose logs -f vpn-bot
```

`compose.yaml` uses strict `${VAR:?...}` interpolation — missing env in `.env` fails at `docker compose up` instead of producing a half-broken bot. (The `russia/exit` stacks use the looser `${VAR:-}` form.)

## Admin commands

All `/admin`, `/send`, and `/sendto` commands are silently ignored unless the sender's Telegram user id matches `ADMIN_TG_ID`. `/start` from the admin's own account just prints a hint.

```
/admin approve <id> <alias>   approve a pending user (alias is required)
/admin deny <id>              mark a user as DENIED (kept in db, can't re-register)
/admin remove <id>            hard-delete a user row (they can re-register fresh)
/admin list                   list all users: <id> | <alias> | @<username> | tg:<tg_id> | <status>
/send <text>                  broadcast text to every APPROVED user
/sendto <id1>,<id2>,... <text>  send text to the listed users (any status); unknown ids are reported back
```

The `<id>` in admin commands is the bot's local sequential id from `bot_user.id`, not the Telegram user id. Use `/admin list` to see it. The admin notification DM on `/start` already includes the right id pre-filled into the command templates.

`deny` vs `remove`: `deny` keeps the row with status `DENIED` so the unique constraint on `tg_user_id` blocks re-registration. `remove` deletes the row entirely, so the same Telegram user can `/start` again and land back in the queue.

## Broadcast pacing

`BroadcastService` sleeps `bot.broadcast-pacing-ms` (default `50` ms, set in `application.yaml`) between sends to stay under Telegram's per-bot rate limit (~30 msg/s). Failures are logged but don't abort the run; the reply to the admin reports `sent / total, failed`.

## State

Postgres data lives in the named volume `vpn-bot-postgres-data`. `docker compose down` keeps it; `docker compose down -v` deletes it. Schema is managed by Flyway (`src/main/resources/db/migration/`), and JPA runs in `validate` mode — schema drift fails startup rather than auto-migrating.

The Postgres container exposes `5433:5432` on the host for ad-hoc inspection (`psql -h 127.0.0.1 -p 5433 -U vpnbot vpnbot`); the bot itself reaches it over the internal Docker network as `vpn-bot-postgres:5432`.

## Layout

```
bot/
├── Dockerfile                     # multi-stage: gradle bootJar -> temurin 21 jre
├── compose.yaml                   # vpn-bot + vpn-bot-postgres on vpn-bot-net
├── .env.example                   # BOT_TOKEN, ADMIN_TG_ID, POSTGRES_PASSWORD
└── src/main/
    ├── resources/
    │   ├── application.yaml       # spring + bot.* config, flyway, actuator health
    │   └── db/migration/          # V1__create_bot_user_table.sql
    └── java/almax/bot/
        ├── BotApplication.java
        ├── config/                # BotProperties (@ConfigurationProperties bot.*), TelegramBot bean
        ├── telegram/
        │   ├── BotLifecycle.java  # SetMyCommands + setUpdatesListener on ApplicationReadyEvent
        │   ├── UpdateRouter.java  # picks the first UpdateHandler whose supports() returns true
        │   ├── AdminGuard.java    # isAdmin(msg) check against ADMIN_TG_ID
        │   └── handlers/          # /start, /admin, /send, /sendto
        ├── user/                  # BotUser entity, UserStatus, UserService, UserRepository
        ├── broadcast/             # BroadcastService + BroadcastResult
        └── notify/                # AdminNotifier — DMs the admin on new pending, DMs user on approval
```
