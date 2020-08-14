package app.avo.inspector;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import app.avo.androidanalyticsdebugger.DebuggerManager;
import app.avo.androidanalyticsdebugger.DebuggerMode;
import app.avo.androidanalyticsdebugger.EventProperty;
import app.avo.androidanalyticsdebugger.PropertyError;

public class AvoInspector implements Inspector {

    private static boolean logsEnabled = false;

    String apiKey;
    Long appVersion = null;
    String appName;
    int libVersion = BuildConfig.VERSION_CODE;

    String env;

    AvoSessionTracker sessionTracker;
    AvoBatcher avoBatcher;
    AvoSchemaExtractor avoSchemaExtractor;

    boolean isHidden = true;

    @Nullable
    DebuggerManager debugger;

    @SuppressWarnings("unused")
    AvoInspector(String apiKey, Application application, String envString, @Nullable Activity rootActivityForVisualInspector) {
        this(apiKey, application,
                envString.toLowerCase().equals("prod") ? AvoInspectorEnv.Prod :
                        envString.toLowerCase().equals("staging") ? AvoInspectorEnv.Staging : AvoInspectorEnv.Dev,
                rootActivityForVisualInspector);
    }

    public AvoInspector(String apiKey, Application application, AvoInspectorEnv env) {
        this(apiKey, application, env, null);
    }

    public AvoInspector(String apiKey, Application application, AvoInspectorEnv env, @Nullable Activity rootActivityForVisualInspector) {
        try {
            PackageManager packageManager = application.getPackageManager();
            PackageInfo pInfo = packageManager.getPackageInfo(application.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                appVersion = pInfo.getLongVersionCode();
            } else {
                appVersion = (long) pInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException ignored) {}

        avoSchemaExtractor = new AvoSchemaExtractor();

        int stringId = application.getApplicationInfo().labelRes;
        appName = stringId == 0 ? application.getApplicationInfo().packageName : application.getString(stringId);

        this.env = env.getName();
        this.apiKey = apiKey;

        AvoInstallationId installationId = new AvoInstallationId(application);
        AvoNetworkCallsHandler networkCallsHandler = new AvoNetworkCallsHandler(
                apiKey, env.getName(), appName, appVersion.toString(), libVersion + "",
                installationId.installationId);
        avoBatcher = new AvoBatcher(application, networkCallsHandler);
        sessionTracker = new AvoSessionTracker(application, avoBatcher);

        if (env == AvoInspectorEnv.Dev) {
            setBatchFlushSeconds(1);
            enableLogging(true);
        } else {
            setBatchFlushSeconds(30);
            enableLogging(false);
        }

        if (env != AvoInspectorEnv.Prod) {
            debugger = new DebuggerManager(application);
            if (rootActivityForVisualInspector != null) {
                showVisualInspector(rootActivityForVisualInspector, DebuggerMode.bubble);
            }
        }

        application.registerActivityLifecycleCallbacks(new EmptyActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (isHidden) {
                    isHidden = false;
                    try {
                        avoBatcher.enterForeground();
                    } catch (Exception e) {
                        Log.e("Avo Inspector", "Something went wrong. Please report to support@avo.app.", e);
                    }
                    try {
                        sessionTracker.startOrProlongSession(System.currentTimeMillis());
                    } catch (Exception e) {
                        Log.e("Avo Inspector", "Something went wrong. Please report to support@avo.app.", e);
                    }
                }
            }
        });

