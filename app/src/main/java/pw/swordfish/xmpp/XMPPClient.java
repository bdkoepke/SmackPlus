package pw.swordfish.xmpp;

import android.annotation.TargetApi;
import android.os.Build;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import pw.swordfish.util.Unsafe;

import java.io.IOException;

import static pw.swordfish.util.Unsafe.cast;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class XMPPClient implements AutoCloseable {
    private XMPPConnection connection;

    private XMPPClient(XMPPConnection connection) {
        this.connection = connection;
        LoggingPacketListener listener = new LoggingPacketListener();
        // TODO: make this more dynamic :(
        PacketFilter messageFilter = new PacketFilter() {
            @Override
            public boolean accept(Packet packet) {
                if (!PacketTypeFilter.MESSAGE.accept(packet))
                    return false;
                // ignore messages that start with SMSSERVER because s.ms sends a message
                // with this prefix every time you receive a message from someone not in
                // your contact list.
                return !Unsafe.<Message>cast(packet).getBody().startsWith("(SMSSERVER)");
            }
        };
        this.connection.addPacketSendingListener(listener, messageFilter);
        this.connection.addPacketListener(listener, messageFilter);
    }

    public static XMPPClient connect(String serviceName, String username, String password) throws XMPPException.ConnectException {
        XMPPConnection connection = new XMPPTCPConnection(serviceName);
        try {
            connection.connect();
            connection.login(username, password, "Android");
        } catch (org.jivesoftware.smack.XMPPException | IOException | SmackException e) {
            throw new XMPPException.ConnectException(e);
        }
        return new XMPPClient(connection);
    }

    public void sendText(String phoneNumber, String text) throws XMPPException.NotConnectedException {
        Message message = new Message(phoneNumber + "@sms");
        message.setBody(text);
        try {
            connection.sendPacket(message);
        } catch (SmackException.NotConnectedException e) {
            throw new XMPPException.NotConnectedException(e);
        }
    }

    @Override
    public void close() {
        if (connection.isConnected())
            try {
                connection.disconnect();
            } catch (SmackException.NotConnectedException ignored) {
            }
    }

    private class LoggingPacketListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) throws SmackException.NotConnectedException {
            Message message = cast(packet);
            System.out.println(message.toXML());
        }
    }
}