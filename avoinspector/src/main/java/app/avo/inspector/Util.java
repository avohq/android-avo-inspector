package app.avo.inspector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

class Util {

    static final String AVO_SHARED_PREFS_KEY = "avo_inspector_preferences";

    static String currentTimeAsISO8601UTCString() {
        SimpleDateFormat ISO8601UTC = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US); // new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
        ISO8601UTC.setTimeZone(TimeZone.getTimeZone("UTC"));
        return ISO8601UTC.format(new Date());
    }

    static JSONArray remapProperties(Map<String, AvoEventSchemaType> originalProperties) {
        List<Map<String, Object>> properties = new ArrayList<>();

        for (String propKey : originalProperties.keySet()) {
            AvoEventSchemaType propValue = originalProperties.get(propKey);
            if (propValue == null) {
                continue;
            }

            Map<String, Object> prop = new HashMap<>();
            prop.put("propertyName", propKey);
            if (propValue instanceof AvoEventSchemaType.AvoObject) {
                prop.put("propertyType", "object");
                prop.put("children", remapProperties(((AvoEventSchemaType.AvoObject) propValue).children));
            } else {
                prop.put("propertyType", propValue.getReportedName());
            }
            properties.add(prop);
        }

        return new JSONArray(properties);
    }

    static String readableJsonProperties(Map<String, AvoEventSchemaType> originalProperties) {
        Map<String, String> propsDescription = new HashMap<>();

        for (String propName: originalProperties.keySet()) {
            AvoEventSchemaType propType = originalProperties.get(propName);
            String propValue = propType != null ? propType.getReadableName() : "null";
            propsDescription.put(propName, propValue);
        }

        try {
            return new JSONObject(propsDescription).toString(1)
                    .replace("\n", "").replace("\\", "");
        } catch (JSONException ex) {
            return new JSONObject(propsDescription).toString().replace("\\", "");
        }
    }

    public static Map<String, Object> jsonToMap(JSONObject json) {
        Map<String, Object> retMap = new HashMap<String, Object>();

        if(json != JSONObject.NULL) {
            retMap = toMap(json);
        }
        return retMap;
    }

    static Map<String, Object> toMap(JSONObject object) {
        Map<String, Object> map = new HashMap<String, Object>();

        Iterator<String> keysItr = object.keys();
        while(keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = null;
            try {
                value = object.get(key);
            } catch (JSONException e) {
                continue;
            }

            if(value instanceof JSONArray) {
                value = toList((JSONArray) value);
            }

            else if(value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    static List<Object> toList(JSONArray array) {
        List<Object> list = new ArrayList<>();
        for(int i = 0; i < array.length(); i++) {
            Object value;
            try {
                value = array.get(i);
            } catch (JSONException e) {
                continue;
            }
            if(value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if(value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }
}
