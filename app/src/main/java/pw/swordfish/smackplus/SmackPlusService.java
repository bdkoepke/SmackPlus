package pw.swordfish.smackplus;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.ISms;

import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;

import pw.swordfish.rx.Observer;
import pw.swordfish.sms.Transceiver;
import pw.swordfish.util.function.Supplier;
import pw.swordfish.util.function.Suppliers;
import pw.swordfish.xmpp.ConnectException;
import pw.swordfish.xmpp.XmppTransceiver;

import static pw.swordfish.util.Unsafe.cast;

public class SmackPlusService extends Service {
    public static final String ACTION_INCOMING_SMS = SmackPlusService.class.getPackage().getName() + ".INCOMING_VOICE";
    public static final String ACCOUNT_CHANGED = SmackPlusService.class.getPackage().getName() + ".ACCOUNT_CHANGED";

    private static final String LOGTAG = SmackPlusService.class.getName();

    /* ensure that this notification listener is enabled.
     * the service watches for google voice notifications to know when to check for new
     * messages. */
    private final BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // refresh inbox if connectivity returns
            if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false))
                return;
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                // TODO: should do something...
                Log.i(LOGTAG, "Network is available, should be reconnecting...");
            }
        }
    };
    /* hook into sms manager to be able to synthesize SMS events.
     * new messages from smack plus get mocked out as real SMS events in Android. */
    private final Supplier<ISms> smsTransport = Suppliers.memoize(new Supplier<ISms>() {
        @Override
        public ISms get() {
            try {
                Class sm = Class.forName("android.os.ServiceManager");
                @SuppressWarnings("unchecked") Method getService = sm.getMethod("getService", String.class);
                return ISms.Stub.asInterface((IBinder) getService.invoke(null, "isms"));
            } catch (Exception e) {
                Log.e(LOGTAG, "register error", e);
                return null;
            }
        }
    });
    private Transceiver transceiver;


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void ensureEnabled() {
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mConnectivityReceiver);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        //SharedPreferences settings = getSharedPreferences("settings", MODE_PRIVATE);
        try {
            transceiver = XmppTransceiver.connect("s.ms", "2508784414", "TkynmSR2nqz3ooq", new PacketFilter() {
                @Override
                public boolean accept(Packet packet) {
                    if (! (packet instanceof Message))
                        return false;
                    Message message = cast(packet);
                    return ! message.getBody().startsWith("(SMSSERVICE)");
                }
            });
        } catch (ConnectException e) {
            Log.e(LOGTAG, "Could not connect", e);
        }

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mConnectivityReceiver, filter);

        Log.i(LOGTAG, "Subscribing new observer to the incoming sms transceiver");
        // TODO: fix memory leak...
        transceiver.subscribe(new Observer<pw.swordfish.sms.Message>() {
            @Override
            public void onCompleted() {
                Log.i(LOGTAG, "closing observer");
            }

            @Override
            public void onError(Exception error) {
            }

            @Override
            public void onNext(pw.swordfish.sms.Message value) {
                insertMessage(value.getAddress(), value.getBody(), ProviderSms.INCOMING, value.getTimestamp());
                synthesizeMessage(value.getAddress(), value.getBody(), value.getTimestamp());
            }
        });
    }

    /* parse out the intent extras from android.intent.action.NEW_OUTGOING_SMS
     * and send it off via xmpp */
    private void handleOutgoingSms(Intent intent) {
        /* boolean multipart = intent.getBooleanExtra("multipart", false);
         String scAddr = intent.getStringExtra("scAddr");
         ArrayList<PendingIntent> deliveryIntents = intent.getParcelableArrayListExtra("deliveryIntents"); */

        String destAddr = intent.getStringExtra("destAddr");
        ArrayList<String> parts = intent.getStringArrayListExtra("parts");
        ArrayList<PendingIntent> sentIntents = intent.getParcelableArrayListExtra("sentIntents");

        onSendText(destAddr, parts, sentIntents);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        int startContinuationMask = super.onStartCommand(intent, flags, startId);

        // TODO: get account info...
        if (intent == null)
            return startContinuationMask;

        ensureEnabled();

        // handle an outgoing sms on a background thread.
        if ("android.intent.action.NEW_OUTGOING_SMS".equals(intent.getAction())) {
            new Thread() {
                @Override
                public void run() {
                    handleOutgoingSms(intent);
                }
            }.start();
        } else if (ACTION_INCOMING_SMS.equals(intent.getAction())) {
            return startContinuationMask;

        }
        return startContinuationMask;
    }

    private static void markIntents(Iterable<PendingIntent> sentIntents, ActivityResult result) {
        if (sentIntents == null)
            return;
        for (PendingIntent sentIntent : sentIntents) {
            if (sentIntent == null)
                continue;
            try {
                sentIntent.send(result.getCode());
            } catch (Exception ignored) {}
        }
    }

    private static String join(Iterable<String> sequences) {
        StringBuilder join = new StringBuilder();
        for (String sequence : sequences)
            join.append(sequence);
        return join.toString();
    }

    /* sent an outgoing message via xmpp */
    private void onSendText(String address, Iterable<String> texts, final Iterable<PendingIntent> sentIntents) {
        try {
            transceiver.send(pw.swordfish.sms.Message.createMessage(address, join(texts), new Date().getTime()));
            markIntents(sentIntents, ActivityResult.RESULT_OK);
        } catch (Exception e) {
            Log.d(LOGTAG, "send error", e);
            markIntents(sentIntents, ActivityResult.RESULT_CANCELED);
        }
    }

    /* insert a message into the sms/mms provider.
     * we do this in the case of outgoing messages
     * that were not sent via this phone, and also on initial
     * message sync. */
    private synchronized void insertMessage(String number, String text, ProviderSms provider, long date) {
        try (Cursor c = getContentResolver().query(provider.getUri(), null, "date = ?",
                new String[]{String.valueOf(date)}, null)) {
            if (c.moveToNext())
                return;
        }

        ContentValues values = new ContentValues();
        values.put("address", number);
        values.put("body", text);
        values.put("type", provider.getType());
        values.put("date", date);
        values.put("date_sent", date);
        values.put("read", 1);
        getContentResolver().insert(provider.getUri(), values);
    }

    synchronized void synthesizeMessage(String number, String message, long date) {
        try (Cursor c = getContentResolver().query(ProviderSms.INCOMING.getUri(), null, "date = ?",
                new String[]{String.valueOf(date)}, null)) {
            if (c.moveToNext())
                return;
        }

        ArrayList<String> list = new ArrayList<>();
        list.add(message);
        try {
            // synthesize a BROADCAST_SMS event
            smsTransport.get().synthesizeMessages(number, null, list, date);
        } catch (Exception e) {
            Log.e(LOGTAG, "Error synthesizing SMS messages", e);
        }
    }
}
