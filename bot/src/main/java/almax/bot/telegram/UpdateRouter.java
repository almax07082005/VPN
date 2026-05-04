package almax.bot.telegram;

import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class UpdateRouter implements UpdatesListener {

    private final String label;
    private final List<? extends UpdateHandler> handlers;

    @Override
    public int process(List<Update> updates) {
        for (Update update : updates) {
            try {
                handlers.stream()
                        .filter(h -> h.supports(update))
                        .findFirst()
                        .ifPresent(h -> h.handle(update));
            } catch (Exception e) {
                log.error("[{}] update {} failed", label, update.updateId(), e);
            }
        }
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }
}
