package almax.bot.telegram.handlers;

import almax.bot.notify.AdminNotifier;
import almax.bot.telegram.AdminGuard;
import almax.bot.telegram.UpdateHandler;
import almax.bot.user.BotUser;
import almax.bot.user.UserService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminCommandHandler implements UpdateHandler {

    private static final String USAGE = """
            Admin commands:
              /admin approve <id> <alias>
              /admin deny <id>
              /admin remove <id>
              /admin list""";

    private final UserService userService;
    private final AdminNotifier adminNotifier;
    private final AdminGuard adminGuard;
    private final TelegramBot bot;

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
            reply(msg, USAGE);
            return;
        }
        String sub = tokens[1].toLowerCase();
        try {
            switch (sub) {
                case "approve" -> handleApprove(msg, tokens);
                case "deny" -> handleDeny(msg, tokens);
                case "remove" -> handleRemove(msg, tokens);
                case "list" -> handleList(msg);
                default -> reply(msg, "Unknown subcommand: " + sub + "\n\n" + USAGE);
            }
        } catch (NoSuchElementException nse) {
            reply(msg, nse.getMessage());
        } catch (NumberFormatException nfe) {
            reply(msg, "Bad id.\n\n" + USAGE);
        } catch (IllegalArgumentException iae) {
            reply(msg, iae.getMessage() + "\n\n" + USAGE);
        }
    }

    private void handleApprove(Message msg, String[] tokens) {
        if (tokens.length < 4) { reply(msg, "Usage: /admin approve <id> <alias>"); return; }
        long id = Long.parseLong(tokens[2]);
        String alias = tokens[3].trim();
        BotUser u = userService.approve(id, alias);
        adminNotifier.notifyApproved(u);
        reply(msg, "Approved user #" + id + " (tg:" + u.getTgUserId() + ") alias=" + u.getAlias());
    }

    private void handleDeny(Message msg, String[] tokens) {
        if (tokens.length < 3) { reply(msg, "Usage: /admin deny <id>"); return; }
        long id = Long.parseLong(tokens[2]);
        BotUser u = userService.deny(id);
        reply(msg, "Denied user #" + id + " (tg:" + u.getTgUserId() + ")");
    }

    private void handleRemove(Message msg, String[] tokens) {
        if (tokens.length < 3) { reply(msg, "Usage: /admin remove <id>"); return; }
        long id = Long.parseLong(tokens[2]);
        BotUser u = userService.remove(id);
        reply(msg, "Removed user #" + id + " (tg:" + u.getTgUserId() + ")");
    }

    private void handleList(Message msg) {
        List<BotUser> users = userService.listAll();
        if (users.isEmpty()) {
            reply(msg, "No users yet.");
            return;
        }
        StringBuilder sb = new StringBuilder("Users:\n");
        for (BotUser u : users) {
            sb.append(u.getId()).append(" | ")
              .append(u.getAlias() == null ? "—" : u.getAlias()).append(" | ")
              .append(u.getUsername() == null ? "(no username)" : "@" + u.getUsername()).append(" | tg:")
              .append(u.getTgUserId()).append(" | ")
              .append(u.getStatus()).append('\n');
        }
        reply(msg, sb.toString());
    }

    private void reply(Message msg, String text) {
        bot.execute(new SendMessage(msg.chat().id(), text));
    }
}
