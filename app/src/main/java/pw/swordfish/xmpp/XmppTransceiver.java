package pw.swordfish.xmpp;

import android.telephony.PhoneNumberUtils;

import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import pw.swordfish.rx.Observer;
import pw.swordfish.sms.MalformedSmsAddress;
import pw.swordfish.sms.Transceiver;
import pw.swordfish.util.Unsafe;

public class XmppTransceiver implements Transceiver {
    private final XMPPTCPConnection connection;
    private final Set<Observer<pw.swordfish.sms.Message>> observers = new HashSet<>();

    private XmppTransceiver(XMPPTCPConnection connection, PacketFilter messageFilter) {
        this.connection = connection;
        PacketListener observablePacketListener = new PacketListener() {
            @Override
            public void processPacket(Packet packet) throws SmackException.NotConnectedException {
                pw.swordfish.sms.Message sms;
                try {
                    sms = xmppToSms(Unsafe.<Message>cast(packet));
                } catch (MalformedSmsAddress ignored) {
                    return;
                }
                for (Observer<pw.swordfish.sms.Message> observer : observers)
                    observer.onNext(sms);
            }
        };
        this.connection.addPacketListener(observablePacketListener, messageFilter);
    }

    private static Message smsToXmpp(pw.swordfish.sms.Message sms) {
        Message xmpp = new Message(PhoneNumberUtils.toaFromString(sms.getAddress()) + "@sms");
        xmpp.setBody(sms.getBody());
        return xmpp;
    }

    private static pw.swordfish.sms.Message xmppToSms(Message xmpp) throws MalformedSmsAddress {
        if (!xmpp.getTo().endsWith("@sms"))
            throw new MalformedSmsAddress("Sms address {0} is not well formed", xmpp.getTo());
        String to = xmpp.getTo().replace("@sms", "");
        // TODO: check the xmlns to see if there is a "delay" field which is the only timestamp:
        // https://stackoverflow.com/questions/11138627/how-to-get-timestamp-of-incoming-xmpp-message
        return pw.swordfish.sms.Message.createMessage(to, xmpp.getBody(), new Date().getTime());
    }

    public static XmppTransceiver connect(String serviceName, String username, String password, PacketFilter messageFilter) throws ConnectException {
        XMPPTCPConnection connection = new XMPPTCPConnection(serviceName);
        try {
            connection.connect();
            connection.login(username, password, "Android");
        } catch (org.jivesoftware.smack.XMPPException | SmackException | IOException e) {
            throw new ConnectException(e);
        }
        return new XmppTransceiver(connection, messageFilter);
    }

    public void send(pw.swordfish.sms.Message sms) throws SendException {
        try {
            connection.sendPacket(smsToXmpp(sms));
        } catch (SmackException.NotConnectedException e) {
            throw new SendException(e);
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

    @Override
    public AutoCloseable subscribe(final Observer<pw.swordfish.sms.Message> observer) {
        this.observers.add(observer);
        return new AutoCloseable() {
            @Override
            public void close() throws Exception {
                observers.remove(observer);
            }
        };
    }
}