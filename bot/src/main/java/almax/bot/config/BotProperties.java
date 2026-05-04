package almax.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bot")
public record BotProperties(
        String token,
        long adminTgId,
        long broadcastPacingMs,
        Vpn vpn) {

    public record Vpn(long commandTimeoutMs) {}

    @Override
    public String toString() {
        return "BotProperties[adminTgId=%d, broadcastPacingMs=%d, vpn=%s, token=***]"
                .formatted(adminTgId, broadcastPacingMs, vpn);
    }
}
