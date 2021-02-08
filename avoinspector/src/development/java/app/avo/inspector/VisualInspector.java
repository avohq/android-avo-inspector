package app.avo.inspector;

import android.app.Activity;
import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import app.avo.androidanalyticsdebugger.DebuggerManager;
import app.avo.androidanalyticsdebugger.DebuggerMode;
import app.avo.androidanalyticsdebugger.EventProperty;
import app.avo.androidanalyticsdebugger.PropertyError;

public class VisualInspector {
    @Nullable
    DebuggerManager debugger;

    public VisualInspector(@NonNull AvoInspectorEnv env, @NonNull Application application, @Nullable Activity rootActivityForVisualInspector) {
        if (env != AvoInspectorEnv.Prod) {
            debugger = new DebuggerManager(application);
            if (rootActivityForVisualInspector != null) {
                show(rootActivityForVisualInspector, VisualInspectorMode.BUBBLE);
            }
        }
    }

    public @Nullable
    Object getDebuggerManager() {
        return debugger;
    }

    void show(Activity rootActivity, VisualInspectorMode visualInspectorMode) {
        if (debugger == null) {
            debugger = new DebuggerManager(rootActivity.getApplication());
        }
        debugger.showDebugger(rootActivity, visualInspectorMode == VisualInspectorMode.BAR ? DebuggerMode.bar : DebuggerMode.bubble);
    }

    void hide(Activity rootActivity) {
        if (debugger != null) {
            debugger.hideDebugger(rootActivity);
        }
    }

    @SuppressWarnings("rawtypes")
    void showEventInVisualInspector(String eventName, @Nullable Map<String, ?> mapParams, @Nullable JSONObject jsonParams) {
        List<EventProperty> props = new ArrayList<>();
        if (mapParams != null) {
            for (Map.Entry<String, ?> param : mapParams.entrySet()) {
                String name = param.getKey();
                Object value = param.getValue();
                if (name != null) {
                    String valueDescription;
                    if (value == null) {
                        valueDescription = "null";
                    } else if (value instanceof List) {
                        valueDescription = new JSONArray((List) value).toString();
                    } else if (value instanceof Map) {
                        try {
                            valueDescription = new JSONObject((Map) value).toString(1)
                                    .replace("\n", "")
                                    .replace("\\", "");
                        } catch (JSONException ex) {
                            valueDescription = new JSONObject((Map) value).toString()
                                    .replace("\\", "");
                        }
                    } else {
                        valueDescription = value.toString();
                    }
                    props.add(new EventProperty("", name, valueDescription));
                }
            }
        }
        if (jsonParams != null) {
            for (Iterator<String> it = jsonParams.keys(); it.hasNext(); ) {
                String name = it.next();
                try {
                    Object value = jsonParams.get(name);
                    props.add(new EventProperty("", name, value != JSONObject.NULL ? value.toString() : "null"));
                } catch (JSONException ignored) {
                }
            }
        }

        if (debugger != null) {
            debugger.publishEvent(System.currentTimeMillis(), "Event: " + eventName, props, new ArrayList<PropertyError>());
        }
    }

    void showSchemaInVisualInspector(String eventName, Map<String, AvoEventSchemaType> schema) {
        List<EventProperty> props = new ArrayList<>();
        for (Map.Entry<String, AvoEventSchemaType> param : schema.entrySet()) {
            String name = param.getKey();
            AvoEventSchemaType value = param.getValue();
            if (name != null) {
                props.add(new EventProperty("", name, value != null ? value.getReadableName() : "null"));
            }
        }
        if (debugger != null) {
            debugger.publishEvent(System.currentTimeMillis(), "Schema: " + eventName, props, new ArrayList<PropertyError>());
        }
    }
}
