package almax.bot.telegram.handlers;

import almax.bot.broadcast.BroadcastResult;
import almax.bot.broadcast.BroadcastService;
import almax.bot.telegram.AdminGuard;
import almax.bot.telegram.AdminUpdateHandler;
import almax.bot.telegram.TgMarkdown;
import almax.bot.user.BotUser;
import almax.bot.user.UserService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SendToCommandHandler implements AdminUpdateHandler {

    private static final String USAGE = "Usage: " + TgMarkdown.code("/sendto") + " "
            + TgMarkdown.esc("<id1>,<id2>,... <text>");

    private final BroadcastService broadcastService;
    private final UserService userService;
    private final AdminGuard adminGuard;
    private final TelegramBot bot;

    public SendToCommandHandler(BroadcastService broadcastService,
                                UserService userService,
                                AdminGuard adminGuard,
                                @Qualifier("adminBot") TelegramBot adminBot) {
        this.broadcastService = broadcastService;
        this.userService = userService;
        this.adminGuard = adminGuard;
        this.bot = adminBot;
    }

    @Override
    public boolean supports(Update update) {
        Message msg = update.message();
        if (msg == null || msg.text() == null) return false;
        String text = msg.text().trim();
        return text.equals("/sendto") || text.startsWith("/sendto ") || text.startsWith("/sendto@");
    }

    @Override
    public void handle(Update update) {
        Message msg = update.message();
        if (!adminGuard.isAdmin(msg)) return;

        String[] tokens = msg.text().trim().split("\\s+", 3);
        if (tokens.length < 3) {
            replyMd(msg, USAGE);
            return;
        }
        String idsRaw = tokens[1];
        String text = tokens[2].trim();
        if (text.isEmpty()) {
            replyMd(msg, USAGE);
            return;
        }

        Set<Long> ids = new LinkedHashSet<>();
        List<String> badTokens = new ArrayList<>();
        for (String part : idsRaw.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            try {
                ids.add(Long.parseLong(t));
            } catch (NumberFormatException nfe) {
                badTokens.add(t);
            }
        }
        if (!badTokens.isEmpty()) {
            replyMd(msg, TgMarkdown.esc("Bad id(s): " + String.join(", ", badTokens)) + "\n\n" + USAGE);
            return;
        }
        if (ids.isEmpty()) {
            replyMd(msg, USAGE);
            return;
        }

        List<BotUser> users = userService.findByIds(new ArrayList<>(ids));
        Set<Long> found = new LinkedHashSet<>();
        for (BotUser u : users) found.add(u.getId());
        List<Long> missing = new ArrayList<>();
        for (Long id : ids) if (!found.contains(id)) missing.add(id);

        if (users.isEmpty()) {
            reply(msg, "No matching users for ids: " + idsRaw);
            return;
        }

        BroadcastResult result = broadcastService.sendTo(users, text);
        StringBuilder sb = new StringBuilder()
                .append("sent to %d / %d users, %d failed".formatted(result.sent(), result.total(), result.failed()));
        if (!missing.isEmpty()) {
            sb.append("\nunknown ids: ");
            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(missing.get(i));
            }
        }
        reply(msg, sb.toString());
    }

    private void reply(Message msg, String text) {
        bot.execute(new SendMessage(msg.chat().id(), text));
    }

    private void replyMd(Message msg, String text) {
        bot.execute(new SendMessage(msg.chat().id(), text).parseMode(ParseMode.MarkdownV2));
    }
}
