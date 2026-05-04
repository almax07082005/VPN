package almax.bot.telegram;

import java.util.regex.Pattern;

public final class TgMarkdown {

    private static final Pattern V2_SPECIALS = Pattern.compile("[_*\\[\\]()~`>#+\\-=|{}.!\\\\]");

    private TgMarkdown() {}

    public static String esc(String text) {
        return V2_SPECIALS.matcher(text).replaceAll("\\\\$0");
    }

    public static String code(String inline) {
        String inside = inline.replace("\\", "\\\\").replace("`", "\\`");
        return "`" + inside + "`";
    }

    public static String codeBlock(String content) {
        String inside = content.replace("\\", "\\\\").replace("`", "\\`");
        return "```\n" + inside + "\n```";
    }
}
