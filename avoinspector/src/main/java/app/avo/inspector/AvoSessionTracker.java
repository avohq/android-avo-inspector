package app.avo.inspector;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

class AvoSessionTracker {

    static String sessionId = "";

    long lastSessionTimestamp;
    long sessionMillis = TimeUnit.MINUTES.toMillis(5);

    static final String sessionStartKey = "avo_inspector_session_start_key";
    static final String sessionIdKey = "avo_inspector_session_id_key";

    private SharedPreferences sharedPreferences;
    private AvoBatcher avoBatcher;

    AvoSessionTracker(Context context, AvoBatcher avoBatcher) {
        this.sharedPreferences = context.getSharedPreferences(Util.AVO_SHARED_PREFS_KEY, Context.MODE_PRIVATE);
        this.lastSessionTimestamp = this.sharedPreferences.getLong(sessionStartKey, -1L);
        this.avoBatcher = avoBatcher;

        sessionId = sharedPreferences.getString(sessionIdKey, null);
        if (sessionId == null) {
            generateSessionId();
        }
    }

    void startOrProlongSession(long triggeredTimeMillis) {
        long timeSinceLastSession = triggeredTimeMillis -  lastSessionTimestamp;
        if (timeSinceLastSession > sessionMillis) {
            generateSessionId();
            avoBatcher.batchSessionStarted();
        }

        lastSessionTimestamp = System.currentTimeMillis();
        sharedPreferences.edit().putLong(sessionStartKey, lastSessionTimestamp).apply();
    }

    private void generateSessionId() {
        sessionId = UUID.randomUUID().toString();
        sharedPreferences.edit().putString(sessionIdKey, sessionId).apply();
    }
}
