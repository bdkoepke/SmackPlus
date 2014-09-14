package pw.swordfish.smackplus;

import android.net.Uri;

enum ProviderSms {
    INCOMING(1, "inbox"),
    OUTGOING(2, "sent");

    private int type;
    private Uri uri;
    private ProviderSms(int type, String folder) {
        this.type = type;
        this.uri = Uri.parse("content://sms/" + folder);
    }
    public int getType() {
        return this.type;
    }
    public Uri getUri() {
        return this.uri;
    }
}
