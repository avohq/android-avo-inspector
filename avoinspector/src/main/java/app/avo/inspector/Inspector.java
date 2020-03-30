package app.avo.inspector;

import android.content.Context;

import org.json.JSONObject;

import java.util.Map;

public interface Inspector {

    Map<String, AvoEventSchemaType> trackSchemaFromEvent(String eventName, JSONObject eventProperties);

    Map<String, AvoEventSchemaType> trackSchemaFromEvent(String eventName, Map<?, ?> eventProperties);

    Map<String, AvoEventSchemaType> trackSchemaFromEvent(String eventName, Object eventProperties);

    void trackSchema(String eventName, Map<String, AvoEventSchemaType> eventSchema);

    void enableLogging(boolean enable);

    Map<String, AvoEventSchemaType> extractSchema(Object eventProperties);
}
