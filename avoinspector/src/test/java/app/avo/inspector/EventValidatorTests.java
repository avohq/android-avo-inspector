package app.avo.inspector;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import com.google.re2j.PatternSyntaxException;

import static org.junit.Assert.*;

public class EventValidatorTests {

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    @Before
    public void setUp() {
        // Clear static caches between tests to avoid cross-test contamination
        EventValidator.clearCaches();
    }

    private EventSpecMetadata createMetadata(String schemaId, String branchId) {
        EventSpecMetadata metadata = new EventSpecMetadata();
        metadata.schemaId = schemaId;
        metadata.branchId = branchId;
        metadata.latestActionId = "action_1";
        metadata.sourceId = "source_1";
        return metadata;
    }

    private PropertyConstraints createConstraints() {
        PropertyConstraints c = new PropertyConstraints();
        c.type = "string";
        c.required = false;
        return c;
    }

    private PropertyConstraints createPinnedValueConstraints(Map<String, List<String>> pinnedValues) {
        PropertyConstraints c = createConstraints();
        c.pinnedValues = pinnedValues;
        return c;
    }

    private PropertyConstraints createPinnedValueConstraints(Map<String, List<String>> pinnedValues, boolean required) {
        PropertyConstraints c = createPinnedValueConstraints(pinnedValues);
        c.required = required;
        return c;
    }

    private PropertyConstraints createAllowedValuesConstraints(Map<String, List<String>> allowedValues) {
        PropertyConstraints c = createConstraints();
        c.allowedValues = allowedValues;
        return c;
    }

    private PropertyConstraints createAllowedValuesConstraints(Map<String, List<String>> allowedValues, boolean required) {
        PropertyConstraints c = createAllowedValuesConstraints(allowedValues);
        c.required = required;
        return c;
    }

    private PropertyConstraints createRegexConstraints(Map<String, List<String>> regexPatterns) {
        PropertyConstraints c = createConstraints();
        c.regexPatterns = regexPatterns;
        return c;
    }

    private PropertyConstraints createRegexConstraints(Map<String, List<String>> regexPatterns, boolean required) {
        PropertyConstraints c = createRegexConstraints(regexPatterns);
        c.required = required;
        return c;
    }

    private PropertyConstraints createMinMaxConstraints(Map<String, List<String>> minMaxRanges) {
        PropertyConstraints c = createConstraints();
        c.type = "float";
        c.minMaxRanges = minMaxRanges;
        return c;
    }

    private PropertyConstraints createMinMaxConstraints(Map<String, List<String>> minMaxRanges, boolean required) {
        PropertyConstraints c = createMinMaxConstraints(minMaxRanges);
        c.required = required;
        return c;
    }

    private PropertyConstraints createNestedConstraints(Map<String, PropertyConstraints> children) {
        PropertyConstraints c = createConstraints();
        c.type = "object";
        c.children = children;
        return c;
    }

    private PropertyConstraints createListConstraints(Map<String, List<String>> allowedValues) {
        PropertyConstraints c = createConstraints();
        c.isList = true;
        c.allowedValues = allowedValues;
        return c;
    }

    private PropertyConstraints createListWithMinMaxConstraints(Map<String, List<String>> minMaxRanges) {
        PropertyConstraints c = createConstraints();
        c.isList = true;
        c.type = "int";
        c.minMaxRanges = minMaxRanges;
        return c;
    }

    private PropertyConstraints createListWithRegexConstraints(Map<String, List<String>> regexPatterns) {
        PropertyConstraints c = createConstraints();
        c.isList = true;
        c.regexPatterns = regexPatterns;
        return c;
    }

    private PropertyConstraints createListOfObjectsConstraints(Map<String, PropertyConstraints> children) {
        PropertyConstraints c = createConstraints();
        c.isList = true;
        c.type = "object";
        c.children = children;
        return c;
    }

    private EventSpecEntry createEntry(String baseEventId, List<String> variantIds,
                                       Map<String, PropertyConstraints> props) {
        EventSpecEntry entry = new EventSpecEntry();
        entry.branchId = "branch_1";
        entry.baseEventId = baseEventId;
        entry.variantIds = variantIds != null ? variantIds : new ArrayList<>();
        entry.props = props != null ? props : new HashMap<>();
        return entry;
    }

    private EventSpecResponse createSpec(List<EventSpecEntry> events) {
        EventSpecResponse spec = new EventSpecResponse();
        spec.events = events;
        spec.metadata = createMetadata("schema_1", "branch_1");
        return spec;
    }

    private Map<String, List<String>> singleMapping(String key, String... eventIds) {
        Map<String, List<String>> map = new HashMap<>();
        map.put(key, Arrays.asList(eventIds));
        return map;
    }

    // =========================================================================
    // 1. EMPTY PROPERTIES
    // =========================================================================

