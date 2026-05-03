package almax.bot.telegram;

import almax.bot.config.BotProperties;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminGuard {

    private final BotProperties props;

    public long adminTgId() {
        return props.adminTgId();
    }

    public boolean isAdmin(Message msg) {
        return msg != null && msg.from() != null && msg.from().id() == props.adminTgId();
    }

    public boolean isAdmin(CallbackQuery cq) {
        return cq != null && cq.from() != null && cq.from().id() == props.adminTgId();
    }
}
