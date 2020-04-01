package app.avo.inspector;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.TimeUnit;

class AvoInspectorSessionTracker {

    long lastSessionTimestamp;
    long sessionLength = TimeUnit.MINUTES.toMillis(5);

    static final String sessionStartKey = "avo_inspector_session_start_key";

    private SharedPreferences sharedPreferences;
    private AvoInspectorBatcher avoBatcher;

    AvoInspectorSessionTracker(Context context, AvoInspectorBatcher avoBatcher) {
        this.sharedPreferences = context.getSharedPreferences(Util.AVO_SHARED_PREFS_KEY, Context.MODE_PRIVATE);
        this.lastSessionTimestamp = this.sharedPreferences.getLong(sessionStartKey, -1L);
        this.avoBatcher = avoBatcher;
    }

    void startOrProlongSession(long triggeredTimeMillis) {
        long timeSinceLastSession = triggeredTimeMillis -  lastSessionTimestamp;
        if (timeSinceLastSession > sessionLength) {
            avoBatcher.batchSessionStarted();
        }

        lastSessionTimestamp = System.currentTimeMillis();
        sharedPreferences.edit().putLong(sessionStartKey, lastSessionTimestamp).apply();
    }
}
