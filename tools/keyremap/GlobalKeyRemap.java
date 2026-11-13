package de.pi.p610.keyremap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Remaps R601 "global" remote buttons (the ones the box intercepts as
 * android.intent.action.GLOBAL_BUTTON) to arbitrary apps.
 *
 * v1 mapping (change via:
 *   adb shell cmd appops / or just edit MAP below and rebuild):
 *   265 (Netflix button) -> org.jellyfin.androidtv
 */
public class GlobalKeyRemap extends BroadcastReceiver {
    private static final String TAG = "P610KeyRemap";

    /** keyCode -> target package */
    private static final int[]    KEYCODES = { 265 };
    private static final String[] TARGETS  = { "org.jellyfin.androidtv" };

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Bundle b = intent.getExtras();
        if (b == null) return;

        KeyEvent ke = b.getParcelable("android.intent.extra.KEY_EVENT");
        if (ke == null) return;

        int keyCode = ke.getKeyCode();
        int action  = ke.getAction();

        Log.i(TAG, "GLOBAL_BUTTON keyCode=" + keyCode + " action=" + action);

        if (action != KeyEvent.ACTION_UP) return;

        for (int i = 0; i < KEYCODES.length; i++) {
            if (keyCode == KEYCODES[i]) {
                Intent launch = ctx.getPackageManager()
                        .getLaunchIntentForPackage(TARGETS[i]);
                if (launch == null) {
                    Log.w(TAG, "target not installed: " + TARGETS[i]);
                    return;
                }
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                ctx.startActivity(launch);
                Log.i(TAG, "launched " + TARGETS[i]);
                return;
            }
        }
    }
}
