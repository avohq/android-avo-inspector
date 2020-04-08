package app.avo.inspector;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.util.Map;

import app.avo.androidanalyticsdebugger.DebuggerMode;

@SuppressWarnings("UnusedReturnValue")
public interface Inspector {

    void showVisualInspector(Activity rootActivity, DebuggerMode debuggerMode);

    void hideVisualInspector(Activity rootActivity);

    Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, JSONObject eventProperties);

    Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, Map<String, ?> eventProperties);

    void trackSchema(@NonNull String eventName, @NonNull Map<String, AvoEventSchemaType> eventSchema);

    Map<String, AvoEventSchemaType> extractSchema(Object eventProperties);
}
