package almax.bot.vpn;

public class VpnException extends RuntimeException {

    public VpnException(String message) {
        super(message);
    }

    public VpnException(String message, Throwable cause) {
        super(message, cause);
    }
}
