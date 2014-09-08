package pw.swordfish.smackplus;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.*;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.ISms;
import pw.swordfish.xmpp.XMPPClient;
import pw.swordfish.xmpp.XMPPException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class SmackPlusService extends Service {
    public static final String ACTION_INCOMING_SMS = SmackPlusService.class.getPackage().getName() + ".INCOMING_SMS";
    private static final int VOICE_INCOMING_SMS = 10;
    private static final int VOICE_OUTGOING_SMS = 11;
    private static final int PROVIDER_INCOMING_SMS = 1;
    private static final int PROVIDER_OUTGOING_SMS = 2;
    private static final Uri URI_SENT = Uri.parse("content://sms/sent");
    private static final Uri URI_RECEIVED = Uri.parse("content://sms/inbox");
    BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // refresh inbox if connectivity returns
            if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false))
                return;
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            if (activeNetworkInfo != null)
                startRefresh();
        }
    };
    private ISms smsTransport;
    private XMPPClient client;

    private static void e(String message, Exception e) {
        Log.e("SmackPlusService", message, e);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Ensure that this notification listener is enabled.
     * The service watches for jabber notifications to know when to check for new
     * messages.
     */
    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private void ensureEnabled() {
        //Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
        final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";

        ComponentName me = new ComponentName(this, SmackListenerService.class);
        String meFlattened = me.flattenToString();

        String existingListeners = Settings.Secure.getString(getContentResolver(),
                ENABLED_NOTIFICATION_LISTENERS);

        if (!TextUtils.isEmpty(existingListeners))
            if (existingListeners.contains(meFlattened))
                return;
            else
                existingListeners += ":" + meFlattened;
        else
            existingListeners = meFlattened;

        Settings.Secure.putString(getContentResolver(),
                ENABLED_NOTIFICATION_LISTENERS,
                existingListeners);
    }

    /**
     * Hook into the sms manager in order to be able to synthesize SMS events.
     * New messages from jabber will get mocked out as real SMS events in Android.
     */
    private void registerSmsMiddleware() {
        if (smsTransport != null)
            return;
        try {
            Class serviceManager = Class.forName("android.os.ServiceManager");
            @SuppressWarnings("unchecked")
            Method getService = serviceManager.getMethod("getService", String.class);
            smsTransport = ISms.Stub.asInterface((IBinder) getService.invoke(null, "isms"));
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e("register error", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mConnectivityReceiver);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        registerSmsMiddleware();

        try {
            registerXmppClient();
        } catch (XMPPException.ConnectException ignore) {
            throw new RuntimeException("Failed to register xmpp client");
        }

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mConnectivityReceiver, filter);

        startRefresh();
    }

    private void registerXmppClient() throws XMPPException.ConnectException {
        if (client == null)
            client = XMPPClient.connect("s.ms", "username", "password");
    }

    /**
     * Parse out the intent extras from android.intent.action.NEW_OUTGOING_SMS
     * and send it off via jabber
     */
    void handleOutgoingSms(Intent intent) {
        boolean multipart = intent.getBooleanExtra("multipart", false);
        String destAddr = intent.getStringExtra("destAddr");
        String scAddr = intent.getStringExtra("scAddr");
        List<String> parts = intent.getStringArrayListExtra("parts");
        List<PendingIntent> sentIntents = intent.getParcelableArrayListExtra("sentIntents");
        List<PendingIntent> deliveryIntents = intent.getParcelableArrayListExtra("deliveryIntents");

        onSendMultiPartText(destAddr, scAddr, parts, sentIntents, deliveryIntents, multipart);
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        int startContinuationMask = super.onStartCommand(intent, flags, startId);

        // TODO: add support for account settings

        ensureEnabled();

        if (intent == null)
            return startContinuationMask;

        // handle an outgoing sms on a background thread.
        if ("android.intent.action.NEW_OUTGOING_SMS".equals(intent.getAction()))
            new Thread() {
                @Override
                public void run() {
                    handleOutgoingSms(intent);
                }
            }.start();
        else if (ACTION_INCOMING_SMS.equals(intent.getAction()))
            startRefresh();

        return startContinuationMask;
    }

    /**
     * Send an outgoing sms event via jabber
     */
    public void onSendMultiPartText(String destAddr, String scAddr, List<String> texts, final List<PendingIntent> sentIntents, final List<PendingIntent> deliveryIntents, boolean multipart) {
        StringBuilder textBuilder = new StringBuilder();
        for (String text : texts)
            textBuilder.append(text);
        try {
            client.sendText(destAddr, textBuilder.toString());
            success(sentIntents);
        } catch (XMPPException.NotConnectedException e) {
            fail(sentIntents);
        }
    }

    public void fail(Iterable<PendingIntent> sentIntents) {
        if (sentIntents == null)
            return;
        for (PendingIntent intent : sentIntents) {
            if (intent == null)
                continue;
            try {
                intent.send();
            } catch (PendingIntent.CanceledException ignored) {
            }
        }
    }

    public void success(Iterable<PendingIntent> sentIntents) {
        if (sentIntents == null)
            return;
        for (PendingIntent intent : sentIntents) {
            if (intent == null)
                continue;
            try {
                intent.send(Activity.RESULT_OK);
            } catch (PendingIntent.CanceledException ignored) {
            }
        }
    }

    void refreshMessages() {
        throw new RuntimeException("Not implemented");
    }

    void startRefresh() {
        new Thread() {
            @Override
            public void run() {
                refreshMessages();
            }
        }.start();
    }

    // insert a message into the sms/mms provider.
    // we do this in the case of outgoing messages
    // that were not sent via this phone, and also on initial
    // message sync.
    @TargetApi(Build.VERSION_CODES.KITKAT)
    synchronized void insertMessage(String number, String text, int type, long date) {
        Uri uri;
        if (type == PROVIDER_INCOMING_SMS)
            uri = URI_RECEIVED;
        else
            uri = URI_SENT;

        try (Cursor c = getContentResolver().query(uri, null, "date = ?",
                new String[]{String.valueOf(date)}, null)) {
            if (c.moveToNext())
                return;
        }
        ContentValues values = new ContentValues();
        values.put("address", number);
        values.put("body", text);
        values.put("type", type);
        values.put("date", date);
        values.put("date_sent", date);
        values.put("read", 1);
        getContentResolver().insert(uri, values);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    synchronized void synthesizeMessage(String number, String message, long date) {
        try (Cursor c = getContentResolver().query(URI_RECEIVED, null, "date = ?",
                new String[]{String.valueOf(date)}, null)) {
            if (c.moveToNext())
                return;
        }

        ArrayList<String> list = new ArrayList<>();
        list.add(message);
        try {
            // synthesize a BROADCAST_SMS event
            smsTransport.synthesizeMessages(number, null, list, date);
        } catch (Exception e) {
            e("Error synthesizing SMS messages", e);
        }
    }
}
