package app.avo.inspector;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.util.Map;

@SuppressWarnings("UnusedReturnValue")
public interface Inspector {

    Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, JSONObject eventProperties);

    Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, Map<?, ?> eventProperties);

    Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, Object eventProperties);

    void trackSchema(@NonNull String eventName, @NonNull Map<String, AvoEventSchemaType> eventSchema);

    Map<String, AvoEventSchemaType> extractSchema(Object eventProperties);
}
