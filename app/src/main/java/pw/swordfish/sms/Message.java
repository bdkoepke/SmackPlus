package pw.swordfish.sms;

import android.telephony.PhoneNumberUtils;

import java.util.Date;

import pw.swordfish.util.Contract;

public class Message {
    private final String address;
    private final String body;
    private final long timestamp;

    private Message(String address, String body, long timestamp) {
        this.address = address;
        this.body = body;
        this.timestamp = timestamp;
    }

    public static Message createMessage(String address, String body, long timestamp) throws MalformedSmsAddress {
        Contract.requiresNonNull(address, "address");
        if (! PhoneNumberUtils.isWellFormedSmsAddress(Contract.requiresNonNull(address, "address")))
            throw new MalformedSmsAddress("Sms address {0} is not well formed", address);
        // TODO: further verification for the timestamp
        return new Message(address, Contract.requiresNonNull(body, "body"), Contract.requiresNonNull(timestamp, "timestamp"));
    }

    public String getAddress() {
        return this.address;
    }
    public String getBody() {
        return this.body;
    }
    public long getTimestamp() {
        return this.timestamp;
    }
}
