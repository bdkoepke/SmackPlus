package pw.swordfish.sms;

public interface Sender {
    void send(Message message) throws SendException;

    public static class SendException extends Exception {
        public SendException(Exception e) {
            super(e);
        }
    }
}
