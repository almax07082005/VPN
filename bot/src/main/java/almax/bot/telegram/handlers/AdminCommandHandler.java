package almax.bot.telegram.handlers;

import almax.bot.notify.AdminNotifier;
import almax.bot.telegram.AdminGuard;
import almax.bot.telegram.AdminUpdateHandler;
import almax.bot.telegram.TgMarkdown;
import almax.bot.user.BotUser;
import almax.bot.user.UserService;
import almax.bot.user.UserStatus;
import almax.bot.vpn.VpnException;
import almax.bot.vpn.VpnService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;

@Component
@Slf4j
public class AdminCommandHandler implements AdminUpdateHandler {

    private static final String USAGE = "Admin commands:\n"
            + "  " + TgMarkdown.code("/admin approve") + " " + TgMarkdown.esc("<id> <alias>") + "\n"
            + "  " + TgMarkdown.code("/admin deny") + " " + TgMarkdown.esc("<id>") + "\n"
            + "  " + TgMarkdown.code("/admin remove") + " " + TgMarkdown.esc("<id>") + "\n"
            + "  " + TgMarkdown.code("/admin list");

    private final UserService userService;
    private final VpnService vpnService;
    private final AdminNotifier adminNotifier;
    private final AdminGuard adminGuard;
    private final TelegramBot bot;

    public AdminCommandHandler(UserService userService,
                               VpnService vpnService,
                               AdminNotifier adminNotifier,
                               AdminGuard adminGuard,
                               @Qualifier("adminBot") TelegramBot adminBot) {
        this.userService = userService;
        this.vpnService = vpnService;
        this.adminNotifier = adminNotifier;
        this.adminGuard = adminGuard;
        this.bot = adminBot;
    }

    @Override
    public boolean supports(Update update) {
        Message msg = update.message();
        if (msg == null || msg.text() == null) return false;
        String text = msg.text().trim();
        return text.equals("/admin") || text.startsWith("/admin ") || text.startsWith("/admin@");
    }

    @Override
    public void handle(Update update) {
        Message msg = update.message();
        if (!adminGuard.isAdmin(msg)) return;

        String[] tokens = msg.text().trim().split("\\s+", 4);
        if (tokens.length < 2) {
            replyMd(msg, USAGE);
            return;
        }
        String sub = tokens[1].toLowerCase();
        try {
            switch (sub) {
                case "approve" -> handleApprove(msg, tokens);
                case "deny" -> handleDeny(msg, tokens);
                case "remove" -> handleRemove(msg, tokens);
                case "list" -> handleList(msg);
                default -> replyMd(msg, "Unknown subcommand: " + TgMarkdown.esc(sub) + "\n\n" + USAGE);
            }
        } catch (NoSuchElementException nse) {
            reply(msg, nse.getMessage());
        } catch (NumberFormatException nfe) {
            replyMd(msg, "Bad id\\.\n\n" + USAGE);
        } catch (IllegalArgumentException iae) {
            replyMd(msg, TgMarkdown.esc(iae.getMessage()) + "\n\n" + USAGE);
        }
    }

    private void handleApprove(Message msg, String[] tokens) {
        if (tokens.length < 4) {
            replyMd(msg, "Usage: " + TgMarkdown.code("/admin approve") + " " + TgMarkdown.esc("<id> <alias>"));
            return;
        }
        long id = Long.parseLong(tokens[2]);
        String alias = tokens[3].trim();

        BotUser existing = userService.findRequired(id);

        VpnService.Provision provision;
        try {
            provision = vpnService.approve(alias);
        } catch (VpnException e) {
            log.error("VPN approve failed for user id={} alias={}", id, alias, e);
            reply(msg, "VPN provisioning failed: " + e.getMessage()
                    + "\n(DB status unchanged for user #" + id + ")");
            return;
        }

        BotUser u = userService.approve(existing.getId(), alias);
        adminNotifier.notifyApproved(u);
        adminNotifier.notifyVpnProvisioned(u, provision);
        reply(msg, "Approved user #" + id + " (tg:" + u.getTgUserId() + ") alias=" + u.getAlias()
                + " — VPN " + (provision.action() == VpnService.Action.ADDED ? "added" : "rotated"));
    }

