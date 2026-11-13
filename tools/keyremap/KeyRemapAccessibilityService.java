package de.pi.p610.keyremap;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Always-running accessibility service (enabled once in Settings ->
 * Accessibility). It registers a *runtime* receiver for
 * android.intent.action.GLOBAL_BUTTON, which is exempt from the
 * implicit-broadcast restriction that blocks manifest receivers in
 * non-system apps (Android 8+).
 *
 * Mapping: keyCode 265 (R601 Netflix button) -> Jellyfin.
 */
public class KeyRemapAccessibilityService extends AccessibilityService {
    private static final String TAG = "P610KeyRemap";

    /** keyCode -> target package (edit + rebuild to change) */
    private static final int[]    KEYCODES = { 265 };
    private static final String[] TARGETS  = { "org.jellyfin.androidtv" };

    private final BroadcastReceiver rcv = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            Bundle b = intent.getExtras();
            if (b == null) return;
            KeyEvent ke = b.getParcelable("android.intent.extra.KEY_EVENT");
            if (ke == null) return;

            int keyCode = ke.getKeyCode();
            Log.i(TAG, "GLOBAL_BUTTON keyCode=" + keyCode
                    + " action=" + ke.getAction());

            if (ke.getAction() != KeyEvent.ACTION_UP) return;
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
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        registerReceiver(rcv, new IntentFilter("android.intent.action.GLOBAL_BUTTON"));
        Log.i(TAG, "service connected, runtime receiver registered");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int t = event.getEventType();
        // TYPE_VIEW_KEY_CLICKED == 2001, TYPE_WINDOW_STATE_CHANGED == 32
        if (t == 2001 || t == 32) {
            Log.i(TAG, "accessEvent type=" + t + " pkg=" + event.getPackageName());
        }
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        Log.i(TAG, "onKeyEvent kc=" + event.getKeyCode()
                + " action=" + event.getAction());
        return super.onKeyEvent(event);
    }

    @Override
    public void onInterrupt() {
        // not used
    }

    @Override
    public boolean onUnbind(Intent intent) {
        try { unregisterReceiver(rcv); } catch (Exception ignored) {}
        Log.i(TAG, "service unbound, receiver unregistered");
        return super.onUnbind(intent);
    }
}
