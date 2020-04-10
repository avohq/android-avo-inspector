package app.avo.inspector;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Map;

import app.avo.androidanalyticsdebugger.DebuggerMode;

@SuppressWarnings("UnusedReturnValue")
public interface Inspector {

    void showVisualInspector(Activity rootActivity, DebuggerMode visualInspectorMode);

    void hideVisualInspector(Activity rootActivity);

    @NonNull Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, @Nullable JSONObject eventProperties);

    @NonNull Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, @Nullable Map<String, ?> eventProperties);

    void trackSchema(@NonNull String eventName, @Nullable Map<String, AvoEventSchemaType> eventSchema);

    @NonNull Map<String, AvoEventSchemaType> extractSchema(@Nullable Object eventProperties);
}
