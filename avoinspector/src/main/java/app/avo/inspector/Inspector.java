package app.avo.inspector;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Map;

@SuppressWarnings("UnusedReturnValue")
public interface Inspector {

    void showVisualInspector(@NonNull Activity rootActivity, @NonNull VisualInspectorMode visualInspectorMode);

    void hideVisualInspector(@NonNull Activity rootActivity);

    @NonNull
    Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, @Nullable JSONObject eventProperties);

    @NonNull
    Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, @Nullable Map<String, ?> eventProperties);

    void trackSchema(@NonNull String eventName, @Nullable Map<String, AvoEventSchemaType> eventSchema);

    @NonNull
    Map<String, AvoEventSchemaType> extractSchema(@Nullable Object eventProperties);

    @Nullable
    Object getVisualInspector();
}
