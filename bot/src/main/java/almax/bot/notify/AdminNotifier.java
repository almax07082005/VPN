package almax.bot.notify;

import almax.bot.config.BotProperties;
import almax.bot.user.BotUser;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
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
}
