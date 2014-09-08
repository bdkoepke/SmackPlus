package pw.swordfish.smackplus;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class SmackListenerService extends NotificationListenerService {
    SharedPreferences settings;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        if (!Helper.SMACK_PLUS_PACKAGE.equals(statusBarNotification.getPackageName()))
            return;
        if (settings == null)
            settings = getSharedPreferences("settings", MODE_PRIVATE);
        // TODO: account settings :(
        /* if (null == settings.getString("account", null))
            return; */
        cancelNotification(Helper.SMACK_PLUS_PACKAGE, statusBarNotification.getTag(), statusBarNotification.getId());
        startService(new Intent(this, SmackPlusService.class).setAction(SmackPlusService.ACTION_INCOMING_SMS));
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {
    }
}
