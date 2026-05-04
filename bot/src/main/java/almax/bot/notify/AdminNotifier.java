package almax.bot.notify;

import almax.bot.config.BotProperties;
import almax.bot.telegram.TgMarkdown;
import almax.bot.user.BotUser;
import almax.bot.vpn.VpnService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AdminNotifier {

    private final TelegramBot adminBot;
    private final TelegramBot publicBot;
    private final BotProperties props;

    public AdminNotifier(@Qualifier("adminBot") TelegramBot adminBot,
                         @Qualifier("publicBot") TelegramBot publicBot,
                         BotProperties props) {
        this.adminBot = adminBot;
        this.publicBot = publicBot;
        this.props = props;
    }

    public void notifyNewPending(BotUser u) {
        String who = u.getUsername() == null
                ? "(no username)  tg:" + u.getTgUserId()
                : "@" + u.getUsername() + "  tg:" + u.getTgUserId();
        String body = "New access request\n"
                + TgMarkdown.esc("#" + u.getId() + "  " + who) + "\n\n"
                + TgMarkdown.code("/users approve " + u.getId()) + " " + TgMarkdown.esc("<alias>") + "\n"
                + TgMarkdown.code("/users deny " + u.getId()) + "\n"
                + TgMarkdown.code("/users remove " + u.getId());
        SendResponse resp = adminBot.execute(new SendMessage(props.adminTgId(), body)
                .parseMode(ParseMode.MarkdownV2));
        if (!resp.isOk()) {
            log.warn("Failed to notify admin of new pending user id={}: {}", u.getId(), resp.description());
        }
    }

    public void notifyApproved(BotUser u) {
        SendResponse resp = publicBot.execute(new SendMessage(u.getTgUserId(),
                "You're in. You'll receive announcements from now on."));
        if (!resp.isOk()) {
            log.warn("Failed to DM approval to user id={}: {}", u.getId(), resp.description());
        }
    }

    public void notifyDenied(BotUser u) {
        SendResponse resp = publicBot.execute(new SendMessage(u.getTgUserId(),
                "Your access was revoked by the admin."));
        if (!resp.isOk()) {
            log.warn("Failed to DM deny to user id={}: {}", u.getId(), resp.description());
        }
    }

    public void notifyRemoved(BotUser u) {
        SendResponse resp = publicBot.execute(new SendMessage(u.getTgUserId(),
                "Your access was revoked and your record was cleared. "
                        + "Send /start to request access again."));
        if (!resp.isOk()) {
            log.warn("Failed to DM remove to user id={}: {}", u.getId(), resp.description());
        }
    }

    public void notifyVpnProvisioned(BotUser u, VpnService.Provision provision) {
        String who = u.getUsername() == null ? "tg:" + u.getTgUserId() : "@" + u.getUsername();
        String action = provision.action() == VpnService.Action.ADDED ? "new" : "rotated";
        String header = "VPN config for "
                + TgMarkdown.esc("#" + u.getId() + " (" + who + ") — alias ") + TgMarkdown.code(provision.alias())
                + TgMarkdown.esc(" (" + action + ")");

        SendResponse h = adminBot.execute(new SendMessage(props.adminTgId(), header)
                .parseMode(ParseMode.MarkdownV2));
        if (!h.isOk()) {
            log.warn("Failed to send VPN header to admin for user id={}: {}", u.getId(), h.description());
        }

        SendResponse t = adminBot.execute(new SendMessage(props.adminTgId(), TgMarkdown.code(provision.vlessUri()))
                .parseMode(ParseMode.MarkdownV2));
        if (!t.isOk()) {
            log.warn("Failed to send VPN URI block to admin for user id={}: {}", u.getId(), t.description());
        }

        SendResponse photo = adminBot.execute(new SendPhoto(props.adminTgId(), provision.qrPng())
                .caption("QR for " + provision.alias()));
        if (!photo.isOk()) {
            log.warn("Failed to send VPN QR photo to admin for user id={}: {}", u.getId(), photo.description());
        }
    }

    public void notifyError(String text) {
        SendResponse resp = adminBot.execute(new SendMessage(props.adminTgId(), text));
        if (!resp.isOk()) {
            log.warn("Failed to push error to admin: {}", resp.description());
        }
    }
}
