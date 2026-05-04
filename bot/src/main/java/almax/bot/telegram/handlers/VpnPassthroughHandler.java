package almax.bot.telegram.handlers;

import almax.bot.telegram.AdminGuard;
import almax.bot.telegram.AdminUpdateHandler;
import almax.bot.telegram.TgMarkdown;
import almax.bot.vpn.VpnService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class VpnPassthroughHandler implements AdminUpdateHandler {

    private static final int MAX_BODY = 3800;

    private final VpnService vpnService;
    private final AdminGuard adminGuard;
    private final TelegramBot bot;

    public VpnPassthroughHandler(VpnService vpnService,
                                 AdminGuard adminGuard,
                                 @Qualifier("adminBot") TelegramBot adminBot) {
        this.vpnService = vpnService;
        this.adminGuard = adminGuard;
        this.bot = adminBot;
    }

    @Override
    public boolean supports(Update update) {
        Message msg = update.message();
        if (msg == null || msg.text() == null) return false;
        String text = msg.text().trim();
        return text.equals("/vpn") || text.startsWith("/vpn ") || text.startsWith("/vpn@");
    }

    @Override
    public void handle(Update update) {
        Message msg = update.message();
        if (!adminGuard.isAdmin(msg)) return;

        String[] tokens = msg.text().trim().split("\\s+");
        List<String> args = new ArrayList<>();
        for (int i = 1; i < tokens.length; i++) {
            String t = tokens[i];
            if (i == 1 && t.startsWith("/vpn@")) continue;
            args.add(t);
        }
        if (args.isEmpty()) {
            replyMd(msg, "Usage: " + TgMarkdown.code("/vpn") + " "
                    + TgMarkdown.esc("<subcommand> [args…]")
                    + "\nExample: " + TgMarkdown.code("/vpn list"));
            return;
        }

        VpnService.RawResult result = vpnService.runRaw(args);
        String body = result.output();
        boolean truncated = body.length() > MAX_BODY;
        if (truncated) {
            body = body.substring(0, MAX_BODY) + "\n…(truncated)";
        }
        String header = "exit=" + result.exitCode();
        String text;
        if (body.isBlank()) {
            text = TgMarkdown.esc(header + " (no output)");
        } else {
            text = TgMarkdown.esc(header) + "\n" + TgMarkdown.codeBlock(body);
        }
        replyMd(msg, text);
    }

    private void replyMd(Message msg, String text) {
        bot.execute(new SendMessage(msg.chat().id(), text).parseMode(ParseMode.MarkdownV2));
    }
}
