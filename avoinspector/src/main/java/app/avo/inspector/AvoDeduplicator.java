package app.avo.inspector;

import androidx.annotation.VisibleForTesting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static app.avo.inspector.Util.mapsEqual;

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

	private static boolean hasSameEventAs(String eventName, Map<String, ?> params, boolean checkInAvoFunctions) {

		boolean result = false;

		if (checkInAvoFunctions) {
			if (lookForEventIn(eventName, params, avoFunctionsEventsParams)) {
				result = true;
			}
		} else {
			if (lookForEventIn(eventName, params, manualEventsParams)) {
				result = true;
			}
		}

		if (result) {
			avoFunctionsEventsParams.remove(eventName);
			manualEventsParams.remove(eventName);
		}

		return result;
	}

	private static boolean lookForEventIn(String eventName, Map<String, ?> params, Map<String, Map<String, ?>> eventsStorage) {
		for (String otherEventName : eventsStorage.keySet()) {
			if (otherEventName.equals(eventName)) {
				Map<String, ?> otherParams = eventsStorage.get(eventName);
				if (mapsEqual(params, otherParams)) {
					return true;
				}
			}
		}
		return false;
	}

	static boolean hasSeenEventParams(Map<String, ?> params, boolean checkInAvoFunctions) {
		boolean result = false;

		if (checkInAvoFunctions) {
			if (lookForEventParamsIn(params, avoFunctionsEventsParams)) {
				result = true;
			}
		} else {
			if (lookForEventParamsIn(params, manualEventsParams)) {
				result = true;
			}
		}

		return result;
	}

	private static boolean lookForEventParamsIn(Map<String, ?> params, Map<String, Map<String, ?>> eventsStorage) {
		for (String otherEventName : eventsStorage.keySet()) {
			Map<String, ?> otherParams = eventsStorage.get(otherEventName);
			if (mapsEqual(params, otherParams)) {
				return true;
			}
		}

		return false;
	}

	static boolean shouldRegisterSchemaFromManually(String eventName, Map<String, AvoEventSchemaType> shapes) {
		clearOldEvents();

		return !hasSameShapeInAvoFunctionsAs(eventName, shapes);
	}

	private static boolean hasSameShapeInAvoFunctionsAs(String eventName, Map<String, AvoEventSchemaType> shapes) {
		boolean result = false;

		if (lookForEventSchemaIn(eventName, shapes, avoFunctionsEventsParams)) {
			result = true;
		}

		if (result) {
			avoFunctionsEventsParams.remove(eventName);
		}

		return result;
	}

	private static boolean lookForEventSchemaIn(String eventName, Map<String, AvoEventSchemaType> shapes, Map<String, Map<String, ?>> eventsStorage) {
		for (String otherEventName : eventsStorage.keySet()) {
			if (otherEventName.equals(eventName)) {
				Map<String, ?> otherShapes = avoSchemaExtractor.extractSchema(eventsStorage.get(eventName), false);
				if (mapsEqual(shapes, otherShapes)) {
					return true;
				}
			}
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
