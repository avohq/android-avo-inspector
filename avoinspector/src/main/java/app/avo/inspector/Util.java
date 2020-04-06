package app.avo.inspector;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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

    static String remapProperties(Map<String, AvoEventSchemaType> originalProperties) {
        List<Map<String, String>> properties = new ArrayList<>();

        for (String propKey : originalProperties.keySet()) {
            AvoEventSchemaType propValue = originalProperties.get(propKey);
            if (propValue == null) {
                continue;
            }

            Map<String, String> prop = new HashMap<>();
            prop.put("propertyName", propKey);
            if (propValue instanceof AvoEventSchemaType.AvoObject) {
                prop.put("propertyType", "object");
                prop.put("children", remapProperties(((AvoEventSchemaType.AvoObject) propValue).children));
            } else {
                prop.put("propertyType", propValue.getName());
            }
            properties.add(prop);
        }

        return new JSONArray(properties).toString().replace("\\\"", "\"");
    }
}
