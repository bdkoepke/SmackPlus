package pw.swordfish.smackplus;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

public class PackageChangeReceiver extends BroadcastReceiver {

    private void setComponentEnabledSetting(PackageManager pm, int componentEnabledState, ComponentName... components) {
        for (ComponentName component : components)
            pm.setComponentEnabledSetting(component, componentEnabledState, 0);
    }

    /*
    private boolean canGetPackageInfo(PackageManager pm, String packageName) {
        try {
            pm.getPackageInfo(packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    */

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null)
            return;

        PackageManager pm = context.getPackageManager();
        if (pm == null)
            return;

        ComponentName
                listenerService = new ComponentName(context, SmackListenerService.class),
                service = new ComponentName(context, SmackPlusService.class),
                receiver = new ComponentName(context, OutgoingSmsReceiver.class),
                activity = new ComponentName(context, SmackPlusSetup.class);

        /*
        if (! canGetPackageInfo(pm, Helper.SMACK_PLUS_PACKAGE)) {
            setComponentEnabledSetting(pm, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, activity, service, receiver, listenerService);
            return;
        }
        */

        SharedPreferences settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (!settings.getBoolean("pestered", false)) {
            Notification.Builder builder = new Notification.Builder(context);
            Notification n = builder
                    .setSmallIcon(R.drawable.stat_sys_splus)
                    .setContentText(context.getString(R.string.enable_smack_plus))
                    .setTicker(context.getString(R.string.enable_smack_plus))
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, SmackPlusSetup.class), 0))
                    .build();
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(1000, n);
            settings.edit().putBoolean("pestered", true).apply();
        }

        setComponentEnabledSetting(pm, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, activity, service, receiver, listenerService);
        context.startService(new Intent(context, SmackPlusService.class));
    }
}
