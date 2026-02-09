package app.avo.inspector;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * EventValidator - Client-side validation of tracking events against the Avo Tracking Plan.
 *
 * This class validates property values against constraints:
 * - Pinned values (exact match required)
 * - Allowed values (must be in list)
 * - Regex patterns (must match pattern)
 * - Min/max ranges (numeric values must be in range)
 *
 * No schema validation (types/required) is performed - only value constraints.
 * Validation runs against ALL events/variants in the response.
 */
class EventValidator {

    private static final String TAG = "AvoInspector";

    /**
     * Maximum nesting depth for recursive value validation.
     * We validate prop (depth 0), prop.child1 (depth 1), but NOT prop.child1.child2 (depth 2+).
     */
    private static final int MAX_CHILD_DEPTH = 2;

    /**
     * Cache for compiled regex patterns to avoid recompilation on every event.
     */
    private static final Map<String, Pattern> regexCache = new HashMap<>();

    /**
     * Cache for parsed allowed values JSON.
     * Key: JSON string, Value: Set of allowed values for O(1) lookup.
     */
    private static final Map<String, Set<String>> allowedValuesCache = new HashMap<>();

    /**
     * Clears static caches. Used in testing to prevent cross-test contamination.
     */
    static void clearCaches() {
        regexCache.clear();
        allowedValuesCache.clear();
    }

    // =========================================================================
    // MAIN VALIDATION FUNCTION
    // =========================================================================

    /**
     * Validates runtime properties against all events in the EventSpecResponse.
     *
     * For each property:
     * - If property not in spec: no validation needed (empty result)
     * - If property in spec: check constraints and collect failed/passed eventIds
     * - Return whichever list is smaller for bandwidth optimization
     *
     * @param properties   The properties observed at runtime
     * @param specResponse The EventSpecResponse from the backend
     * @return ValidationResult with metadata and per-property results
     */
    public static ValidationResult validateEvent(Map<String, ?> properties,
                                                 EventSpecResponse specResponse) {
        List<String> allEventIds = collectAllEventIds(specResponse.events);

        Map<String, PropertyConstraints> constraintsByProperty =
                collectConstraintsByPropertyName(specResponse.events);

        Map<String, PropertyValidationResult> propertyResults = new HashMap<>();

        for (String propName : properties.keySet()) {
            Object value = properties.get(propName);
            PropertyConstraints constraints = constraintsByProperty.get(propName);

            if (constraints == null) {
                // Property not in spec - no constraints to fail
                propertyResults.put(propName, new PropertyValidationResult());
            } else {
                PropertyValidationResult result =
                        validatePropertyConstraints(value, constraints, allEventIds, 0);
                propertyResults.put(propName, result);
            }
        }

        ValidationResult result = new ValidationResult();
        result.metadata = specResponse.metadata;
        result.propertyResults = propertyResults;
        return result;
    }

    // =========================================================================
    // HELPER FUNCTIONS
    // =========================================================================

    /**
     * Collects all eventIds (baseEventId + variantIds) from all events.
     */
    private static List<String> collectAllEventIds(List<EventSpecEntry> events) {
        List<String> ids = new ArrayList<>();
        for (EventSpecEntry event : events) {
            ids.add(event.baseEventId);
            for (String variantId : event.variantIds) {
                ids.add(variantId);
            }
        }
        return ids;
    }

