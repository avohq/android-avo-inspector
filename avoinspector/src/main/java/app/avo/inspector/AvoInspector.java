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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.sentry.DefaultSentryClientFactory;
import io.sentry.Sentry;
import io.sentry.config.Lookup;
import io.sentry.config.provider.ConfigurationProvider;

import static app.avo.inspector.Util.handleException;

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

    @NonNull
    VisualInspector visualInspector;

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
        String appVersionString = "";
        try {
            PackageManager packageManager = application.getPackageManager();
            PackageInfo pInfo = packageManager.getPackageInfo(application.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                appVersion = pInfo.getLongVersionCode();
            } else {
                appVersion = (long) pInfo.versionCode;
            }
            appVersionString = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {}

        avoSchemaExtractor = new AvoSchemaExtractor();

        int stringId = application.getApplicationInfo().labelRes;
        appName = stringId == 0 ? application.getApplicationInfo().packageName : application.getString(stringId);

        this.env = env.getName();
        this.apiKey = apiKey;

        setupSentry(appVersionString);

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
                    try {
                        sessionTracker.startOrProlongSession(System.currentTimeMillis());
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

    private void setupSentry(String appVersionString) {
        try {
            Lookup lookup = Lookup.getDefaultWithAdditionalProviders(Collections.<ConfigurationProvider>singletonList(new ConfigurationProvider() {
                @Override
                public String getProperty(String key) {
                    if (key.equals(DefaultSentryClientFactory.UNCAUGHT_HANDLER_ENABLED_OPTION)) {
                        return Boolean.FALSE.toString();
                    }
                    return null;
                }
            }), new ArrayList<ConfigurationProvider>());

            Sentry.init("https://c88eba7699af4b568fc82fc15128fc7f@o75921.ingest.sentry.io/5394353", new DefaultSentryClientFactory(lookup));

            Sentry.getStoredClient().addTag("App name", appName);
            Sentry.getStoredClient().addTag("Environment", this.env);
            Sentry.getStoredClient().addTag("App Version", appVersionString);
            Sentry.getStoredClient().addTag("App Version Number", this.appVersion.toString());
            Sentry.getStoredClient().addTag("Lib Version", BuildConfig.VERSION_NAME);
            Sentry.getStoredClient().addTag("Lib Version Number", (Long.valueOf(BuildConfig.VERSION_CODE)).toString());
        } catch (Exception ignored) {}
    }

    @Override
    public void showVisualInspector(Activity rootActivity, VisualInspectorMode visualInspectorMode) {
        try {
            visualInspector.show(rootActivity, visualInspectorMode);
        } catch (Exception e) {
            handleException(e, AvoInspector.this.env);
        }
    }

    @Override
    public void hideVisualInspector(Activity rootActivity) {
        try {
            visualInspector.hide(rootActivity);
        } catch (Exception e) {
            handleException(e, AvoInspector.this.env);
        }
    }

    @SuppressWarnings({"unused", "SameParameterValue"})
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
            Log.d("Avo Inspector", "Supplied event " + eventName + " with params \n" + eventProperties.toString());
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
