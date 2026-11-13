package de.pi.p610.keyremap;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * "Window hijack" remapper: watches for a window-state-change of the
 * app that the SYSTEM launches for a physical remote button, and
 * immediately starts the target app instead.
 *
 * Works for category-A R601 buttons (Netflix/YouTube/Prime/TV-Guide),
 * which are intercepted by com.sdt.globalkey and are invisible to
 * broadcast receivers and key event listeners (see docs/remote.md).
 *
 * Mapping (edit + rebuild):
 *   window appears: com.netflix.ninja   -> launch: org.jellyfin.androidtv
 *
 * Toggle behaviour: pressing the button again while ALREADY inside the
 * target app (the previous window was the target) closes it (goes Home).
 * The decision uses the actually observed previous window, not internal
 * state, so it stays correct if the user navigated away in between.
 *
 * A delayed verification relaunches the target if the first
 * startActivity was swallowed (happens on cold start), which removes
 * the "have to press the button twice" effect.
 */
public class KeyRemapAccessibilityService extends AccessibilityService {
    private static final String TAG = "P610KeyRemap";

    /** pairs: [0] = trigger package, [1] = target package */
    private static final String[][] MAP = {
        { "com.netflix.ninja", "org.jellyfin.androidtv" },
    };

    private static final long DEBOUNCE_MS = 3000;
    private static final long VERIFY_DELAY_MS = 2500;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private long lastFire = 0;
    private String lastWindowPkg = null;   // previous focused window

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "hijack service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence cs = event.getPackageName();
        if (cs == null) return;
        final String pkg = cs.toString();

        final String prevWindow = lastWindowPkg;
        lastWindowPkg = pkg;
        Log.d(TAG, "window: " + pkg);

        for (String[] pair : MAP) {
            String trigger = pair[0];
            String target  = pair[1];
            if (!trigger.equals(pkg)) continue;

            long now = System.currentTimeMillis();
            if (now - lastFire < DEBOUNCE_MS) return; // debounce
            lastFire = now;

            if (target.equals(prevWindow)) {
                // user was in the target, pressed the button again -> Home
                logi("toggle: was in " + target + " -> Home");
                Intent home = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(home);
                return;
            }

            logi("hijack: " + trigger + " -> " + target);
            launchVerified(target);
        }
    }

    /** launch target, and if it did not come to the front, launch again */
    private void launchVerified(final String target) {
        Intent launch = getPackageManager()
                .getLaunchIntentForPackage(target);
        if (launch == null) {
            Log.w(TAG, "target not installed: " + target);
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(launch);
        } catch (Throwable t) {
            Log.w(TAG, "launch " + target + " failed: " + t);
            return;
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String top = topPackage();
                if (top == null || !target.equals(top)) {
                    logi("retry launch: " + target + " (top=" + top + ")");
                    try {
                        startActivity(launch);
                    } catch (Throwable t) {
                        Log.w(TAG, "retry " + target + " failed: " + t);
                    }
                }
            }
        }, VERIFY_DELAY_MS);
    }

    /** package name of the currently focused window, or null */
    private String topPackage() {
        try {
            android.view.accessibility.AccessibilityNodeInfo root =
                    getRootInActiveWindow();
            if (root == null) return null;
            CharSequence p = root.getPackageName();
            return p == null ? null : p.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private void logi(String s) { Log.i(TAG, s); }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
