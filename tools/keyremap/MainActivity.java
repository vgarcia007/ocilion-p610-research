package de.pi.p610.keyremap;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.view.Gravity;
import android.graphics.Color;

/**
 * Minimal foreground activity:
 *  - un-stops the package so the broadcast receiver gets GLOBAL_BUTTON
 *  - documents the active mapping
 *  - button: test-launch the target app
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        t.setPadding(64, 64, 64, 64);
        t.setText("P610 Key Remap\n\n"
             + "Netflix-Taste (keyCode 265) -> Jellyfin\n\n"
             + "Mapping aktiv, solange diese App installiert ist.\n"
             + "Test: Jellyfin jetzt starten:\n");
        t.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(getPackageManager().getLaunchIntentForPackage("org.jellyfin.androidtv"));
            }
        });
        setContentView(t);
    }
}
