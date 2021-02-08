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
    VisualInspector(@Nullable AvoInspectorEnv env, @Nullable Application application, @Nullable Activity rootActivityForVisualInspector) {
    }

    @Nullable
    public Object getDebuggerManager() {
        return null;
    }

    void show(Activity rootActivity, VisualInspectorMode visualInspectorMode) {
    }

    void hide(Activity rootActivity) {
    }

    void showEventInVisualInspector(String eventName, @Nullable Map<String, ?> mapParams, @Nullable JSONObject jsonParams) {
    }

    void showSchemaInVisualInspector(String eventName, Map<String, AvoEventSchemaType> schema) {
    }
}
