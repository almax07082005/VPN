package almax.bot.telegram.handlers;

import almax.bot.broadcast.BroadcastResult;
import almax.bot.broadcast.BroadcastService;
import almax.bot.telegram.AdminGuard;
import almax.bot.telegram.TgMarkdown;
import almax.bot.telegram.UpdateHandler;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SendCommandHandler implements UpdateHandler {

    private final BroadcastService broadcastService;
    private final AdminGuard adminGuard;
    private final TelegramBot bot;

    @Override
    public boolean supports(Update update) {
        Message msg = update.message();
        if (msg == null || msg.text() == null) return false;
        String text = msg.text().trim();
        return text.equals("/send") || text.startsWith("/send ") || text.startsWith("/send@");
    }

    @Override
    public void handle(Update update) {
        Message msg = update.message();
        if (!adminGuard.isAdmin(msg)) return;

        String raw = msg.text();
        int idx = raw.indexOf(' ');
        if (idx < 0 || idx + 1 >= raw.length()) {
            replyUsage(msg);
            return;
        }
        String text = raw.substring(idx + 1).trim();
        if (text.isEmpty()) {
            replyUsage(msg);
            return;
        }

        BroadcastResult result = broadcastService.sendToAllApproved(text);
        bot.execute(new SendMessage(msg.chat().id(),
                "sent to %d / %d users, %d failed".formatted(result.sent(), result.total(), result.failed())));
    }

    private void replyUsage(Message msg) {
        bot.execute(new SendMessage(msg.chat().id(), "Usage: " + TgMarkdown.code("/send <text>"))
                .parseMode(ParseMode.MarkdownV2));
    }
}
