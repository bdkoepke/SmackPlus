package pw.swordfish.smackplus;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class SmackListenerService extends NotificationListenerService {
    //SharedPreferences settings;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        /* if (!Helper.SMACK_PLUS_PACKAGE.equals(sbn.getPackageName()))
            return;
        if (settings == null)
            settings = getSharedPreferences("settings", MODE_PRIVATE);
        if (null == settings.getString("account", null))
            return;
        //cancelNotification(Helper.SMACK_PLUS_PACKAGE, sbn.getTag(), sbn.getId());
        startService(new Intent(this, SmackPlusService.class).setAction(SmackPlusService.ACTION_INCOMING_SMS)); */
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
    }
}