        application.registerComponentCallbacks(new ComponentCallbacks2() {
            @Override
            public void onTrimMemory(int i) {
                if (i == TRIM_MEMORY_UI_HIDDEN) {
                    isHidden = true;
                    try {
                        avoBatcher.enterBackground();
                    } catch (Exception e) {
                        Log.e("Avo Inspector", "Something went wrong. Please report to support@avo.app.", e);
                    }
                }
            }

            @Override
            public void onConfigurationChanged(@NonNull Configuration configuration) {}

            @Override
            public void onLowMemory() {}
        });
    }

    @Override
    public void showVisualInspector(Activity rootActivity, DebuggerMode visualInspectorMode) {
        if (debugger == null) {
            debugger = new DebuggerManager(rootActivity.getApplication());
        }
        debugger.showDebugger(rootActivity, visualInspectorMode);
    }

    @Override
    public void hideVisualInspector(Activity rootActivity) {
        if (debugger != null) {
            debugger.hideDebugger(rootActivity);
        }
    }

    @SuppressWarnings({"unused", "SameParameterValue"})
    @NonNull Map<String, AvoEventSchemaType> avoFunctionTrackSchemaFromEvent(@NonNull String eventName, @Nullable Map<String, ?> eventProperties, @NonNull String eventId, @NonNull String eventHash) {
        try {
            if (AvoDeduplicator.shouldRegisterEvent(eventName, eventProperties, true)) {
                logPreExtract(eventName, eventProperties);
                showEventInVisualInspector(eventName, eventProperties, null);

                Map<String, AvoEventSchemaType> schema = avoSchemaExtractor.extractSchema(eventProperties, false);

                trackSchemaInternal(eventName, schema, eventId, eventHash);

                return schema;
            } else {
                if (isLogging()) {
                    Log.d("Avo Inspector", "[avo] Avo Inspector: Deduplicated event " + eventName);
                }

                return new HashMap<>();
            }
        } catch (Exception e) {
            Log.e("Avo Inspector", "Something went wrong. Please report to support@avo.app.", e);
            return new HashMap<>();
        }
    }

    @Override
    public @NonNull Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, @Nullable JSONObject eventProperties) {
        try {
            if (AvoDeduplicator.shouldRegisterEvent(eventName, Util.jsonToMap(eventProperties), false)) {
                logPreExtract(eventName, eventProperties);
                showEventInVisualInspector(eventName, null, eventProperties);

                Map<String, AvoEventSchemaType> schema = avoSchemaExtractor.extractSchema(eventProperties, false);

                trackSchemaInternal(eventName, schema, null, null);

                return schema;
            } else {
                if (isLogging()) {
                    Log.d("Avo Inspector", "[avo] Avo Inspector: Deduplicated event " + eventName);
                }
                return new HashMap<>();
            }
        } catch (Exception e) {
            Log.e("Avo Inspector", "Something went wrong. Please report to support@avo.app.", e);
            return new HashMap<>();
        }
    }

    @Override
    public @NonNull Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, @Nullable Map<String, ?> eventProperties) {
        try {
            if (AvoDeduplicator.shouldRegisterEvent(eventName, eventProperties, false)) {
                logPreExtract(eventName, eventProperties);
                showEventInVisualInspector(eventName, eventProperties, null);

                Map<String, AvoEventSchemaType> schema = avoSchemaExtractor.extractSchema(eventProperties, false);

                trackSchemaInternal(eventName, schema, null, null);

                return schema;
            } else {
                if (isLogging()) {
                    Log.d("Avo Inspector", "Deduplicated event " + eventName);
                }
                return new HashMap<>();
            }
        } catch (Exception e) {
            Log.e("Avo Inspector", "Something went wrong. Please report to support@avo.app.", e);
            return new HashMap<>();
        }
    }

    private void logPreExtract(@NonNull String eventName, @Nullable Object eventProperties) {
        if (isLogging() && eventProperties != null) {
            Log.d("Avo Inspector", "Supplied event " + eventName + " with params \n" + eventProperties.toString());
        }
    }

    @SuppressWarnings("rawtypes")
    private void showEventInVisualInspector(String eventName, @Nullable Map<String, ?> mapParams, @Nullable JSONObject jsonParams) {
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
                            valueDescription = new JSONObject((Map)value).toString(1)
                                    .replace("\n", "")
                                    .replace("\\", "");
                        } catch (JSONException ex) {
                            valueDescription = new JSONObject((Map)value).toString()
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

    private void showSchemaInVisualInspector(String eventName, Map<String, AvoEventSchemaType> schema) {
        List<EventProperty> props = new ArrayList<>();
        for (Map.Entry<String, AvoEventSchemaType> param: schema.entrySet()) {
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

    @Override
    public void trackSchema(@NonNull String eventName, @Nullable Map<String, AvoEventSchemaType> eventSchema) {
        try {
            if (AvoDeduplicator.shouldRegisterSchemaFromManually(eventName, eventSchema)) {
                trackSchemaInternal(eventName, eventSchema, null, null);
            } else {
                if (isLogging()) {
                    Log.d("Avo Inspector", "Deduplicated event " + eventName);
                }
            }
        } catch (Exception e) {
            Log.e("Avo Inspector", "Something went wrong. Please report to support@avo.app.", e);
        }
    }

    private void trackSchemaInternal(@NonNull String eventName, @Nullable Map<String, AvoEventSchemaType> eventSchema, @Nullable String eventId, @Nullable String eventHash) {
        if (eventSchema == null) {
            eventSchema = new HashMap<>();
        }

        logPostExtract(eventName, eventSchema);
        showSchemaInVisualInspector(eventName, eventSchema);

        sessionTracker.startOrProlongSession(System.currentTimeMillis());

        avoBatcher.batchTrackEventSchema(eventName, eventSchema, eventId, eventHash);
    }

    static void logPostExtract(@Nullable String eventName, @NonNull Map<String, AvoEventSchemaType> eventSchema) {
        if (isLogging()) {
            StringBuilder schemaString = new StringBuilder();

            for (String key: eventSchema.keySet()) {
                AvoEventSchemaType value = eventSchema.get(key);
                if (value != null) {
                    String entry = "\t\"" + key + "\": \"" + value.getReportedName() + "\";\n";
                    schemaString.append(entry);
                }
            }

            if (eventName != null) {
                Log.d("Avo Inspector", "Saved event " + eventName + " with schema {\n" + schemaString + "}");
            } else {
                Log.d("Avo Inspector", "Parsed schema {\n" + schemaString + "}");
            }
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public @NonNull Map<String, AvoEventSchemaType> extractSchema(@Nullable Object eventProperties) {
        try {
            sessionTracker.startOrProlongSession(System.currentTimeMillis());

            Map eventPropsToCheck = new HashMap<>();
            if (eventProperties instanceof Map) {
                eventPropsToCheck = (Map) eventProperties;
            } else if (eventProperties instanceof JSONObject) {
                eventPropsToCheck = Util.jsonToMap((JSONObject) eventProperties);
            }
            try {
                //noinspection unchecked
                if (AvoDeduplicator.hasSeenEventParams(eventPropsToCheck, true)) {
                    Log.w("Avo Inspector", "WARNING! You are trying to extract schema shape that was just reported by your Avo functions. " +
                            "This is an indicator of duplicate inspector reporting. " +
                            "Please reach out to support@avo.app for advice if you are not sure how to handle this.");
                }
            } catch (Exception ignored) {
            }

            return avoSchemaExtractor.extractSchema(eventProperties, true);
        } catch (Exception e) {
            Log.e("Avo Inspector", "Something went wrong. Please report to support@avo.app.", e);
            return new HashMap<>();
        }
    }

    @Override
    @Nullable
    public DebuggerManager getVisualInspector() {
        return debugger;
    }

    @SuppressWarnings("WeakerAccess")
    static public boolean isLogging() {
        return logsEnabled;
    }

    @SuppressWarnings("WeakerAccess")
    static public void enableLogging(boolean enabled) {
        logsEnabled = enabled;
    }

    @SuppressWarnings("WeakerAccess")
    static public int getBatchSize() {
        return AvoBatcher.batchSize;
    }

    @SuppressWarnings("WeakerAccess")
    static public void setBatchSize(int newBatchSize) {
        AvoBatcher.batchSize = newBatchSize;
    }

    @SuppressWarnings("WeakerAccess")
    static public int getBatchFlushSeconds() {
        return AvoBatcher.batchFlushSeconds;
    }

    @SuppressWarnings("WeakerAccess")
    static public void setBatchFlushSeconds(int newBatchFlushSeconds) {
        AvoBatcher.batchFlushSeconds = newBatchFlushSeconds;
    }
}
