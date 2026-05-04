package almax.bot.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.response.BaseResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class BotLifecycle {

    private final TelegramBot publicBot;
    private final TelegramBot adminBot;
    private final UpdateRouter publicRouter;
    private final UpdateRouter adminRouter;
    private final AdminGuard adminGuard;

    public BotLifecycle(@Qualifier("publicBot") TelegramBot publicBot,
                        @Qualifier("adminBot") TelegramBot adminBot,
                        List<PublicUpdateHandler> publicHandlers,
                        List<AdminUpdateHandler> adminHandlers,
                        AdminGuard adminGuard) {
        this.publicBot = publicBot;
        this.adminBot = adminBot;
        this.publicRouter = new UpdateRouter("public", publicHandlers);
        this.adminRouter = new UpdateRouter("admin", adminHandlers);
        this.adminGuard = adminGuard;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        log.info("Bots starting; admin tg id = {}", adminGuard.adminTgId());

        BaseResponse pubCmds = publicBot.execute(new SetMyCommands(
                new BotCommand("start", "Request access")
        ));
        if (!pubCmds.isOk()) log.warn("public SetMyCommands failed: {}", pubCmds.description());

        BaseResponse adminCmds = adminBot.execute(new SetMyCommands(
                new BotCommand("users", "User admin: approve / deny / remove / list"),
                new BotCommand("send", "Broadcast a message"),
                new BotCommand("sendto", "Send a message to specific user ids")
        ));
        if (!adminCmds.isOk()) log.warn("admin SetMyCommands failed: {}", adminCmds.description());

        publicBot.setUpdatesListener(publicRouter);
        adminBot.setUpdatesListener(adminRouter);
        log.info("Both bots polling: public + admin");
    }

    @PreDestroy
    void shutdown() {
        for (TelegramBot b : List.of(publicBot, adminBot)) {
            try {
                b.removeGetUpdatesListener();
            } catch (Exception e) {
                log.warn("removeGetUpdatesListener threw on shutdown", e);
            }
            try {
                b.shutdown();
            } catch (Exception e) {
                log.warn("bot.shutdown threw", e);
            }
        }
    }
}
