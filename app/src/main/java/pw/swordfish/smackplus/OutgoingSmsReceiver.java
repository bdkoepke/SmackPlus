package pw.swordfish.smackplus;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class OutgoingSmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: add account info

        if (!canDeliverToAddress(context, intent)) {
            String destination = intent.getStringExtra("destAddr");
            if (destination == null)
                destination = "(null)";
            Log.d("OutgoingSmsReceiver", "Sending <" + destination + "> via cellular instead of SmackPlus.");
            return;
        }

        abortBroadcast();
        setResultCode(Activity.RESULT_CANCELED);

        intent.setClass(context, SmackPlusService.class);
        context.startService(intent);
    }

    private boolean canDeliverToAddress(Context context, Intent intent) {
        return true;
    }
}
