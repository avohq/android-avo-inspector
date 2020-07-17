package app.avo.inspector;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

class AvoDeduplicator {

	private static Map<Long, String> avoFunctionsEvents = new HashMap<>();
	private static Map<Long, String> manualEvents = new HashMap<>();

	private static Map<String, Map<String, ?>> avoFunctionsEventsParams = new HashMap<>();
	private static Map<String, Map<String, ?>> manualEventsParams = new HashMap<>();

	private static AvoSchemaExtractor avoSchemaExtractor = new AvoSchemaExtractor();

	static boolean shouldRegisterEvent(String eventName, Map<String, ?> params, boolean fromAvoFunction) {
		clearOldEvents();

		if (fromAvoFunction) {
			avoFunctionsEvents.put(System.currentTimeMillis(), eventName);
			avoFunctionsEventsParams.put(eventName, params);
		} else {
			manualEvents.put(System.currentTimeMillis(), eventName);
			manualEventsParams.put(eventName, params);
		}

		boolean checkInAvoFunctions = !fromAvoFunction;

		return !hasSameEventAs(eventName, params, checkInAvoFunctions);
	}

	static boolean hasSeenEventParams(Map<String, ?> params, boolean checkInAvoFunctions) {
		boolean result = false;

		if (checkInAvoFunctions) {
			for (String otherEventName : avoFunctionsEventsParams.keySet()) {
				Map<String, ?> otherParams = avoFunctionsEventsParams.get(otherEventName);
				if (mapsEqual(params, otherParams)) {
					result = true;
				}
			}
		} else {
			for (String otherEventName : manualEventsParams.keySet()) {
				Map<String, ?> otherParams = manualEventsParams.get(otherEventName);
				if (mapsEqual(params, otherParams)) {
					result = true;
				}
			}
		}

		return result;
	}

	static boolean shouldRegisterSchemaFromManually(String eventName, Map<String, AvoEventSchemaType> shapes) {
		clearOldEvents();

		return !hasSameShapeInAvoFunctionsAs(eventName, shapes);
	}

	private static boolean hasSameShapeInAvoFunctionsAs(String eventName, Map<String, AvoEventSchemaType> shapes) {
		boolean result = false;

		for (String otherEventName : avoFunctionsEventsParams.keySet()) {
			if (otherEventName.equals(eventName)) {
				Map<String, ?> otherShapes = avoSchemaExtractor.extractSchema(avoFunctionsEventsParams.get(eventName), false);
				if (mapsEqual(shapes, otherShapes)) {
					result = true;
					break;
				}
			}
		}

		if (result) {
			avoFunctionsEventsParams.remove(eventName);
		}

		return result;
	}

	private static boolean hasSameEventAs(String eventName, Map<String, ?> params, boolean checkInAvoFunctions) {

		boolean result = false;

		if (checkInAvoFunctions) {
			for (String otherEventName : avoFunctionsEventsParams.keySet()) {
				if (otherEventName.equals(eventName)) {
					Map<String, ?> otherParams = avoFunctionsEventsParams.get(eventName);
					if (mapsEqual(params, otherParams)) {
						result = true;
						break;
					}
				}
			}
		} else {
			for (String otherEventName : manualEventsParams.keySet()) {
				if (otherEventName.equals(eventName)) {
					Map<String, ?> otherParams = manualEventsParams.get(eventName);
					if (mapsEqual(params, otherParams)) {
						result = true;
						break;
					}
				}
			}
		}

		if (result) {
			avoFunctionsEventsParams.remove(eventName);
			manualEventsParams.remove(eventName);
		}

		return result;
	}

	static boolean mapsEqual(@Nullable Map<?, ?> params, @Nullable Map<?, ?> otherParams) {
		if (params == null || otherParams == null) {
			return otherParams == null && params == null;
		}

		if (params.keySet().containsAll(otherParams.keySet()) && otherParams.keySet().containsAll(params.keySet())) {
			for (Object paramName : params.keySet()) {
				Object paramValue = params.get(paramName);
				Object otherParamValue = otherParams.get(paramName);

				return objectsEqual(paramValue, otherParamValue);
			}

			return  true;
		}

		return false;
	}

	private static boolean objectsEqual(@Nullable Object paramValue, @Nullable Object otherParamValue) {
		if (paramValue == null) {
			return otherParamValue == null;
		}

		if (paramValue.getClass().isArray()) {
			return arraysEqual(paramValue, otherParamValue);
		}

		if (paramValue instanceof Collection && otherParamValue instanceof Collection) {
			for (Object paramValueCollectionItem : (Collection)paramValue) {
				if (!objectsEqual(paramValueCollectionItem, otherParamValue)) {
					return false;
				}
			}
		} else if (paramValue instanceof Map && otherParamValue instanceof Map) {
			return mapsEqual((Map)paramValue, (Map)otherParamValue);
		} else {
			return paramValue.equals(otherParamValue);
		}
		return true;
	}

	private static boolean arraysEqual(@Nullable Object paramValue, @Nullable Object otherParamValue) {
		if (paramValue instanceof Object[] && otherParamValue instanceof Object[]) {
			return Arrays.equals((Object[]) paramValue, (Object[]) otherParamValue);
		} else if (paramValue instanceof boolean[] && otherParamValue instanceof boolean[]) {
			return Arrays.equals((boolean[]) paramValue, (boolean[]) otherParamValue);
		} else if (paramValue instanceof int[] && otherParamValue instanceof int[]) {
			return Arrays.equals((int[]) paramValue, (int[]) otherParamValue);
		} else if (paramValue instanceof byte[] && otherParamValue instanceof byte[]) {
			return Arrays.equals((byte[]) paramValue, (byte[]) otherParamValue);
		} else if (paramValue instanceof short[] && otherParamValue instanceof short[]) {
			return Arrays.equals((short[]) paramValue, (short[]) otherParamValue);
		} else if (paramValue instanceof char[] && otherParamValue instanceof char[]) {
			return Arrays.equals((char[]) paramValue, (char[]) otherParamValue);
		} else if (paramValue instanceof long[] && otherParamValue instanceof long[]) {
			return Arrays.equals((long[]) paramValue, (long[]) otherParamValue);
		} else if (paramValue instanceof float[] && otherParamValue instanceof float[]) {
			return Arrays.equals((float[]) paramValue, (float[]) otherParamValue);
		} else if (paramValue instanceof double[] && otherParamValue instanceof double[]) {
			return Arrays.equals((double[]) paramValue, (double[]) otherParamValue);
		}
		return false;
	}

	private static void clearOldEvents() {
		long now = System.currentTimeMillis();
		long msToConsiderOld = 300;

		for(Iterator<Map.Entry<Long, String>> it = avoFunctionsEvents.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, String> entry = it.next();
			Long timestamp = entry.getKey();
			String eventName = entry.getValue();
			if(now - timestamp > msToConsiderOld) {
				it.remove();
				avoFunctionsEventsParams.remove(eventName);
			}
		}

		for(Iterator<Map.Entry<Long, String>> it = manualEvents.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, String> entry = it.next();
			Long timestamp = entry.getKey();
			String eventName = entry.getValue();
			if(now - timestamp > msToConsiderOld) {
				it.remove();
				manualEventsParams.remove(eventName);
			}
		}
	}

	@VisibleForTesting
	static  void clearEvents() {
		avoFunctionsEvents.clear();
		manualEvents.clear();

		avoFunctionsEventsParams.clear();
		manualEventsParams.clear();
	}
}