    /**
     * Collects all property constraints from all events into a single lookup table.
     *
     * When multiple events define the same property, their constraint mappings
     * (pinnedValues, allowedValues, etc.) are merged by unioning the eventId arrays.
     */
    private static Map<String, PropertyConstraints> collectConstraintsByPropertyName(
            List<EventSpecEntry> events) {
        if (events.isEmpty()) {
            return new HashMap<>();
        }

        // Fast path: single event, return props directly
        if (events.size() == 1) {
            return events.get(0).props;
        }

        // Multiple events: aggregate constraints
        Map<String, PropertyConstraints> result = new HashMap<>();

        for (EventSpecEntry event : events) {
            for (Map.Entry<String, PropertyConstraints> propEntry : event.props.entrySet()) {
                String propName = propEntry.getKey();
                PropertyConstraints constraints = propEntry.getValue();

                if (!result.containsKey(propName)) {
                    // First time seeing this property - deep copy
                    PropertyConstraints copy = new PropertyConstraints();
                    copy.type = constraints.type;
                    copy.required = constraints.required;
                    copy.isList = constraints.isList;
                    copy.pinnedValues = constraints.pinnedValues != null
                            ? deepCopyConstraintMapping(constraints.pinnedValues) : null;
                    copy.allowedValues = constraints.allowedValues != null
                            ? deepCopyConstraintMapping(constraints.allowedValues) : null;
                    copy.regexPatterns = constraints.regexPatterns != null
                            ? deepCopyConstraintMapping(constraints.regexPatterns) : null;
                    copy.minMaxRanges = constraints.minMaxRanges != null
                            ? deepCopyConstraintMapping(constraints.minMaxRanges) : null;
                    copy.children = constraints.children != null
                            ? deepCopyChildren(constraints.children) : null;
                    result.put(propName, copy);
                } else {
                    // Merge constraint mappings from additional events
                    PropertyConstraints existing = result.get(propName);
                    mergeConstraintMappings(existing, constraints);
                    // Recursively merge nested children
                    if (constraints.children != null) {
                        if (existing.children == null) {
                            existing.children = deepCopyChildren(constraints.children);
                        } else {
                            mergeChildren(existing.children, constraints.children);
                        }
                    }
                }
            }
        }

        return result;
    }

    // =========================================================================
    // DEEP COPY HELPERS
    // =========================================================================

    /**
     * Deep copies a constraint mapping (pinnedValues, allowedValues, etc.),
     * including the arrays inside to avoid shared references.
     */
    private static Map<String, List<String>> deepCopyConstraintMapping(
            Map<String, List<String>> mapping) {
        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : mapping.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    /**
     * Deep copies children constraints recursively.
     */
    private static Map<String, PropertyConstraints> deepCopyChildren(
            Map<String, PropertyConstraints> children) {
        Map<String, PropertyConstraints> result = new HashMap<>();
        for (Map.Entry<String, PropertyConstraints> entry : children.entrySet()) {
            PropertyConstraints constraints = entry.getValue();
            PropertyConstraints copy = new PropertyConstraints();
            copy.type = constraints.type;
            copy.required = constraints.required;
            copy.isList = constraints.isList;
            copy.pinnedValues = constraints.pinnedValues != null
                    ? deepCopyConstraintMapping(constraints.pinnedValues) : null;
            copy.allowedValues = constraints.allowedValues != null
                    ? deepCopyConstraintMapping(constraints.allowedValues) : null;
            copy.regexPatterns = constraints.regexPatterns != null
                    ? deepCopyConstraintMapping(constraints.regexPatterns) : null;
            copy.minMaxRanges = constraints.minMaxRanges != null
                    ? deepCopyConstraintMapping(constraints.minMaxRanges) : null;
            copy.children = constraints.children != null
                    ? deepCopyChildren(constraints.children) : null;
            result.put(entry.getKey(), copy);
        }
        return result;
    }

    // =========================================================================
    // MERGE HELPERS
    // =========================================================================

    /**
     * Merges constraint mappings (pinnedValues, allowedValues, etc.) from source into target.
     */
    private static void mergeConstraintMappings(PropertyConstraints target,
                                                PropertyConstraints source) {
        target.pinnedValues = mergeMapping(target.pinnedValues, source.pinnedValues);
        target.allowedValues = mergeMapping(target.allowedValues, source.allowedValues);
        target.regexPatterns = mergeMapping(target.regexPatterns, source.regexPatterns);
        target.minMaxRanges = mergeMapping(target.minMaxRanges, source.minMaxRanges);
    }

    /**
     * Merges a single constraint mapping from source into target,
     * unioning the eventId arrays for each constraint key.
     */
    private static Map<String, List<String>> mergeMapping(
            Map<String, List<String>> target, Map<String, List<String>> source) {
        if (source == null) {
            return target;
        }
        if (target == null) {
            target = new HashMap<>();
        }
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            String key = entry.getKey();
            List<String> sourceIds = entry.getValue();
            if (target.containsKey(key)) {
                Set<String> merged = new LinkedHashSet<>(target.get(key));
                merged.addAll(sourceIds);
                target.put(key, new ArrayList<>(merged));
            } else {
                target.put(key, new ArrayList<>(sourceIds));
            }
        }
        return target;
    }

