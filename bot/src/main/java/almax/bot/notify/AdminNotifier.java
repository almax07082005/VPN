package almax.bot.notify;

import almax.bot.config.BotProperties;
import almax.bot.user.BotUser;
import almax.bot.vpn.VpnService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotifier {

    private final TelegramBot bot;
    private final BotProperties props;

    public void notifyNewPending(BotUser u) {
        String who = u.getUsername() == null
                ? "(no username)  tg:" + u.getTgUserId()
                : "@" + u.getUsername() + "  tg:" + u.getTgUserId();
        String body = ("""
                New access request
                #%d  %s

                /admin approve %d <alias>
                /admin deny %d
                /admin remove %d""").formatted(
                u.getId(), who, u.getId(), u.getId(), u.getId());
        SendResponse resp = bot.execute(new SendMessage(props.adminTgId(), body));
        if (!resp.isOk()) {
            log.warn("Failed to notify admin of new pending user id={}: {}", u.getId(), resp.description());
        }
    }

    public void notifyApproved(BotUser u) {
        SendResponse resp = bot.execute(new SendMessage(u.getTgUserId(),
                "You're in. You'll receive announcements from now on."));
        if (!resp.isOk()) {
            log.warn("Failed to DM approval to user id={}: {}", u.getId(), resp.description());
        }
    }

    public void notifyVpnProvisioned(BotUser u, VpnService.Provision provision) {
        String header = "VPN config for #%d (%s) — alias '%s' (%s)".formatted(
                u.getId(),
                u.getUsername() == null ? "tg:" + u.getTgUserId() : "@" + u.getUsername(),
                provision.alias(),
                provision.action() == VpnService.Action.ADDED ? "new" : "rotated");

        SendResponse h = bot.execute(new SendMessage(props.adminTgId(), header));
        if (!h.isOk()) {
            log.warn("Failed to send VPN header to admin for user id={}: {}", u.getId(), h.description());
        }

        String code = "```\n" + provision.vlessUri() + "\n```";
        SendResponse t = bot.execute(new SendMessage(props.adminTgId(), code)
                .parseMode(ParseMode.MarkdownV2));
        if (!t.isOk()) {
            log.warn("Failed to send VPN URI block to admin for user id={}: {}", u.getId(), t.description());
        }

        SendResponse photo = bot.execute(new SendPhoto(props.adminTgId(), provision.qrPng())
                .caption("QR for " + provision.alias()));
        if (!photo.isOk()) {
            log.warn("Failed to send VPN QR photo to admin for user id={}: {}", u.getId(), photo.description());
        }
    }

    public void notifyError(String text) {
        SendResponse resp = bot.execute(new SendMessage(props.adminTgId(), text));
        if (!resp.isOk()) {
            log.warn("Failed to push error to admin: {}", resp.description());
        }
    }
}
