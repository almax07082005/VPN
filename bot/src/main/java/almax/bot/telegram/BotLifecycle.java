package almax.bot.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.response.BaseResponse;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BotLifecycle {

    private final TelegramBot bot;
    private final UpdateRouter router;
    private final AdminGuard adminGuard;

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        log.info("Bot starting; admin tg id = {}", adminGuard.adminTgId());
        BaseResponse resp = bot.execute(new SetMyCommands(
                new BotCommand("start", "Request access"),
                new BotCommand("send", "Broadcast a message (admin)"),
                new BotCommand("sendto", "Send a message to specific user ids (admin)"),
                new BotCommand("admin", "Admin tools (admin)")
        ));
        if (!resp.isOk()) {
            log.warn("SetMyCommands failed: {}", resp.description());
        }
        bot.setUpdatesListener(router);
        log.info("Bot polling started");
    }

    @PreDestroy
    void shutdown() {
        try {
            bot.removeGetUpdatesListener();
        } catch (Exception e) {
            log.warn("removeGetUpdatesListener threw on shutdown", e);
        }
        try {
            bot.shutdown();
        } catch (Exception e) {
            log.warn("bot.shutdown threw", e);
        }
    }
}
