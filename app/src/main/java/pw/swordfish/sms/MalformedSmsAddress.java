package pw.swordfish.sms;

public class MalformedSmsAddress extends Exception {
    public MalformedSmsAddress(String format, Object... params) {
        super(String.format(format, params));
    }
}
