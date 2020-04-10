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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    boolean isHidden = true;

    @Nullable
    private DebuggerManager debugger;

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
                    avoBatcher.enterForeground();
                    sessionTracker.startOrProlongSession(System.currentTimeMillis());
                }
            }
        });

        application.registerComponentCallbacks(new ComponentCallbacks2() {
            @Override
            public void onTrimMemory(int i) {
                if (i == TRIM_MEMORY_UI_HIDDEN) {
                    isHidden = true;
                    avoBatcher.enterBackground();
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

    @Override
    public @NonNull Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, @Nullable JSONObject eventProperties) {
        logPreExtract(eventName, eventProperties);
        showEventInVisualInspector(eventName, null, eventProperties);

        Map<String, AvoEventSchemaType> schema = extractSchema(eventProperties, false);

        trackSchema(eventName, schema);

        return schema;
    }

    @Override
    public @NonNull Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, @Nullable Map<String, ?> eventProperties) {
        logPreExtract(eventName, eventProperties);
        showEventInVisualInspector(eventName, eventProperties, null);

        Map<String, AvoEventSchemaType> schema = extractSchema(eventProperties, false);

        trackSchema(eventName, schema);

        return schema;
    }

    private void logPreExtract(@NonNull String eventName, @Nullable Object eventProperties) {
        if (isLogging() && eventProperties != null) {
            Log.d("Avo Inspector", "Supplied event " + eventName + " with params \n" + eventProperties.toString());
        }
    }

    private void showEventInVisualInspector(String eventName, @Nullable Map<String, ?> mapParams, @Nullable JSONObject jsonParams) {
        List<EventProperty> props = new ArrayList<>();
        if (mapParams != null) {
            for (Map.Entry<String, ?> param : mapParams.entrySet()) {
                String name = param.getKey();
                Object value = param.getValue();
                if (name != null) {
                    props.add(new EventProperty("", name, value != null ? value.toString() : "null"));
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
                props.add(new EventProperty("", name, value != null ? value.getName() : "null"));
            }
        }
        if (debugger != null) {
            debugger.publishEvent(System.currentTimeMillis(), "Schema: " + eventName, props, new ArrayList<PropertyError>());
        }
    }

    @Override
    public void trackSchema(@NonNull String eventName, @Nullable Map<String, AvoEventSchemaType> eventSchema) {
        if (eventSchema == null) {
            eventSchema = new HashMap<>();
        }

        logPostExtract(eventName, eventSchema);
        showSchemaInVisualInspector(eventName, eventSchema);

        sessionTracker.startOrProlongSession(System.currentTimeMillis());

        avoBatcher.batchTrackEventSchema(eventName, eventSchema);
    }

    private void logPostExtract(@Nullable String eventName, @NonNull Map<String, AvoEventSchemaType> eventSchema) {
        if (isLogging()) {
            StringBuilder schemaString = new StringBuilder();

            for (String key: eventSchema.keySet()) {
                AvoEventSchemaType value = eventSchema.get(key);
                if (value != null) {
                    String entry = "\t\"" + key + "\": \"" + value.getName() + "\";\n";
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

    @Override
    public @NonNull Map<String, AvoEventSchemaType> extractSchema(@Nullable Object eventProperties) {
        return extractSchema(eventProperties, true);
    }

    private  @NonNull Map<String, AvoEventSchemaType> extractSchema(@Nullable Object eventProperties, boolean shouldLogIfEnabled) {
        Map<String, AvoEventSchemaType> result;

        if (eventProperties == null) {
            result = new HashMap<>();
        } else if (eventProperties instanceof Map) {
            result = extractSchemaFromMap((Map) eventProperties);
        } else if (eventProperties instanceof JSONObject) {
            result = extractSchemaFromJson((JSONObject) eventProperties);
        } else {
            result = extractSchemaFromObject(eventProperties);
        }

        if (shouldLogIfEnabled && isLogging()) {
            logPostExtract(null, result);
        }

        return result;
    }

    private Map<String, AvoEventSchemaType> extractSchemaFromObject(@NonNull Object eventProperties) {
        Map<String, AvoEventSchemaType> result = new HashMap<>();

        List<Field> eventPropertiesFields = new ArrayList<>();

        for (Class<?> eventPropertiesClass = eventProperties.getClass();
             eventPropertiesClass != Object.class && eventPropertiesClass != null;
             eventPropertiesClass = eventPropertiesClass.getSuperclass())
        {
            Field[] fields = eventPropertiesClass.getDeclaredFields();
            eventPropertiesFields.addAll(Arrays.asList(fields));
        }

        for (Field eventPropertyField: eventPropertiesFields) {
            AvoEventSchemaType propertyType = getAvoSchemaType(eventProperties, eventPropertyField);
            result.put(eventPropertyField.getName(), propertyType);
        }
        return result;
    }

    private AvoEventSchemaType getAvoSchemaType(Object eventProperties, Field eventPropertyField) {
        try {
            return objectToAvoType(eventPropertyField.get(eventProperties));
        } catch (IllegalAccessException ignored) {
            return new AvoEventSchemaType.AvoUnknownType();
        }
    }

    private AvoEventSchemaType objectToAvoType(@Nullable Object val) {
        if (val == null || val instanceof AvoEventSchemaType.AvoNull || val == JSONObject.NULL) {
            return new AvoEventSchemaType.AvoNull();
        } else {
            if (val instanceof List) {
                Set<AvoEventSchemaType> subtypes = new HashSet<>();
                List list = (List) val;
                for (Object v : list) {
                    subtypes.add(objectToAvoType(v));
                }

                return new AvoEventSchemaType.AvoList(subtypes);
            } else if (val instanceof JSONArray) {
                Set<AvoEventSchemaType> subItems = new HashSet<>();
                JSONArray jsonArray = (JSONArray) val;
                for (int i = 0; i < jsonArray.length(); i++) {
                    try {
                        subItems.add(objectToAvoType(jsonArray.get(i)));
                    } catch (JSONException ignored) { }
                }

                return new AvoEventSchemaType.AvoList(subItems);
            } else if (val instanceof Map) {
                AvoEventSchemaType.AvoObject result = new AvoEventSchemaType.AvoObject(new HashMap<String, AvoEventSchemaType>());

                for (Object childName: ((Map)val).keySet()) {
                    String childNameString = (String) childName;

                    AvoEventSchemaType paramType = objectToAvoType(((Map)val).get(childName));

                    result.children.put(childNameString, paramType);
                }

                return result;
            } else if (val instanceof Integer || val instanceof  Byte || val instanceof Long || val instanceof  Short) {
                return new AvoEventSchemaType.AvoInt();
            } else if (val instanceof Boolean) {
                return new AvoEventSchemaType.AvoBoolean();
            } else if (val instanceof Float || val instanceof  Double) {
                return new AvoEventSchemaType.AvoFloat();
            } else if (val instanceof String || val instanceof  Character) {
                return new AvoEventSchemaType.AvoString();
            } else {
                return arrayOrUnknownToAvoType(val);
            }
        }
    }

    private AvoEventSchemaType arrayOrUnknownToAvoType(@NonNull Object val) {
        String className = val.getClass().getName();
        switch (className) {
            case "[Ljava.lang.String;":
                Set<AvoEventSchemaType> subtypes = new HashSet<>();
                subtypes.add(new AvoEventSchemaType.AvoString());
                subtypes.add(new AvoEventSchemaType.AvoNull());
                return new AvoEventSchemaType.AvoList(subtypes);
            case "[Ljava.lang.Integer;":
                subtypes = new HashSet<>();
                subtypes.add(new AvoEventSchemaType.AvoInt());
                subtypes.add(new AvoEventSchemaType.AvoNull());
                return new AvoEventSchemaType.AvoList(subtypes);
            case "[I":
                subtypes = new HashSet<>();
                subtypes.add(new AvoEventSchemaType.AvoInt());
                return new AvoEventSchemaType.AvoList(subtypes);
            case "[Ljava.lang.Boolean;":
                subtypes = new HashSet<>();
                subtypes.add(new AvoEventSchemaType.AvoBoolean());
                subtypes.add(new AvoEventSchemaType.AvoNull());
                return new AvoEventSchemaType.AvoList(subtypes);
            case "[Z":
                subtypes = new HashSet<>();
                subtypes.add(new AvoEventSchemaType.AvoBoolean());
                return new AvoEventSchemaType.AvoList(subtypes);
            case "[Ljava.lang.Float;":
            case "[Ljava.lang.Double;":
                subtypes = new HashSet<>();
                subtypes.add(new AvoEventSchemaType.AvoFloat());
                subtypes.add(new AvoEventSchemaType.AvoNull());
                return new AvoEventSchemaType.AvoList(subtypes);
            case "[D":
            case "[F":
                subtypes = new HashSet<>();
                subtypes.add(new AvoEventSchemaType.AvoFloat());
                return new AvoEventSchemaType.AvoList(subtypes);
            default:
                if (className.startsWith("[L") && className.contains("List")) {
                    subtypes = new HashSet<>();
                    subtypes.add(new AvoEventSchemaType.AvoList(new HashSet<AvoEventSchemaType>()));
                    subtypes.add(new AvoEventSchemaType.AvoNull());
                    return new AvoEventSchemaType.AvoList(subtypes);
                } else if (className.startsWith("[L")) {
                    subtypes = new HashSet<>();
                    subtypes.add(new AvoEventSchemaType.AvoObject(new HashMap<String, AvoEventSchemaType>()));
                    subtypes.add(new AvoEventSchemaType.AvoNull());
                    return new AvoEventSchemaType.AvoList(subtypes);
                } else {
                    return new AvoEventSchemaType.AvoUnknownType();
                }
        }
    }

    private Map<String, AvoEventSchemaType> extractSchemaFromMap(@Nullable Map<?, ?> eventSchema) {
        if (eventSchema == null) {
            return new HashMap<>();
        }

        Map<String, AvoEventSchemaType> result = new HashMap<>();

        for (Map.Entry<?, ?> entry: eventSchema.entrySet()) {
            AvoEventSchemaType propertyType = objectToAvoType(entry.getValue());
            result.put(entry.getKey().toString(), propertyType);
        }

        return result;
    }

    private Map<String, AvoEventSchemaType> extractSchemaFromJson(@Nullable JSONObject eventSchema) {
        if (eventSchema == null) {
            return new HashMap<>();
        }

        Map<String, AvoEventSchemaType> result = new HashMap<>();

        for (Iterator<String> it = eventSchema.keys(); it.hasNext(); ) {
            String key = it.next();
            try {
                Object value = eventSchema.get(key);

                AvoEventSchemaType propertyType = objectToAvoType(value);
                result.put(key, propertyType);
            } catch (JSONException ignored) {}
        }

        return result;
    }

    @SuppressWarnings("unused")
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
