package almax.bot.telegram;

import com.pengrad.telegrambot.model.Update;

public interface UpdateHandler {

    boolean supports(Update update);

    void handle(Update update);
}
