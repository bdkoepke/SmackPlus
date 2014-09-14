package pw.swordfish.smackplus;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class OutgoingSmsReceiver extends BroadcastReceiver {
    private static final String LOGTAG = "OutgoingSmsReceiver";

    private boolean canDeliverToAddress(Intent intent) {
        String address = intent.getStringExtra("destAddr");
        return address != null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        /* if (context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("account", null) == null)
            return; */

        if (!canDeliverToAddress(intent)) {
            String destination = intent.getStringExtra("destAddr");
            if (destination == null)
                destination = "(null)";
            Log.d(LOGTAG, "Sending <" + destination + "> via cellular instead of Smack Plus.");
            return;
        }

        abortBroadcast();
        setResultCode(Activity.RESULT_CANCELED);

        intent.setClass(context, SmackPlusService.class);
        context.startService(intent);
    }
}
