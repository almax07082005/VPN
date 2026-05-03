package almax.bot.broadcast;

import almax.bot.config.BotProperties;
import almax.bot.user.BotUser;
import almax.bot.user.UserService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastService {

    private final TelegramBot bot;
    private final UserService userService;
    private final BotProperties props;

    public BroadcastResult sendToAllApproved(String text) {
        return sendTo(userService.listApproved(), text);
    }

    public BroadcastResult sendTo(List<BotUser> users, String text) {
        int sent = 0, failed = 0;
        for (BotUser u : users) {
            try {
                SendResponse resp = bot.execute(new SendMessage(u.getTgUserId(), text));
                if (resp.isOk()) {
                    sent++;
                } else {
                    failed++;
                    log.warn("Broadcast to user id={} (tg={}) not ok: {}", u.getId(), u.getTgUserId(), resp.description());
                }
            } catch (Exception e) {
                failed++;
                log.warn("Broadcast to user id={} (tg={}) threw", u.getId(), u.getTgUserId(), e);
            }
            try {
                Thread.sleep(props.broadcastPacingMs());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Broadcast loop interrupted after {} sends", sent + failed);
                break;
            }
        }
        return new BroadcastResult(sent, failed, users.size());
    }
}
