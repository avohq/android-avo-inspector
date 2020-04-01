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

public class AvoInspector implements Inspector {

    String apiKey;
    Long appVersion = null;
    String appName;
    int libVersion = BuildConfig.VERSION_CODE;
    boolean logsEnabled = false;
    String env;

    AvoInspectorSessionTracker sessionTracker;
    AvoInspectorBatcher avoBatcher;

    boolean isHidden = true;

    public AvoInspector(String apiKey, Application application, AvoInspectorEnv env) {
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

        avoBatcher = new AvoInspectorBatcher(application);
        sessionTracker = new AvoInspectorSessionTracker(application, avoBatcher);

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
    public @NonNull Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, @Nullable JSONObject eventProperties) {
        Map<String, AvoEventSchemaType> schema = extractSchemaFromJson(eventProperties);

        trackSchema(eventName, schema);

        return schema;
    }

    @Override
    public @NonNull Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, @Nullable Map<?, ?> eventProperties) {
        Map<String, AvoEventSchemaType> schema = extractSchemaFromMap(eventProperties);

        trackSchema(eventName, schema);

        return schema;
    }

    @Override
    public @NonNull Map<String, AvoEventSchemaType> trackSchemaFromEvent(@NonNull String eventName, @Nullable Object eventProperties) {
        Map<String, AvoEventSchemaType> schema = extractSchema(eventProperties);

        trackSchema(eventName, schema);

        return schema;
    }

    @Override
    public void trackSchema(@NonNull String eventName, @Nullable Map<String, AvoEventSchemaType> eventSchema) {
        if (logsEnabled) {
            Log.d(getClass().getSimpleName(), "Avo State Of Tracking schema tracked");
        }

        sessionTracker.startOrProlongSession(System.currentTimeMillis());

        avoBatcher.batchTrackEventSchema(eventName, eventSchema);
    }

    @Override
    public void enableLogging(boolean enable) {
        logsEnabled = enable;
    }

    @Override
    public @NonNull Map<String, AvoEventSchemaType> extractSchema(@Nullable Object eventProperties) {
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

        if (logsEnabled) {
            Log.d("AvoInspector", "Logged schema: " + result.toString());
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
            return new AvoEventSchemaType.Unknown();
        }
    }

    private AvoEventSchemaType objectToAvoType(@Nullable Object val) {
        if (val == null || val instanceof AvoEventSchemaType.Null || val == JSONObject.NULL) {
            return new AvoEventSchemaType.Null();
        } else {
            String valTypeName = val.getClass().getName();

            switch (valTypeName) {
                case "int":
                case "java.lang.Integer":
                case "byte":
                case "java.lang.Byte":
                case "long":
                case "java.lang.Long":
                case "short":
                case "java.lang.Short":
                    return new AvoEventSchemaType.Int();
                case "boolean":
                case "java.lang.Boolean":
                    return new AvoEventSchemaType.Boolean();
                case "float":
                case "java.lang.Float":
                case "double":
                case "java.lang.Double":
                    return new AvoEventSchemaType.Float();
                case "char":
                case "java.lang.Character":
                case "java.lang.String":
                    return new AvoEventSchemaType.String();
                case "java.util.ArrayList":
                case "java.util.LinkedList":
                    Set<AvoEventSchemaType> subtypes = new HashSet<>();
                    List list = (List) val;
                    for (Object v : list) {
                        subtypes.add(objectToAvoType(v));
                    }

                    return new AvoEventSchemaType.List(subtypes);
                case "org.json.JSONArray":
                    Set<AvoEventSchemaType> subitems = new HashSet<>();
                    JSONArray jsonArray = (JSONArray) val;
                    for (int i = 0; i < jsonArray.length(); i++) {
                        try {
                            subitems.add(objectToAvoType(jsonArray.get(i)));
                        } catch (JSONException ignored) { }
                    }

                    return new AvoEventSchemaType.List(subitems);
                case "[Ljava.lang.String;":
                    subtypes = new HashSet<>();
                    subtypes.add(new AvoEventSchemaType.String());
                    subtypes.add(new AvoEventSchemaType.Null());
                    return new AvoEventSchemaType.List(subtypes);
                case "[Ljava.lang.Integer;":
                    subtypes = new HashSet<>();
                    subtypes.add(new AvoEventSchemaType.Int());
                    subtypes.add(new AvoEventSchemaType.Null());
                    return new AvoEventSchemaType.List(subtypes);
                case "[I":
                    subtypes = new HashSet<>();
                    subtypes.add(new AvoEventSchemaType.Int());
                    return new AvoEventSchemaType.List(subtypes);
                case "[Ljava.lang.Boolean;":
                    subtypes = new HashSet<>();
                    subtypes.add(new AvoEventSchemaType.Boolean());
                    subtypes.add(new AvoEventSchemaType.Null());
                    return new AvoEventSchemaType.List(subtypes);
                case "[Z":
                    subtypes = new HashSet<>();
                    subtypes.add(new AvoEventSchemaType.Boolean());
                    return new AvoEventSchemaType.List(subtypes);
                case "[Ljava.lang.Float;":
                case "[Ljava.lang.Double;":
                    subtypes = new HashSet<>();
                    subtypes.add(new AvoEventSchemaType.Float());
                    subtypes.add(new AvoEventSchemaType.Null());
                    return new AvoEventSchemaType.List(subtypes);
                case "[D":
                case "[F":
                    subtypes = new HashSet<>();
                    subtypes.add(new AvoEventSchemaType.Float());
                    return new AvoEventSchemaType.List(subtypes);
                case "java.util.HashMap":
                    AvoEventSchemaType.AvoObject result = new AvoEventSchemaType.AvoObject(new HashMap<String, AvoEventSchemaType>());

                    for (Object childName: ((Map)val).keySet()) {
                        String childNameString = (String) childName;

                        AvoEventSchemaType paramType = objectToAvoType(((Map)val).get(childName));

                        result.children.put(childNameString, paramType);
                    }

                    return result;
                default:
                    return new AvoEventSchemaType.Unknown();
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
}
