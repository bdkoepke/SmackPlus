package pw.swordfish.xmpp;

public class XMPPException extends Exception {
    XMPPException(Exception e) {
        super(e);
    }

    public static class ConnectException extends XMPPException {
        public ConnectException(Exception e) {
            super(e);
        }
    }

    public static class NotConnectedException extends XMPPException {
        public NotConnectedException(Exception e) {
            super(e);
        }
    }
}
