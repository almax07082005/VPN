# vpn-bot

Broadcast-only Telegram bot for the VPN project. Runs on the Russia VM in its own compose stack alongside `vpn-russia`.

## Flow

1. A user DMs `/start` to the bot.
2. The single admin (set via `ADMIN_TG_ID`) gets a notification with `[✅ Approve] [❌ Deny]` buttons.
3. Once approved, the user receives any future `/send <text>` broadcasts the admin issues.

## Run

```sh
cp .env.example .env
$EDITOR .env                       # fill BOT_TOKEN, ADMIN_TG_ID, POSTGRES_PASSWORD
docker compose up -d --build
docker compose logs -f vpn-bot
```

The bot uses long polling — no public webhook, no port to expose to the internet. Port `8080` is only exposed inside the `vpn-bot-net` Docker network (and the container itself) for the actuator healthcheck.

## Admin commands

All `/admin` and `/send` commands are silently ignored unless the sender's Telegram user id matches `ADMIN_TG_ID`.

```
/admin approve <id>     approve a pending user
/admin remove <id>      revoke a user (soft-delete: status = DENIED)
/admin alias <id> <txt> set a local alias for a user
/admin list             list all users (id | alias | @username | status)
/send <text>            broadcast text to every APPROVED user
```

The `<id>` is the bot's local sequential id, not the Telegram user id. Use `/admin list` to see it.

## State

Postgres data lives in the named volume `vpn-bot-postgres-data`. `docker compose down` keeps it; `docker compose down -v` deletes it.

## Notes

- `compose.yaml` uses strict `${VAR:?...}` interpolation — missing env in `.env` fails at `docker compose up` instead of producing a half-broken bot. (The `russia/exit` stacks use the looser `${VAR:-}` form.)
- Removed users (status `DENIED`) cannot re-register via `/start` — the row stays so the unique constraint on `tg_user_id` blocks re-creation.
