package pw.swordfish.smackplus;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class SmackPlusService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