    /**
     * Merges children constraints from source into target recursively.
     */
    private static void mergeChildren(Map<String, PropertyConstraints> target,
                                      Map<String, PropertyConstraints> source) {
        for (Map.Entry<String, PropertyConstraints> entry : source.entrySet()) {
            String propName = entry.getKey();
            PropertyConstraints sourceConstraints = entry.getValue();

            if (!target.containsKey(propName)) {
                // New child property - deep copy
                PropertyConstraints copy = new PropertyConstraints();
                copy.type = sourceConstraints.type;
                copy.required = sourceConstraints.required;
                copy.isList = sourceConstraints.isList;
                copy.pinnedValues = sourceConstraints.pinnedValues != null
                        ? deepCopyConstraintMapping(sourceConstraints.pinnedValues) : null;
                copy.allowedValues = sourceConstraints.allowedValues != null
                        ? deepCopyConstraintMapping(sourceConstraints.allowedValues) : null;
                copy.regexPatterns = sourceConstraints.regexPatterns != null
                        ? deepCopyConstraintMapping(sourceConstraints.regexPatterns) : null;
                copy.minMaxRanges = sourceConstraints.minMaxRanges != null
                        ? deepCopyConstraintMapping(sourceConstraints.minMaxRanges) : null;
                copy.children = sourceConstraints.children != null
                        ? deepCopyChildren(sourceConstraints.children) : null;
                target.put(propName, copy);
            } else {
                PropertyConstraints targetConstraints = target.get(propName);
                mergeConstraintMappings(targetConstraints, sourceConstraints);
                if (sourceConstraints.children != null) {
                    if (targetConstraints.children == null) {
                        targetConstraints.children = deepCopyChildren(sourceConstraints.children);
                    } else {
                        mergeChildren(targetConstraints.children, sourceConstraints.children);
                    }
                }
            }
        }
    }

    // =========================================================================
    // PROPERTY VALIDATION
    // =========================================================================

    /**
     * Validates a property value against its constraints.
     * Returns the validation result with either failedEventIds or passedEventIds
     * (whichever is smaller for bandwidth optimization).
     *
     * @param depth Current recursion depth (internal use)
     */
    private static PropertyValidationResult validatePropertyConstraints(
            Object value, PropertyConstraints constraints,
            List<String> allEventIds, int depth) {

        // Stop recursion at depth 2+
        if (depth >= MAX_CHILD_DEPTH) {
            return new PropertyValidationResult();
        }

        // Handle list types (isList=true)
        if (constraints.isList != null && constraints.isList) {
            return validateListProperty(value, constraints, allEventIds, depth);
        }

        // Handle nested object properties with children (single object, not list)
        if (constraints.children != null) {
            return validateObjectProperty(value, constraints, allEventIds, depth);
        }

        // For primitive properties: skip validation for null on non-required properties
        if (value == null && !constraints.required) {
            return new PropertyValidationResult();
        }

        // Validate value constraints for primitive properties
        return validatePrimitiveProperty(value, constraints, allEventIds);
    }

    /**
     * Validates a primitive property (not list, not object with children).
     */
    private static PropertyValidationResult validatePrimitiveProperty(
            Object value, PropertyConstraints constraints, List<String> allEventIds) {
        Set<String> failedIds = new HashSet<>();

        if (constraints.pinnedValues != null) {
            checkPinnedValues(value, constraints.pinnedValues, failedIds);
        }

        if (constraints.allowedValues != null) {
            checkAllowedValues(value, constraints.allowedValues, failedIds);
        }

        if (constraints.regexPatterns != null) {
            checkRegexPatterns(value, constraints.regexPatterns, failedIds);
        }

        if (constraints.minMaxRanges != null) {
            checkMinMaxRanges(value, constraints.minMaxRanges, failedIds);
        }

        return buildValidationResult(failedIds, allEventIds);
    }

