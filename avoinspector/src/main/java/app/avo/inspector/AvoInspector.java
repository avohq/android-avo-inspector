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

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import static app.avo.inspector.Util.handleException;

@SuppressWarnings("unchecked")
public class AvoInspector implements Inspector {

    private static boolean logsEnabled = false;

    String apiKey;
    @Nullable String appVersionString = null;
    String appName;
    int libVersion = BuildConfig.VERSION_CODE;

    String env;

    AvoBatcher avoBatcher;
    AvoSchemaExtractor avoSchemaExtractor;

    boolean isHidden = true;

    @NonNull
    VisualInspector visualInspector;

    public static AvoStorage avoStorage;

    AvoInspector(String apiKey, Application application, String envString, @Nullable Activity rootActivityForVisualInspector) {
        this(apiKey, application,
                envString.equalsIgnoreCase("prod") ? AvoInspectorEnv.Prod :
                        envString.equalsIgnoreCase("staging") ? AvoInspectorEnv.Staging : AvoInspectorEnv.Dev,
                rootActivityForVisualInspector);
    }

    public AvoInspector(@NonNull String apiKey, @NonNull Application application, @NonNull AvoInspectorEnv env) {
        this(apiKey, application, env, null);
    }

    @SuppressWarnings("deprecation")
    public AvoInspector(@NonNull String apiKey, @NonNull Application application, @NonNull AvoInspectorEnv env, @Nullable Activity rootActivityForVisualInspector) {
        try {
            PackageManager packageManager = application.getPackageManager();
            PackageInfo pInfo = packageManager.getPackageInfo(application.getPackageName(), 0);

            appVersionString = pInfo.versionName;

            if (!appVersionString.matches("^(?:[\\w-]+-)?(\\d+\\.\\d+\\.\\d+(?:-[\\w.-]+)?(?:\\+[\\w.-]+)?)$")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    appVersionString = pInfo.getLongVersionCode() + "";
                } else {
                    //noinspection deprecation
                    appVersionString = pInfo.versionCode + "";
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {}

        if (avoStorage == null) {
            final android.content.SharedPreferences sharedPreferences = application.getSharedPreferences("AvoInspector", android.content.Context.MODE_PRIVATE);
            avoStorage = new AvoStorage() {
                @Override
                public boolean isInitialized() {
                    return true;
                }

                @Override
                public String getItem(String key) {
                    return sharedPreferences.getString(key, null);
                }

                @Override
                public void setItem(String key, String value) {
                    sharedPreferences.edit().putString(key, value).apply();
                }
            };
        }

        avoSchemaExtractor = new AvoSchemaExtractor();

        int stringId = application.getApplicationInfo().labelRes;
        appName = stringId == 0 ? application.getApplicationInfo().packageName : application.getString(stringId);

        this.env = env.getName();
        this.apiKey = apiKey;

        AvoNetworkCallsHandler networkCallsHandler = new AvoNetworkCallsHandler(
                apiKey, env.getName(), appName, appVersionString, libVersion + "");
        avoBatcher = new AvoBatcher(application, networkCallsHandler);

        if (env == AvoInspectorEnv.Dev) {
            setBatchSize(1);
            enableLogging(true);
        } else {
            setBatchSize(30);
            setBatchFlushSeconds(30);
            enableLogging(false);
        }

        visualInspector = new VisualInspector(env, application, rootActivityForVisualInspector);

        application.registerActivityLifecycleCallbacks(new EmptyActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (isHidden) {
                    isHidden = false;
                    try {
                        avoBatcher.enterForeground();
                    } catch (Exception e) {
                        handleException(e, AvoInspector.this.env);
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
                        handleException(e, AvoInspector.this.env);
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
    public void showVisualInspector(@NonNull Activity rootActivity, @NonNull VisualInspectorMode visualInspectorMode) {
        try {
            visualInspector.show(rootActivity, visualInspectorMode);
        } catch (Exception e) {
            handleException(e, AvoInspector.this.env);
        }
    }

    @Override
    public void hideVisualInspector(@NonNull Activity rootActivity) {
        try {
            visualInspector.hide(rootActivity);
        } catch (Exception e) {
            handleException(e, AvoInspector.this.env);
        }
    }

    @SuppressWarnings({"SameParameterValue"})
    @NonNull Map<String, AvoEventSchemaType> avoFunctionTrackSchemaFromEvent(@NonNull String eventName, @Nullable Map<String, ?> eventProperties, @NonNull String eventId, @NonNull String eventHash) {
        try {
            if (AvoDeduplicator.shouldRegisterEvent(eventName, eventProperties, true)) {
                logPreExtract(eventName, eventProperties);
                visualInspector.showEventInVisualInspector(eventName, eventProperties, null);

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
            handleException(e, AvoInspector.this.env);
            return new HashMap<>();
        }
    }

    @Override
    public @NonNull Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, @Nullable JSONObject eventProperties) {
        try {
            if (AvoDeduplicator.shouldRegisterEvent(eventName, Util.jsonToMap(eventProperties), false)) {
                logPreExtract(eventName, eventProperties);
                visualInspector.showEventInVisualInspector(eventName, null, eventProperties);

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
            handleException(e, AvoInspector.this.env);
            return new HashMap<>();
        }
    }

    @Override
    public @NonNull Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, @Nullable Map<String, ?> eventProperties) {
        try {
            if (AvoDeduplicator.shouldRegisterEvent(eventName, eventProperties, false)) {
                logPreExtract(eventName, eventProperties);
                visualInspector.showEventInVisualInspector(eventName, eventProperties, null);

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
            handleException(e, AvoInspector.this.env);
            return new HashMap<>();
        }
    }

    private void logPreExtract(@NonNull String eventName, @Nullable Object eventProperties) {
        if (isLogging() && eventProperties != null) {
            Log.d("Avo Inspector", "Supplied event " + eventName + " with params \n" + eventProperties);
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
            handleException(e, AvoInspector.this.env);
        }
    }

    private void trackSchemaInternal(@NonNull String eventName, @Nullable Map<String, AvoEventSchemaType> eventSchema, @Nullable String eventId, @Nullable String eventHash) {
        if (eventSchema == null) {
            eventSchema = new HashMap<>();
        }

        logPostExtract(eventName, eventSchema);
        visualInspector.showSchemaInVisualInspector(eventName, eventSchema);

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
            Map eventPropsToCheck = new HashMap<>();
            if (eventProperties instanceof Map) {
                eventPropsToCheck = (Map) eventProperties;
            } else if (eventProperties instanceof JSONObject) {
                eventPropsToCheck = Util.jsonToMap((JSONObject) eventProperties);
            }
            try {
                //noinspection unchecked
                if (AvoDeduplicator.hasSeenEventParams(eventPropsToCheck, true)) {
                    Log.w("Avo Inspector", "WARNING! You are trying to extract schema shape that was just reported by your Avo Codegen. " +
                            "This is an indicator of duplicate inspector reporting. " +
                            "Please reach out to support@avo.app for advice if you are not sure how to handle this.");
                }
            } catch (Exception ignored) {
            }

            return avoSchemaExtractor.extractSchema(eventProperties, true);
        } catch (Exception e) {
            handleException(e, AvoInspector.this.env);
            return new HashMap<>();
        }
    }

    @Override
    @Nullable
    public Object getVisualInspector() {
        return visualInspector.getDebuggerManager();
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
        if (newBatchSize < 1) {
            AvoBatcher.batchSize = 1;
        } else {
            AvoBatcher.batchSize = newBatchSize;
        }
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
