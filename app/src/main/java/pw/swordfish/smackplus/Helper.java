package pw.swordfish.smackplus;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.PowerManager;

public class Helper {
    public static final String SMACK_PLUS_PACKAGE = "pw.swordfish.smackplus";

    private static PowerManager.WakeLock wakeLock;
    private static WifiManager.WifiLock wifiLock;

    public static void acquireTemporaryWakelocks(Context context, long timeout) {
        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "PushSMS");
        }
        wakeLock.acquire(timeout);

        if (wifiLock == null) {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            wifiLock = wifiManager.createWifiLock("PushSMS");
        }

        wifiLock.acquire();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                wifiLock.release();
            }
        }, timeout);
    }
}
