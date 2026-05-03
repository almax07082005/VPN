package almax.bot.config;

import com.pengrad.telegrambot.TelegramBot;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BotProperties.class)
public class TelegramConfig {

    @Bean
    TelegramBot telegramBot(BotProperties props) {
        return new TelegramBot.Builder(props.token()).build();
    }
}
