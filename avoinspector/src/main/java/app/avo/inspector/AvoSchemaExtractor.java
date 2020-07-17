package app.avo.inspector;

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

public class AvoSchemaExtractor {

	@NonNull Map<String, AvoEventSchemaType> extractSchema(@Nullable Object eventProperties, boolean shouldLogIfEnabled) {
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

		if (shouldLogIfEnabled && AvoInspector.isLogging()) {
			AvoInspector.logPostExtract(null, result);
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
}
