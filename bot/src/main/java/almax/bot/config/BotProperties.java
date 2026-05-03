package almax.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bot")
public record BotProperties(String token, long adminTgId, long broadcastPacingMs) {

    @Override
    public String toString() {
        return "BotProperties[adminTgId=%d, broadcastPacingMs=%d, token=***]"
                .formatted(adminTgId, broadcastPacingMs);
    }
}