    @Test
    public void emptyPropertiesReturnsEmptyPropertyResults() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        assertNotNull(result);
        assertNotNull(result.propertyResults);
        assertTrue(result.propertyResults.isEmpty());
        assertEquals("schema_1", result.metadata.schemaId);
    }

    // =========================================================================
    // 2. PROPERTY NOT IN SPEC
    // =========================================================================

    @Test
    public void propertyNotInSpecReturnsEmptyResult() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("known_prop", createConstraints());
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("unknown_prop", "value");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("unknown_prop");
        assertNotNull(pvr);
        assertNull(pvr.failedEventIds);
        assertNull(pvr.passedEventIds);
        assertNull(pvr.children);
    }

    // =========================================================================
    // 3. PINNED VALUE MATCH
    // =========================================================================

    @Test
    public void pinnedValueMatchReturnsNoFailure() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("status", createPinnedValueConstraints(singleMapping("active", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("status", "active");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("status");
        assertNull(pvr.failedEventIds);
        assertNull(pvr.passedEventIds);
    }

    // =========================================================================
    // 4. PINNED VALUE MISMATCH
    // =========================================================================

    @Test
    public void pinnedValueMismatchReturnsFailedEventIds() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("status", createPinnedValueConstraints(singleMapping("active", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("status", "inactive");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("status");
        assertNotNull(pvr.failedEventIds);
        assertTrue(pvr.failedEventIds.contains("evt_1"));
    }

    // =========================================================================
    // 5. ALLOWED VALUE IN LIST
    // =========================================================================

    @Test
    public void allowedValueInListReturnsNoFailure() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("color", createAllowedValuesConstraints(
                singleMapping("[\"red\",\"green\",\"blue\"]", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("color", "red");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("color");
        assertNull(pvr.failedEventIds);
    }

    // =========================================================================
    // 6. ALLOWED VALUE NOT IN LIST
    // =========================================================================

    @Test
    public void allowedValueNotInListReturnsFailedEventIds() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("color", createAllowedValuesConstraints(
                singleMapping("[\"red\",\"green\",\"blue\"]", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("color", "yellow");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("color");
        assertNotNull(pvr.failedEventIds);
        assertTrue(pvr.failedEventIds.contains("evt_1"));
    }

    // =========================================================================
    // 7. REGEX MATCH
    // =========================================================================

    @Test
    public void regexMatchReturnsNoFailure() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("email", createRegexConstraints(
                singleMapping("^[\\w\\-.]+@[\\w\\-]+\\.[a-z]{2,}$", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("email", "test@example.com");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("email");
        assertNull(pvr.failedEventIds);
    }

    // =========================================================================
    // 8. REGEX NON-MATCH
    // =========================================================================

    @Test
    public void regexNonMatchReturnsFailedEventIds() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("email", createRegexConstraints(
                singleMapping("^[\\w\\-.]+@[\\w\\-]+\\.[a-z]{2,}$", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("email", "not-an-email");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("email");
        assertNotNull(pvr.failedEventIds);
        assertTrue(pvr.failedEventIds.contains("evt_1"));
    }

    // =========================================================================
    // 9. REGEX NON-STRING VALUE
    // =========================================================================

    @Test
    public void regexNonStringValueFailsAllConstraints() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("code", createRegexConstraints(singleMapping("^[A-Z]{3}$", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("code", 123);

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("code");
        assertNotNull(pvr.failedEventIds);
        assertTrue(pvr.failedEventIds.contains("evt_1"));
    }

    // =========================================================================
    // 10. MINMAX IN RANGE
    // =========================================================================

    @Test
    public void minMaxInRangeReturnsNoFailure() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("amount", createMinMaxConstraints(singleMapping("0,100", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("amount", 50);

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("amount");
        assertNull(pvr.failedEventIds);
    }

    @Test
    public void minMaxAtBoundaryReturnsNoFailure() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("score", createMinMaxConstraints(singleMapping("0,100", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> propertiesMin = new HashMap<>();
        propertiesMin.put("score", 0);
        ValidationResult resultMin = EventValidator.validateEvent(propertiesMin, spec);
        assertNull(resultMin.propertyResults.get("score").failedEventIds);

        Map<String, Object> propertiesMax = new HashMap<>();
        propertiesMax.put("score", 100);
        ValidationResult resultMax = EventValidator.validateEvent(propertiesMax, spec);
        assertNull(resultMax.propertyResults.get("score").failedEventIds);
    }

    // =========================================================================
    // 11. MINMAX OUT OF RANGE
    // =========================================================================

    @Test
    public void minMaxBelowMinReturnsFailedEventIds() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("amount", createMinMaxConstraints(singleMapping("0.01,10000", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("amount", 0);

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("amount");
        assertNotNull(pvr.failedEventIds);
        assertTrue(pvr.failedEventIds.contains("evt_1"));
    }

    @Test
    public void minMaxAboveMaxReturnsFailedEventIds() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("amount", createMinMaxConstraints(singleMapping("0.01,10000", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("amount", 15000);

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("amount");
        assertNotNull(pvr.failedEventIds);
        assertTrue(pvr.failedEventIds.contains("evt_1"));
    }

    // =========================================================================
    // 12. MINMAX NON-NUMERIC
    // =========================================================================

    @Test
    public void minMaxNonNumericValueFailsAllConstraints() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("price", createMinMaxConstraints(singleMapping("0,1000", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("price", "not a number");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("price");
        assertNotNull(pvr.failedEventIds);
        assertTrue(pvr.failedEventIds.contains("evt_1"));
    }

    // =========================================================================
    // 13. NESTED OBJECT CHILDREN
    // =========================================================================

    @Test
    public void nestedObjectChildrenValidatesChildProperties() {
        Map<String, PropertyConstraints> children = new HashMap<>();
        children.put("status", createPinnedValueConstraints(singleMapping("active", "evt_1")));

        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("user", createNestedConstraints(children));

        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        // Pass case
        Map<String, Object> userPass = new HashMap<>();
        userPass.put("status", "active");
        Map<String, Object> propertiesPass = new HashMap<>();
        propertiesPass.put("user", userPass);

        ValidationResult resultPass = EventValidator.validateEvent(propertiesPass, spec);
        PropertyValidationResult userPassResult = resultPass.propertyResults.get("user");
        // When all children pass, there are no non-empty results, so children may be null
        if (userPassResult.children != null) {
            PropertyValidationResult statusPass = userPassResult.children.get("status");
            if (statusPass != null) {
                assertNull(statusPass.failedEventIds);
            }
        }
        // No failures reported at parent level either
        assertNull(userPassResult.failedEventIds);

        // Fail case
        Map<String, Object> userFail = new HashMap<>();
        userFail.put("status", "inactive");
        Map<String, Object> propertiesFail = new HashMap<>();
        propertiesFail.put("user", userFail);

        ValidationResult resultFail = EventValidator.validateEvent(propertiesFail, spec);
        assertNotNull(resultFail.propertyResults.get("user").children);
        PropertyValidationResult statusFail = resultFail.propertyResults.get("user").children.get("status");
        assertNotNull(statusFail);
        assertNotNull(statusFail.failedEventIds);
        assertTrue(statusFail.failedEventIds.contains("evt_1"));
    }

    // =========================================================================
    // 14. LIST OF PRIMITIVES
    // =========================================================================

    @Test
    public void listOfPrimitivesValidatesEachItem() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("tags", createListConstraints(
                singleMapping("[\"red\",\"green\",\"blue\"]", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        // All valid
        Map<String, Object> propertiesPass = new HashMap<>();
        propertiesPass.put("tags", Arrays.asList("red", "green"));
        ValidationResult resultPass = EventValidator.validateEvent(propertiesPass, spec);
        assertNull(resultPass.propertyResults.get("tags").failedEventIds);

        // One invalid item
        Map<String, Object> propertiesFail = new HashMap<>();
        propertiesFail.put("tags", Arrays.asList("red", "yellow"));
        ValidationResult resultFail = EventValidator.validateEvent(propertiesFail, spec);
        assertNotNull(resultFail.propertyResults.get("tags").failedEventIds);
        assertTrue(resultFail.propertyResults.get("tags").failedEventIds.contains("evt_1"));
    }

    @Test
    public void listNonArrayValueReturnsEmptyResult() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("tags", createListConstraints(
                singleMapping("[\"red\",\"green\",\"blue\"]", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("tags", "red");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("tags");
        assertNull(pvr.failedEventIds);
        assertNull(pvr.passedEventIds);
        assertNull(pvr.children);
    }

    @Test
    public void listEmptyArrayReturnsNoFailure() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("tags", createListConstraints(
                singleMapping("[\"red\",\"green\",\"blue\"]", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("tags", Collections.emptyList());

        ValidationResult result = EventValidator.validateEvent(properties, spec);
        assertNull(result.propertyResults.get("tags").failedEventIds);
    }

    // =========================================================================
    // 15. BANDWIDTH OPTIMIZATION
    // =========================================================================

    @Test
    public void bandwidthOptimizationReturnsPassedWhenSmaller() {
        // 5 events: evt_1, evt_1.v1, evt_1.v2, evt_1.v3, evt_1.v4
        // Pinned: "basic" -> evt_1, "premium" -> evt_1.v1, evt_1.v2, evt_1.v3, evt_1.v4
        // Value "basic" matches evt_1 (1 pass), fails others (4 fail)
        // passedEventIds (1) < failedEventIds (4), so passedEventIds returned
        Map<String, List<String>> pinnedValues = new HashMap<>();
        pinnedValues.put("basic", Collections.singletonList("evt_1"));
        pinnedValues.put("premium", Arrays.asList("evt_1.v1", "evt_1.v2", "evt_1.v3", "evt_1.v4"));

        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("tier", createPinnedValueConstraints(pinnedValues));

        EventSpecEntry entry = createEntry("evt_1",
                Arrays.asList("evt_1.v1", "evt_1.v2", "evt_1.v3", "evt_1.v4"), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("tier", "basic");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("tier");
        assertNotNull(pvr.passedEventIds);
        assertNull(pvr.failedEventIds);
        assertTrue(pvr.passedEventIds.contains("evt_1"));
        assertEquals(1, pvr.passedEventIds.size());
    }

    @Test
    public void bandwidthOptimizationPrefersFailedWhenEqual() {
        // 2 events: evt_1, evt_1.v1
        // Pinned: "light" -> evt_1, "dark" -> evt_1.v1
        // Value "light" matches evt_1, fails evt_1.v1
        // 1 pass = 1 fail -> prefer failedEventIds
        Map<String, List<String>> pinnedValues = new HashMap<>();
        pinnedValues.put("light", Collections.singletonList("evt_1"));
        pinnedValues.put("dark", Collections.singletonList("evt_1.v1"));

        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("mode", createPinnedValueConstraints(pinnedValues));

        EventSpecEntry entry = createEntry("evt_1",
                Collections.singletonList("evt_1.v1"), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("mode", "light");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("mode");
        assertNotNull(pvr.failedEventIds);
        assertNull(pvr.passedEventIds);
        assertTrue(pvr.failedEventIds.contains("evt_1.v1"));
        assertEquals(1, pvr.failedEventIds.size());
    }

    // =========================================================================
    // 16. MULTIPLE EVENTS MERGED
    // =========================================================================

    @Test
    public void multipleEventsMergedConstraintsCorrectly() {
        // Event 1: status pinned to "active" for evt_1
        Map<String, PropertyConstraints> props1 = new HashMap<>();
        props1.put("status", createPinnedValueConstraints(singleMapping("active", "evt_1")));
        EventSpecEntry entry1 = createEntry("evt_1", Collections.emptyList(), props1);

        // Event 2: status pinned to "inactive" for evt_2
        Map<String, PropertyConstraints> props2 = new HashMap<>();
        props2.put("status", createPinnedValueConstraints(singleMapping("inactive", "evt_2")));
        EventSpecEntry entry2 = createEntry("evt_2", Collections.emptyList(), props2);

        EventSpecResponse spec = createSpec(Arrays.asList(entry1, entry2));

        Map<String, Object> properties = new HashMap<>();
        properties.put("status", "active");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("status");
        // evt_1 passes (active matches), evt_2 fails (active != inactive)
        if (pvr.failedEventIds != null) {
            assertTrue(pvr.failedEventIds.contains("evt_2"));
            assertFalse(pvr.failedEventIds.contains("evt_1"));
        } else if (pvr.passedEventIds != null) {
            assertTrue(pvr.passedEventIds.contains("evt_1"));
            assertFalse(pvr.passedEventIds.contains("evt_2"));
        }
    }

    // =========================================================================
    // 17. DEPTH LIMIT
    // =========================================================================

    @Test
    public void depthLimitStopsAtMaxChildDepth() {
        // order (depth 0) -> shipping (depth 1) -> address (depth 2) -> country (depth 3)
        // Only depth 0 and 1 are validated. address at depth 2 should not be validated.
        Map<String, PropertyConstraints> countryChildren = new HashMap<>();
        countryChildren.put("country", createPinnedValueConstraints(singleMapping("US", "evt_1")));

        Map<String, PropertyConstraints> addressChildren = new HashMap<>();
        addressChildren.put("address", createNestedConstraints(countryChildren));

        Map<String, PropertyConstraints> shippingChildren = new HashMap<>();
        shippingChildren.put("shipping", createNestedConstraints(addressChildren));

        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("order", createNestedConstraints(shippingChildren));

        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        // Value "CA" would fail if validated, but depth >= MAX_CHILD_DEPTH (2) stops it
        Map<String, Object> countryMap = new HashMap<>();
        countryMap.put("country", "CA");
        Map<String, Object> addressMap = new HashMap<>();
        addressMap.put("address", countryMap);
        Map<String, Object> shippingMap = new HashMap<>();
        shippingMap.put("shipping", addressMap);
        Map<String, Object> properties = new HashMap<>();
        properties.put("order", shippingMap);

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult orderResult = result.propertyResults.get("order");
        // No validation failures at depth 2+
        assertNull(orderResult.failedEventIds);
        assertNull(orderResult.passedEventIds);
    }

    @Test
    public void depthLimitValidatesWithinLimit() {
        // order (depth 0) -> status (depth 1) with pinned value
        Map<String, PropertyConstraints> children = new HashMap<>();
        children.put("status", createPinnedValueConstraints(singleMapping("pending", "evt_1")));

        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("order", createNestedConstraints(children));

        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        // Should validate at depth 1
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("status", "shipped");
        Map<String, Object> properties = new HashMap<>();
        properties.put("order", orderMap);

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult orderResult = result.propertyResults.get("order");
        assertNotNull(orderResult.children);
        PropertyValidationResult statusResult = orderResult.children.get("status");
        assertNotNull(statusResult);
        assertNotNull(statusResult.failedEventIds);
        assertTrue(statusResult.failedEventIds.contains("evt_1"));
    }

    // =========================================================================
    // ADDITIONAL TESTS: Optional null handling
    // =========================================================================

    @Test
    public void nullValueOnOptionalPropertySkipsValidation() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("optionalProp", createPinnedValueConstraints(
                singleMapping("expected", "evt_1"), false));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("optionalProp", null);

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("optionalProp");
        assertNull(pvr.failedEventIds);
    }

    @Test
    public void nullValueOnRequiredPropertyFailsValidation() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("requiredProp", createPinnedValueConstraints(
                singleMapping("expected", "evt_1"), true));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("requiredProp", null);

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("requiredProp");
        assertNotNull(pvr.failedEventIds);
        assertTrue(pvr.failedEventIds.contains("evt_1"));
    }

    // =========================================================================
    // ADDITIONAL TESTS: MinMax open-ended ranges
    // =========================================================================

    @Test
    public void minMaxMinOnlyRangeAllowsLargeValues() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("price", createMinMaxConstraints(singleMapping("0,", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("price", 999999);
        ValidationResult resultPass = EventValidator.validateEvent(properties, spec);
        assertNull(resultPass.propertyResults.get("price").failedEventIds);

        Map<String, Object> propertiesFail = new HashMap<>();
        propertiesFail.put("price", -1);
        ValidationResult resultFail = EventValidator.validateEvent(propertiesFail, spec);
        assertNotNull(resultFail.propertyResults.get("price").failedEventIds);
        assertTrue(resultFail.propertyResults.get("price").failedEventIds.contains("evt_1"));
    }

    @Test
    public void minMaxMaxOnlyRangeAllowsSmallValues() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("score", createMinMaxConstraints(singleMapping(",100", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("score", -999999);
        ValidationResult resultPass = EventValidator.validateEvent(properties, spec);
        assertNull(resultPass.propertyResults.get("score").failedEventIds);

        Map<String, Object> propertiesFail = new HashMap<>();
        propertiesFail.put("score", 101);
        ValidationResult resultFail = EventValidator.validateEvent(propertiesFail, spec);
        assertNotNull(resultFail.propertyResults.get("score").failedEventIds);
        assertTrue(resultFail.propertyResults.get("score").failedEventIds.contains("evt_1"));
    }

    // =========================================================================
    // ADDITIONAL TESTS: Boolean value stringification
    // =========================================================================

    @Test
    public void booleanValueStringifiedForPinnedValueComparison() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("enabled", createPinnedValueConstraints(singleMapping("true", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("enabled", true);

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("enabled");
        assertNull(pvr.failedEventIds);
    }

    // =========================================================================
    // ADDITIONAL TESTS: List with minmax
    // =========================================================================

    @Test
    public void listWithMinMaxValidatesEachItem() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("quantities", createListWithMinMaxConstraints(singleMapping("1,99", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        // All in range
        Map<String, Object> propertiesPass = new HashMap<>();
        propertiesPass.put("quantities", Arrays.asList(1, 50, 99));
        ValidationResult resultPass = EventValidator.validateEvent(propertiesPass, spec);
        assertNull(resultPass.propertyResults.get("quantities").failedEventIds);

        // One out of range
        Map<String, Object> propertiesFail = new HashMap<>();
        propertiesFail.put("quantities", Arrays.asList(1, 50, 100));
        ValidationResult resultFail = EventValidator.validateEvent(propertiesFail, spec);
        assertNotNull(resultFail.propertyResults.get("quantities").failedEventIds);
        assertTrue(resultFail.propertyResults.get("quantities").failedEventIds.contains("evt_1"));
    }

    // =========================================================================
    // ADDITIONAL TESTS: List with regex
    // =========================================================================

    @Test
    public void listWithRegexValidatesEachItem() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("codes", createListWithRegexConstraints(singleMapping("^[A-Z]{3}$", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        // All match
        Map<String, Object> propertiesPass = new HashMap<>();
        propertiesPass.put("codes", Arrays.asList("USD", "EUR", "GBP"));
        ValidationResult resultPass = EventValidator.validateEvent(propertiesPass, spec);
        assertNull(resultPass.propertyResults.get("codes").failedEventIds);

        // One doesn't match
        Map<String, Object> propertiesFail = new HashMap<>();
        propertiesFail.put("codes", Arrays.asList("USD", "euro", "GBP"));
        ValidationResult resultFail = EventValidator.validateEvent(propertiesFail, spec);
        assertNotNull(resultFail.propertyResults.get("codes").failedEventIds);
        assertTrue(resultFail.propertyResults.get("codes").failedEventIds.contains("evt_1"));
    }

    // =========================================================================
    // ADDITIONAL TESTS: List of objects
    // =========================================================================

    @Test
    public void listOfObjectsValidatesChildrenAcrossItems() {
        Map<String, PropertyConstraints> children = new HashMap<>();
        children.put("quantity", createMinMaxConstraints(singleMapping("1,99", "evt_1")));
        children.put("item_type", createAllowedValuesConstraints(
                singleMapping("[\"product\",\"service\"]", "evt_1")));

        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("items", createListOfObjectsConstraints(children));

        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        // All valid
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("quantity", 5);
        item1.put("item_type", "product");
        items.add(item1);
        Map<String, Object> item2 = new HashMap<>();
        item2.put("quantity", 10);
        item2.put("item_type", "service");
        items.add(item2);

        Map<String, Object> propertiesPass = new HashMap<>();
        propertiesPass.put("items", items);
        ValidationResult resultPass = EventValidator.validateEvent(propertiesPass, spec);

        PropertyValidationResult itemsResult = resultPass.propertyResults.get("items");
        if (itemsResult.children != null) {
            PropertyValidationResult qtyResult = itemsResult.children.get("quantity");
            if (qtyResult != null) {
                assertNull(qtyResult.failedEventIds);
            }
        }

        // One item has invalid quantity
        List<Map<String, Object>> itemsFail = new ArrayList<>();
        Map<String, Object> itemOk = new HashMap<>();
        itemOk.put("quantity", 5);
        itemsFail.add(itemOk);
        Map<String, Object> itemBad = new HashMap<>();
        itemBad.put("quantity", 100);
        itemsFail.add(itemBad);

        Map<String, Object> propertiesFail = new HashMap<>();
        propertiesFail.put("items", itemsFail);
        ValidationResult resultFail = EventValidator.validateEvent(propertiesFail, spec);

        PropertyValidationResult itemsResultFail = resultFail.propertyResults.get("items");
        assertNotNull(itemsResultFail.children);
        PropertyValidationResult qtyFail = itemsResultFail.children.get("quantity");
        assertNotNull(qtyFail);
        assertNotNull(qtyFail.failedEventIds);
        assertTrue(qtyFail.failedEventIds.contains("evt_1"));
    }

    // =========================================================================
    // ADDITIONAL TESTS: Invalid regex gracefully handled
    // =========================================================================

    @Test
    public void invalidRegexDoesNotThrow() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("field", createRegexConstraints(singleMapping("[invalid(regex", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("field", "test");

        // Should not throw
        ValidationResult result = EventValidator.validateEvent(properties, spec);
        assertNotNull(result);
    }

    // =========================================================================
    // ADDITIONAL TESTS: Convert value to string
    // =========================================================================

    @Test
    public void numericValueConvertedToStringForAllowedValues() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("level", createAllowedValuesConstraints(
                singleMapping("[\"1\",\"2\",\"3\"]", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("level", 2);

        ValidationResult result = EventValidator.validateEvent(properties, spec);
        assertNull(result.propertyResults.get("level").failedEventIds);
    }

    // =========================================================================
    // ADDITIONAL TESTS: Variant IDs collection
    // =========================================================================

    @Test
    public void variantIdsCollectedCorrectly() {
        Map<String, List<String>> pinnedValues = new HashMap<>();
        pinnedValues.put("basic", Collections.singletonList("evt_1"));
        pinnedValues.put("premium", Collections.singletonList("evt_1.v1"));
        pinnedValues.put("enterprise", Collections.singletonList("evt_1.v2"));

        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("tier", createPinnedValueConstraints(pinnedValues));

        EventSpecEntry entry = createEntry("evt_1", Arrays.asList("evt_1.v1", "evt_1.v2"), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("tier", "premium");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("tier");
        // evt_1.v1 passes, evt_1 and evt_1.v2 fail
        if (pvr.failedEventIds != null) {
            assertTrue(pvr.failedEventIds.contains("evt_1"));
            assertTrue(pvr.failedEventIds.contains("evt_1.v2"));
            assertFalse(pvr.failedEventIds.contains("evt_1.v1"));
        } else if (pvr.passedEventIds != null) {
            assertTrue(pvr.passedEventIds.contains("evt_1.v1"));
            assertFalse(pvr.passedEventIds.contains("evt_1"));
            assertFalse(pvr.passedEventIds.contains("evt_1.v2"));
        }
    }

    // =========================================================================
    // ADDITIONAL TESTS: Empty events
    // =========================================================================

    @Test
    public void emptyEventsArrayReturnsEmptyResults() {
        EventSpecResponse spec = createSpec(Collections.emptyList());

        Map<String, Object> properties = new HashMap<>();
        properties.put("any_prop", "value");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("any_prop");
        assertNotNull(pvr);
        assertNull(pvr.failedEventIds);
        assertNull(pvr.passedEventIds);
    }

    // =========================================================================
    // ADDITIONAL TESTS: Negative number ranges
    // =========================================================================

    @Test
    public void negativeNumberRangeValidatesCorrectly() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("temperature", createMinMaxConstraints(singleMapping("-100,-50", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        // In range
        Map<String, Object> propertiesInRange = new HashMap<>();
        propertiesInRange.put("temperature", -75);
        ValidationResult resultInRange = EventValidator.validateEvent(propertiesInRange, spec);
        assertNull(resultInRange.propertyResults.get("temperature").failedEventIds);

        // Too high
        Map<String, Object> propertiesTooHigh = new HashMap<>();
        propertiesTooHigh.put("temperature", -40);
        ValidationResult resultTooHigh = EventValidator.validateEvent(propertiesTooHigh, spec);
        assertNotNull(resultTooHigh.propertyResults.get("temperature").failedEventIds);
        assertTrue(resultTooHigh.propertyResults.get("temperature").failedEventIds.contains("evt_1"));

        // Too low
        Map<String, Object> propertiesTooLow = new HashMap<>();
        propertiesTooLow.put("temperature", -110);
        ValidationResult resultTooLow = EventValidator.validateEvent(propertiesTooLow, spec);
        assertNotNull(resultTooLow.propertyResults.get("temperature").failedEventIds);
        assertTrue(resultTooLow.propertyResults.get("temperature").failedEventIds.contains("evt_1"));
    }

    // =========================================================================
    // ADDITIONAL TESTS: Map value stringification
    // =========================================================================

    @Test
    public void mapValueStringifiedForPinnedValueComparison() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        // Note: JSONObject key ordering may vary, so we test with a single key
        props.put("data", createPinnedValueConstraints(singleMapping("{\"key\":\"value\"}", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("key", "value");
        Map<String, Object> properties = new HashMap<>();
        properties.put("data", dataMap);

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        // JSONObject(map).toString() for {"key":"value"} should match
        PropertyValidationResult pvr = result.propertyResults.get("data");
        assertNull(pvr.failedEventIds);
    }

    // =========================================================================
    // ADDITIONAL TESTS: No constraints on property
    // =========================================================================

    @Test
    public void propertyWithNoConstraintsReturnsEmptyResult() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("unconstrained_prop", createConstraints());
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("unconstrained_prop", "any value");

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("unconstrained_prop");
        assertNull(pvr.failedEventIds);
        assertNull(pvr.passedEventIds);
        assertNull(pvr.children);
    }

    // =========================================================================
    // EDGE CASES: convertValueToString
    // =========================================================================

    @Test
    public void convertValueToStringWithArrayDoesNotCrash() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        PropertyConstraints c = createConstraints();
        c.pinnedValues = singleMapping("[1,2,3]", "evt_1");
        props.put("data", c);
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("data", new int[]{1, 2, 3});

        // Should not throw, regardless of whether it matches the pinned value
        ValidationResult result = EventValidator.validateEvent(properties, spec);
        assertNotNull(result);
        assertNotNull(result.propertyResults.get("data"));
    }

    @Test
    public void convertValueToStringWithCustomObject() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        PropertyConstraints c = createConstraints();
        c.pinnedValues = singleMapping("CustomValue", "evt_1");
        props.put("data", c);
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("data", new Object() {
            @Override
            public String toString() {
                return "CustomValue";
            }
        });

        ValidationResult result = EventValidator.validateEvent(properties, spec);
        PropertyValidationResult pvr = result.propertyResults.get("data");
        // String.valueOf calls toString(), which returns "CustomValue" matching the pinned value
        assertNull(pvr.failedEventIds);
    }

    // =========================================================================
    // EDGE CASES: checkMinMax
    // =========================================================================

    @Test
    public void checkMinMaxWithNaNValue() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("amount", createMinMaxConstraints(singleMapping("0,100", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("amount", Double.NaN);

        ValidationResult result = EventValidator.validateEvent(properties, spec);

        PropertyValidationResult pvr = result.propertyResults.get("amount");
        assertNotNull(pvr.failedEventIds);
        assertTrue(pvr.failedEventIds.contains("evt_1"));
    }

    @Test
    public void checkMinMaxWithInvalidRangeFormat() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("amount", createMinMaxConstraints(singleMapping("abc,def", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("amount", 50);

        // Should not crash, invalid range is skipped
        ValidationResult result = EventValidator.validateEvent(properties, spec);
        PropertyValidationResult pvr = result.propertyResults.get("amount");
        // No failures because the invalid range is skipped via NumberFormatException
        assertNull(pvr.failedEventIds);
    }

    // =========================================================================
    // EDGE CASES: checkAllowedValues
    // =========================================================================

    @Test
    public void checkAllowedValuesWithInvalidJson() {
        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("color", createAllowedValuesConstraints(singleMapping("not[json", "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("color", "red");

        // Should not crash, invalid JSON is skipped
        ValidationResult result = EventValidator.validateEvent(properties, spec);
        assertNotNull(result);
        PropertyValidationResult pvr = result.propertyResults.get("color");
        // Invalid JSON key is skipped, so no failure
        assertNull(pvr.failedEventIds);
    }

    // =========================================================================
    // EDGE CASES: null events in spec
    // =========================================================================

    @Test
    public void validateEventWithNullEventsInSpec() {
        EventSpecResponse spec = new EventSpecResponse();
        spec.events = null;
        spec.metadata = createMetadata("schema_1", "branch_1");

        Map<String, Object> properties = new HashMap<>();
        properties.put("any_prop", "value");

        // Should not crash — needs a null guard in validateEvent
        ValidationResult result = EventValidator.validateEvent(properties, spec);
        assertNotNull(result);
        assertNotNull(result.propertyResults);
    }

    // =========================================================================
    // EDGE CASES: addValidationChildren without property children
    // =========================================================================

    @Test
    public void addValidationChildrenWithoutPropertyChildren() throws Exception {
        // Schema has primitive property (no children)
        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        schema.put("name", new AvoEventSchemaType.AvoString());

        // Validation result has children data for this property
        ValidationResult validationResult = new ValidationResult();
        validationResult.metadata = new EventSpecMetadata();
        validationResult.propertyResults = new HashMap<>();
        PropertyValidationResult propResult = new PropertyValidationResult();
        propResult.children = new HashMap<>();
        PropertyValidationResult childResult = new PropertyValidationResult();
        childResult.failedEventIds = Arrays.asList("event1");
        propResult.children.put("subfield", childResult);
        validationResult.propertyResults.put("name", propResult);

        // remapPropertiesWithValidation should not crash
        JSONArray result = Util.remapPropertiesWithValidation(schema, validationResult);
        assertNotNull(result);
        assertEquals(1, result.length());
        JSONObject prop = result.getJSONObject(0);
        assertEquals("name", prop.optString("propertyName"));
        // Property should NOT have children since the schema property is a primitive
        assertFalse(prop.has("children"));
    }

    // =========================================================================
    // REDOS SAFETY TESTS
    // =========================================================================

    @Test(timeout = 1000)
    public void redosPatternCompletesQuicklyWithRe2j() {
        // (a+)+$ is a classic ReDoS pattern that causes catastrophic backtracking
        // in java.util.regex but is safe with RE2
        String redosPattern = "(a+)+$";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            sb.append('a');
        }
        sb.append('!');
        String evilInput = sb.toString();

        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("field", createRegexConstraints(singleMapping(redosPattern, "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("field", evilInput);

        long startNanos = System.nanoTime();
        ValidationResult result = EventValidator.validateEvent(properties, spec);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertNotNull(result);
        // RE2 should complete in well under 1 second; java.util.regex would hang
        assertTrue("ReDoS pattern took " + elapsedMillis + "ms, expected < 1000ms", elapsedMillis < 1000);
    }

    @Test
    public void unsupportedLookaheadPatternHandledGracefully() {
        // RE2 does not support lookaheads; this should be caught as PatternSyntaxException
        // and handled gracefully (no crash, no failure for that constraint)
        String lookaheadPattern = "(?=.*[A-Z]).*";

        Map<String, PropertyConstraints> props = new HashMap<>();
        props.put("field", createRegexConstraints(singleMapping(lookaheadPattern, "evt_1")));
        EventSpecEntry entry = createEntry("evt_1", Collections.emptyList(), props);
        EventSpecResponse spec = createSpec(Collections.singletonList(entry));

        Map<String, Object> properties = new HashMap<>();
        properties.put("field", "Hello");

        // Should not throw — the PatternSyntaxException is caught in checkRegexPatterns
        ValidationResult result = EventValidator.validateEvent(properties, spec);
        assertNotNull(result);
        // The invalid pattern is skipped, so no eventIds are failed by it
        PropertyValidationResult pvr = result.propertyResults.get("field");
        assertNull(pvr.failedEventIds);
    }
}