    /**
     * Validates an object property (single object with children).
     */
    private static PropertyValidationResult validateObjectProperty(
            Object value, PropertyConstraints constraints,
            List<String> allEventIds, int depth) {
        PropertyValidationResult result = new PropertyValidationResult();
        Map<String, PropertyValidationResult> childrenResults = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> valueObj = (value instanceof Map) ? (Map<String, Object>) value
                : new HashMap<>();

        for (Map.Entry<String, PropertyConstraints> childEntry : constraints.children.entrySet()) {
            String childName = childEntry.getKey();
            PropertyConstraints childConstraints = childEntry.getValue();
            Object childValue = valueObj.get(childName);

            PropertyValidationResult childResult =
                    validatePropertyConstraints(childValue, childConstraints, allEventIds, depth + 1);

            // Only include non-empty results
            if (childResult.failedEventIds != null || childResult.passedEventIds != null
                    || childResult.children != null) {
                childrenResults.put(childName, childResult);
            }
        }

        if (!childrenResults.isEmpty()) {
            result.children = childrenResults;
        }

        return result;
    }

    /**
     * Validates a list property (array of items).
     * For list of objects: validates each item's children.
     * For list of primitives: validates each item against constraints.
     */
    private static PropertyValidationResult validateListProperty(
            Object value, PropertyConstraints constraints,
            List<String> allEventIds, int depth) {
        PropertyValidationResult result = new PropertyValidationResult();

        // If value is not a List, we can't validate list items
        if (!(value instanceof List)) {
            return result;
        }

        @SuppressWarnings("unchecked")
        List<Object> listValue = (List<Object>) value;

        // List of objects with children
        if (constraints.children != null) {
            Map<String, PropertyValidationResult> childrenResults = new HashMap<>();

            for (Map.Entry<String, PropertyConstraints> childEntry : constraints.children.entrySet()) {
                String childName = childEntry.getKey();
                PropertyConstraints childConstraints = childEntry.getValue();
                Set<String> aggregatedFailedIds = new HashSet<>();

                for (Object item : listValue) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> itemObj = (item instanceof Map)
                            ? (Map<String, Object>) item : new HashMap<>();
                    Object childValue = itemObj.get(childName);
                    PropertyValidationResult childResult =
                            validatePropertyConstraints(childValue, childConstraints, allEventIds, depth + 1);

                    // Collect failed IDs from this item
                    if (childResult.failedEventIds != null) {
                        aggregatedFailedIds.addAll(childResult.failedEventIds);
                    }
                    // If passedEventIds is returned, failed = allEventIds - passedEventIds
                    if (childResult.passedEventIds != null) {
                        Set<String> passedSet = new HashSet<>(childResult.passedEventIds);
                        for (String id : allEventIds) {
                            if (!passedSet.contains(id)) {
                                aggregatedFailedIds.add(id);
                            }
                        }
                    }
                }

                // Build result for this child property
                if (!aggregatedFailedIds.isEmpty()) {
                    List<String> failedArray = new ArrayList<>(aggregatedFailedIds);
                    List<String> passedIds = new ArrayList<>();
                    for (String id : allEventIds) {
                        if (!aggregatedFailedIds.contains(id)) {
                            passedIds.add(id);
                        }
                    }

                    if (passedIds.size() < failedArray.size() && !passedIds.isEmpty()) {
                        PropertyValidationResult childResult = new PropertyValidationResult();
                        childResult.passedEventIds = passedIds;
                        childrenResults.put(childName, childResult);
                    } else {
                        PropertyValidationResult childResult = new PropertyValidationResult();
                        childResult.failedEventIds = failedArray;
                        childrenResults.put(childName, childResult);
                    }
                }
            }

            if (!childrenResults.isEmpty()) {
                result.children = childrenResults;
            }

            return result;
        }

        // List of primitives - validate each item against constraints
        Set<String> failedIds = new HashSet<>();

