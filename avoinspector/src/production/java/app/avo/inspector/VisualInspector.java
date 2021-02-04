package app.avo.inspector;

import android.app.Activity;
import android.app.Application;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Map;

/**
 * Empty implementation in the production flavour. Visual inspector is only meant to be used in the development mode.
 */
public class VisualInspector {
    public VisualInspector(AvoInspectorEnv env, Application application, Activity rootActivityForVisualInspector) {
    }

    public @Nullable Object getDebuggerManager() {
        return null;
    }

    void show(Activity rootActivity, VisualInspectorMode visualInspectorMode) {
    }

    void hide(Activity rootActivity) {
    }

    @SuppressWarnings("rawtypes")
    void showEventInVisualInspector(String eventName, @Nullable Map<String, ?> mapParams, @Nullable JSONObject jsonParams) {
    }

    void showSchemaInVisualInspector(String eventName, Map<String, AvoEventSchemaType> schema) {
    }
}