    private void handleDeny(Message msg, String[] tokens) {
        if (tokens.length < 3) {
            replyMd(msg, "Usage: " + TgMarkdown.code("/admin deny") + " " + TgMarkdown.esc("<id>"));
            return;
        }
        long id = Long.parseLong(tokens[2]);

        BotUser before = userService.findRequired(id);
        String alias = before.getAlias();
        boolean revoked = revokeVpnSafely(msg, id, alias);

        BotUser u = userService.deny(id);
        StringBuilder sb = new StringBuilder()
                .append("Denied user #").append(id).append(" (tg:").append(u.getTgUserId()).append(")");
        if (alias != null && !alias.isBlank()) {
            sb.append(" — VPN ").append(revoked ? "revoked (alias='" + alias + "')" : "no-op (no '" + alias + "' on host)");
        }
        reply(msg, sb.toString());
    }

    private void handleRemove(Message msg, String[] tokens) {
        if (tokens.length < 3) {
            replyMd(msg, "Usage: " + TgMarkdown.code("/admin remove") + " " + TgMarkdown.esc("<id>"));
            return;
        }
        long id = Long.parseLong(tokens[2]);

        BotUser before = userService.findRequired(id);
        String alias = before.getAlias();
        boolean revoked = revokeVpnSafely(msg, id, alias);

        BotUser u = userService.remove(id);
        StringBuilder sb = new StringBuilder()
                .append("Removed user #").append(id).append(" (tg:").append(u.getTgUserId()).append(")");
        if (alias != null && !alias.isBlank()) {
            sb.append(" — VPN ").append(revoked ? "revoked (alias='" + alias + "')" : "no-op (no '" + alias + "' on host)");
        }
        reply(msg, sb.toString());
    }

    private boolean revokeVpnSafely(Message msg, long id, String alias) {
        if (alias == null || alias.isBlank()) return false;
        try {
            return vpnService.removeIfExists(alias);
        } catch (VpnException e) {
            log.error("VPN revoke failed for user id={} alias={}", id, alias, e);
            reply(msg, "WARNING: VPN revoke for alias='" + alias + "' failed: " + e.getMessage()
                    + "\nProceeding with DB update anyway.");
            return false;
        }
    }

    private void handleList(Message msg) {
        List<BotUser> users = userService.listAll();
        if (users.isEmpty()) {
            reply(msg, "No users yet. (total: 0)");
            return;
        }
        int approved = 0, pending = 0, denied = 0;
        StringBuilder sb = new StringBuilder("Users:\n");
        for (BotUser u : users) {
            sb.append(u.getId()).append(" | ")
              .append(u.getAlias() == null ? "—" : u.getAlias()).append(" | ")
              .append(u.getUsername() == null ? "(no username)" : "@" + u.getUsername()).append(" | tg:")
              .append(u.getTgUserId()).append(" | ")
              .append(u.getStatus()).append('\n');
            UserStatus s = u.getStatus();
            if (s == UserStatus.APPROVED) approved++;
            else if (s == UserStatus.PENDING) pending++;
            else if (s == UserStatus.DENIED) denied++;
        }
        sb.append("\nTotal: ").append(users.size())
          .append(" (approved=").append(approved)
          .append(", pending=").append(pending)
          .append(", denied=").append(denied)
          .append(')');
        reply(msg, sb.toString());
    }

    private void reply(Message msg, String text) {
        bot.execute(new SendMessage(msg.chat().id(), text));
    }

    private void replyMd(Message msg, String text) {
        bot.execute(new SendMessage(msg.chat().id(), text).parseMode(ParseMode.MarkdownV2));
    }
}