        for (Object item : listValue) {
            if (constraints.pinnedValues != null) {
                checkPinnedValues(item, constraints.pinnedValues, failedIds);
            }
            if (constraints.allowedValues != null) {
                checkAllowedValues(item, constraints.allowedValues, failedIds);
            }
            if (constraints.regexPatterns != null) {
                checkRegexPatterns(item, constraints.regexPatterns, failedIds);
            }
            if (constraints.minMaxRanges != null) {
                checkMinMaxRanges(item, constraints.minMaxRanges, failedIds);
            }
        }

        return buildValidationResult(failedIds, allEventIds);
    }

    // =========================================================================
    // CONSTRAINT VALIDATION FUNCTIONS
    // =========================================================================

    /**
     * Converts runtime value to string for comparison.
     * - null -> "null"
     * - Boolean/Number/String -> String.valueOf(value)
     * - Map -> new JSONObject(map).toString()
     * - List/Array -> new JSONArray(list).toString()
     * - Other -> String.valueOf(value)
     */
    private static String convertValueToString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Number || value instanceof String) {
            return String.valueOf(value);
        }
        if (value instanceof Map) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                return new JSONObject(map).toString();
            } catch (Exception e) {
                Log.w(TAG, "Failed to stringify Map value: " + e);
                return String.valueOf(value);
            }
        }
        if (value instanceof List) {
            try {
                return new JSONArray((List<?>) value).toString();
            } catch (Exception e) {
                Log.w(TAG, "Failed to stringify List value: " + e);
                return String.valueOf(value);
            }
        }
        if (value.getClass().isArray()) {
            try {
                return new JSONArray(value).toString();
            } catch (Exception e) {
                Log.w(TAG, "Failed to stringify array value: " + e);
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    /**
     * Checks pinned values constraint.
     * For each pinnedValue -> eventIds entry, if runtime value != pinnedValue, those eventIds FAIL.
     */
    private static void checkPinnedValues(Object value,
                                          Map<String, List<String>> pinnedValues,
                                          Set<String> failedIds) {
        String stringValue = convertValueToString(value);

        for (Map.Entry<String, List<String>> entry : pinnedValues.entrySet()) {
            String pinnedValue = entry.getKey();
            List<String> eventIds = entry.getValue();

            if (!stringValue.equals(pinnedValue)) {
                failedIds.addAll(eventIds);
            }
        }
    }

    /**
     * Checks allowed values constraint.
     * For each "[...array]" -> eventIds entry, if runtime value NOT in array, those eventIds FAIL.
     * Uses cached Set for O(1) lookup.
     */
    private static void checkAllowedValues(Object value,
                                           Map<String, List<String>> allowedValues,
                                           Set<String> failedIds) {
        String stringValue = convertValueToString(value);

        for (Map.Entry<String, List<String>> entry : allowedValues.entrySet()) {
            String allowedArrayJson = entry.getKey();
            List<String> eventIds = entry.getValue();

            Set<String> allowedSet = getOrParseAllowedValues(allowedArrayJson);
            if (allowedSet == null) {
                Log.w(TAG, "Invalid allowed values JSON: " + allowedArrayJson);
                continue;
            }
            if (!allowedSet.contains(stringValue)) {
                failedIds.addAll(eventIds);
            }
        }
    }

    /**
     * Checks regex pattern constraint.
     * For each pattern -> eventIds entry, if runtime value doesn't match pattern, those eventIds FAIL.
     * Non-string values fail all regex constraints.
     */
    private static void checkRegexPatterns(Object value,
                                           Map<String, List<String>> regexPatterns,
                                           Set<String> failedIds) {
        // Only check regex for String values
        if (!(value instanceof String)) {
            // Non-string values fail all regex constraints
            for (List<String> eventIds : regexPatterns.values()) {
                failedIds.addAll(eventIds);
            }
            return;
        }

        String stringValue = (String) value;

        for (Map.Entry<String, List<String>> entry : regexPatterns.entrySet()) {
            String pattern = entry.getKey();
            List<String> eventIds = entry.getValue();

            try {
                Pattern regex = getOrCompileRegex(pattern);
                if (!regex.matcher(stringValue).find()) {
                    failedIds.addAll(eventIds);
                }
            } catch (PatternSyntaxException e) {
                Log.w(TAG, "Invalid regex pattern: " + pattern);
            }
        }
    }

    /**
     * Checks min/max range constraint.
     * For each "min,max" -> eventIds entry, if runtime value < min OR > max, those eventIds FAIL.
     * Empty bounds: "0," means min=0 with no max, ",100" means no min with max=100.
     * Non-numeric values fail all min/max constraints.
     */
    private static void checkMinMaxRanges(Object value,
                                          Map<String, List<String>> minMaxRanges,
                                          Set<String> failedIds) {
        // Only check min/max for numeric values
        if (!(value instanceof Number)) {
            // Non-numeric values fail all min/max constraints
            for (List<String> eventIds : minMaxRanges.values()) {
                failedIds.addAll(eventIds);
            }
            return;
        }

        double numericValue = ((Number) value).doubleValue();

        // NaN values fail all min/max constraints
        if (Double.isNaN(numericValue)) {
            Log.w(TAG, "NaN value fails min/max constraint");
            for (List<String> eventIds : minMaxRanges.values()) {
                failedIds.addAll(eventIds);
            }
            return;
        }

        for (Map.Entry<String, List<String>> entry : minMaxRanges.entrySet()) {
            String rangeStr = entry.getKey();
            List<String> eventIds = entry.getValue();

            String[] parts = rangeStr.split(",", -1);
            String minStr = parts.length > 0 ? parts[0] : "";
            String maxStr = parts.length > 1 ? parts[1] : "";

            boolean hasMin = !minStr.isEmpty();
            boolean hasMax = !maxStr.isEmpty();

            double min = hasMin ? Double.parseDouble(minStr) : Double.NEGATIVE_INFINITY;
            double max = hasMax ? Double.parseDouble(maxStr) : Double.POSITIVE_INFINITY;

            // Check for invalid format
            if ((hasMin && Double.isNaN(min)) || (hasMax && Double.isNaN(max))) {
                Log.w(TAG, "Invalid min/max range: " + rangeStr);
                continue;
            }

            if (numericValue < min || numericValue > max) {
                failedIds.addAll(eventIds);
            }
        }
    }

    // =========================================================================
    // RESULT BUILDER
    // =========================================================================

    /**
     * Builds the validation result from failed IDs, returning whichever list is smaller.
     */
    private static PropertyValidationResult buildValidationResult(
            Set<String> failedIds, List<String> allEventIds) {
        List<String> passedIds = new ArrayList<>();
        for (String id : allEventIds) {
            if (!failedIds.contains(id)) {
                passedIds.add(id);
            }
        }
        List<String> failedArray = new ArrayList<>(failedIds);

        PropertyValidationResult result = new PropertyValidationResult();

        // If both are empty, return empty result
        if (failedArray.isEmpty() && passedIds.isEmpty()) {
            return result;
        }

        // Prefer passedEventIds only when strictly smaller than failedEventIds
        // When equal, prefer failedEventIds
        if (passedIds.size() < failedArray.size() && !passedIds.isEmpty()) {
            result.passedEventIds = passedIds;
        } else if (!failedArray.isEmpty()) {
            result.failedEventIds = failedArray;
        }

        return result;
    }

    // =========================================================================
    // CACHE HELPERS
    // =========================================================================

    /**
     * Gets a compiled regex from cache or compiles and caches it.
     */
    private static Pattern getOrCompileRegex(String pattern) {
        Pattern regex = regexCache.get(pattern);
        if (regex == null) {
            regex = Pattern.compile(pattern);
            regexCache.put(pattern, regex);
        }
        return regex;
    }

    /**
     * Parses allowed values JSON string and returns a Set for O(1) lookup.
     * Results are cached to avoid repeated JSON.parse calls.
     *
     * @return Set of allowed values, or null if JSON is invalid
     */
    private static Set<String> getOrParseAllowedValues(String jsonString) {
        Set<String> allowedSet = allowedValuesCache.get(jsonString);
        if (allowedSet == null) {
            try {
                JSONArray jsonArray = new JSONArray(jsonString);
                allowedSet = new HashSet<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    allowedSet.add(jsonArray.getString(i));
                }
                allowedValuesCache.put(jsonString, allowedSet);
            } catch (Exception e) {
                return null;
            }
        }
        return allowedSet;
    }
}
