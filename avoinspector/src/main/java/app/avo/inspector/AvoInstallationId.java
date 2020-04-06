package app.avo.inspector;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

class AvoInstallationId {

    String installationId;

    static String cacheKey = "AvoInspectorInstallationIdKey";

    AvoInstallationId(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Util.AVO_SHARED_PREFS_KEY, Context.MODE_PRIVATE);
        installationId = sharedPreferences.getString(cacheKey, UUID.randomUUID().toString());
    }
}
