package almax.bot.telegram.handlers;

import almax.bot.notify.AdminNotifier;
import almax.bot.telegram.PublicUpdateHandler;
import almax.bot.user.RegisterOutcome;
import almax.bot.user.UserService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StartCommandHandler implements PublicUpdateHandler {

    private final UserService userService;
    private final AdminNotifier adminNotifier;
    private final TelegramBot bot;

    public StartCommandHandler(UserService userService,
                               AdminNotifier adminNotifier,
                               @Qualifier("publicBot") TelegramBot publicBot) {
        this.userService = userService;
        this.adminNotifier = adminNotifier;
        this.bot = publicBot;
    }

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
