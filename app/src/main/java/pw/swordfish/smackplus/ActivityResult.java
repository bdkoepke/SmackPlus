package pw.swordfish.smackplus;

import android.app.Activity;

enum ActivityResult {
    RESULT_CANCELED(Activity.RESULT_CANCELED),
    RESULT_OK(Activity.RESULT_OK);

    private int code;
    private ActivityResult(int code) {
        this.code = code;
    }
    public int getCode() {
        return this.code;
    }
}