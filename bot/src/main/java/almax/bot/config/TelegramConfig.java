package almax.bot.config;

import com.pengrad.telegrambot.TelegramBot;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(BotProperties.class)
public class TelegramConfig {

    @Bean(name = "publicBot")
    @Primary
    TelegramBot publicBot(BotProperties props) {
        return new TelegramBot.Builder(props.token()).build();
    }

    @Bean(name = "adminBot")
    TelegramBot adminBot(BotProperties props) {
        return new TelegramBot.Builder(props.adminToken()).build();
    }
}
