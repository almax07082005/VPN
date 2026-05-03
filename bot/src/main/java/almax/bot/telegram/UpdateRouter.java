package almax.bot.telegram;

import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateRouter implements UpdatesListener {

    private final List<UpdateHandler> handlers;

    @Override
    public int process(List<Update> updates) {
        for (Update update : updates) {
            try {
                handlers.stream()
                        .filter(h -> h.supports(update))
                        .findFirst()
                        .ifPresent(h -> h.handle(update));
            } catch (Exception e) {
                log.error("Update {} failed", update.updateId(), e);
            }
        }
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }
}
