package almax.bot.telegram.handlers;

import almax.bot.notify.AdminNotifier;
import almax.bot.telegram.AdminGuard;
import almax.bot.telegram.UpdateHandler;
import almax.bot.user.RegisterOutcome;
import almax.bot.user.UserService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartCommandHandler implements UpdateHandler {

    private final UserService userService;
    private final AdminNotifier adminNotifier;
    private final AdminGuard adminGuard;
    private final TelegramBot bot;

    @Override
    public boolean supports(Update update) {
        Message msg = update.message();
        if (msg == null || msg.text() == null) return false;
        String text = msg.text().trim();
        return text.equals("/start") || text.startsWith("/start ") || text.startsWith("/start@");
    }

    @Override
    public void handle(Update update) {
        Message msg = update.message();
        User from = msg.from();
        if (from == null) return;

        if (adminGuard.isAdmin(msg)) {
            bot.execute(new SendMessage(msg.chat().id(),
                    "You're the admin. Use /admin and /send."));
            return;
        }

        UserService.RegisterResult result = userService.register(from.id(), from.username());
        String reply = switch (result.outcome()) {
            case NEW_PENDING -> "Request sent. Waiting for the admin to approve you.";
            case ALREADY_PENDING -> "You're already in the queue. Please wait.";
            case ALREADY_APPROVED -> "You're already approved. You'll receive announcements here.";
            case DENIED -> "Your request was previously denied.";
        };
        bot.execute(new SendMessage(msg.chat().id(), reply));

        if (result.outcome() == RegisterOutcome.NEW_PENDING) {
            adminNotifier.notifyNewPending(result.user());
        }
    }
}
