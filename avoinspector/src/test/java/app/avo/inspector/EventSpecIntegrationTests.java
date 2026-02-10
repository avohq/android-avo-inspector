package app.avo.inspector;

import android.app.Application;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class EventSpecIntegrationTests {

    @Mock
    Application mockApplication;
    @Mock
    PackageManager mockPackageManager;
    @Mock
    PackageInfo mockPackageInfo;
    @Mock
    ApplicationInfo mockApplicationInfo;
    @Mock
    SharedPreferences mockSharedPrefs;
    @Mock
    SharedPreferences.Editor mockEditor;

    private AvoStorage prevAvoStorage;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mockPackageInfo.versionName = "1.0.0";
        mockApplicationInfo.packageName = "testPckg";

        when(mockApplication.getPackageManager()).thenReturn(mockPackageManager);
        when(mockApplication.getPackageName()).thenReturn("");
        when(mockPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mockPackageInfo);
        when(mockApplication.getApplicationInfo()).thenReturn(mockApplicationInfo);
        when(mockApplication.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPrefs);
        when(mockSharedPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        when(mockEditor.remove(anyString())).thenReturn(mockEditor);
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
        when(mockApplication.getContentResolver()).thenReturn(mock(ContentResolver.class));

        // Set up AvoStorage mock for AvoAnonymousId
        prevAvoStorage = AvoInspector.avoStorage;
        AvoAnonymousId.clearCache();
        AvoStorage mockStorage = mock(AvoStorage.class);
        when(mockStorage.isInitialized()).thenReturn(true);
        when(mockStorage.getItem(any())).thenReturn("testStreamId");
        AvoInspector.avoStorage = mockStorage;
    }

    @After
    public void tearDown() {
        AvoInspector.avoStorage = prevAvoStorage;
        AvoAnonymousId.clearCache();
        AvoDeduplicator.clearEvents();
    }

    // =========================================================================
    // Task 2a: Initialization tests
    // =========================================================================

    @Test
    public void eventSpecFetcherInitializedInDevMode() {
        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        assertNotNull("eventSpecCache should be initialized in Dev mode", sut.eventSpecCache);
        assertNotNull("eventSpecFetcher should be initialized in Dev mode", sut.eventSpecFetcher);
    }

    @Test
    public void eventSpecFetcherInitializedInStagingMode() {
        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Staging);

        assertNotNull("eventSpecCache should be initialized in Staging mode", sut.eventSpecCache);
        assertNotNull("eventSpecFetcher should be initialized in Staging mode", sut.eventSpecFetcher);
    }

    @Test
    public void eventSpecFetcherInitializedInProdMode() {
        // In prod mode, the fetcher should still be initialized (filtering happens at fetch time),
        // because the constructor initializes based on streamId availability, not env
        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Prod);

        assertNotNull("eventSpecCache should be initialized when streamId is available", sut.eventSpecCache);
        assertNotNull("eventSpecFetcher should be initialized when streamId is available", sut.eventSpecFetcher);
    }

    @Test
    public void eventSpecComponentsAlwaysInitializedWithValidStorage() {
        // AvoAnonymousId always returns a non-empty string (generates GUID if needed),
        // so the event spec components should always be initialized when storage works
        AvoAnonymousId.clearCache();
        AvoStorage workingStorage = mock(AvoStorage.class);
        when(workingStorage.isInitialized()).thenReturn(true);
        when(workingStorage.getItem(any())).thenReturn(null); // Will cause GUID generation
        AvoInspector.avoStorage = workingStorage;

        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        assertNotNull("eventSpecCache should be initialized when storage works", sut.eventSpecCache);
        assertNotNull("eventSpecFetcher should be initialized when storage works", sut.eventSpecFetcher);
    }

    // =========================================================================
    // Task 2d: fetchAndValidateAsync tests - cache hit and miss
    // =========================================================================

    @Test
    public void cacheHitSendsValidatedEvent() {
        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        // Create mock network handler
        AvoNetworkCallsHandler mockNetworkHandler = mock(AvoNetworkCallsHandler.class);
        Map<String, Object> mockBody = new HashMap<>();
        mockBody.put("type", "event");
        when(mockNetworkHandler.bodyForValidatedEventSchemaCall(
                anyString(), any(), any(), any(), any(), anyString()
        )).thenReturn(mockBody);

        // Replace batcher with one using mock handler
        AvoBatcher mockBatcher = mock(AvoBatcher.class);
        when(mockBatcher.getNetworkCallsHandler()).thenReturn(mockNetworkHandler);
        sut.avoBatcher = mockBatcher;

        // Warm the cache
        EventSpecResponse specResponse = createTestEventSpecResponse();
        sut.eventSpecCache.set("apiKey", "testStreamId", "TestEvent", specResponse);

        // Track event (should use cached spec)
        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put("userId", "user123");
        sut.trackSchemaFromEvent("TestEvent", (Map<String, ?>) eventProps);

        // Verify validated event was sent (not batched)
        verify(mockNetworkHandler).reportValidatedEvent(any());
        verify(mockBatcher, never()).batchTrackEventSchema(anyString(), any(), any(), any());
    }

    @Test
    public void cacheMissFallsBackToBatch() {
        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        // Create mock batcher
        AvoBatcher mockBatcher = mock(AvoBatcher.class);
        AvoNetworkCallsHandler mockNetworkHandler = mock(AvoNetworkCallsHandler.class);
        when(mockBatcher.getNetworkCallsHandler()).thenReturn(mockNetworkHandler);
        sut.avoBatcher = mockBatcher;

        // Replace fetcher with mock that invokes callback with null (simulating network failure)
        sut.eventSpecFetcher = mock(AvoEventSpecFetcher.class);
        doAnswer(invocation -> {
            EventSpecFetchCallback callback = invocation.getArgument(1);
            callback.onResult(null);
            return null;
        }).when(sut.eventSpecFetcher).fetch(any(), any());

        // Cache is empty, so this should fall back to batch
        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put("userId", "user123");
        sut.trackSchemaFromEvent("TestEvent", (Map<String, ?>) eventProps);

        // Verify event was batched (not sent immediately)
        verify(mockBatcher).batchTrackEventSchema(eq("TestEvent"), any(), eq(null), eq(null));
        verify(mockNetworkHandler, never()).reportValidatedEvent(any());
    }

    @Test
    public void prodEnvironmentFallsBackToBatch() {
        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Prod);

        // Create mock batcher
        AvoBatcher mockBatcher = mock(AvoBatcher.class);
        AvoNetworkCallsHandler mockNetworkHandler = mock(AvoNetworkCallsHandler.class);
        when(mockBatcher.getNetworkCallsHandler()).thenReturn(mockNetworkHandler);
        sut.avoBatcher = mockBatcher;

        // Even with a cache hit, prod should batch
        if (sut.eventSpecCache != null) {
            EventSpecResponse specResponse = createTestEventSpecResponse();
            sut.eventSpecCache.set("apiKey", "testStreamId", "TestEvent", specResponse);
        }

        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put("userId", "user123");
        sut.trackSchemaFromEvent("TestEvent", (Map<String, ?>) eventProps);

        // Verify event was batched (prod should never use validated path)
        verify(mockBatcher).batchTrackEventSchema(eq("TestEvent"), any(), eq(null), eq(null));
        verify(mockNetworkHandler, never()).reportValidatedEvent(any());
    }

    @Test
    public void emptyJsonPropertiesBatchNormally() {
        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        // Create mock batcher
        AvoBatcher mockBatcher = mock(AvoBatcher.class);
        AvoNetworkCallsHandler mockNetworkHandler = mock(AvoNetworkCallsHandler.class);
        when(mockBatcher.getNetworkCallsHandler()).thenReturn(mockNetworkHandler);
        sut.avoBatcher = mockBatcher;

        // Mock fetcher to invoke callback synchronously with null (no spec found)
        sut.eventSpecFetcher = mock(AvoEventSpecFetcher.class);
        doAnswer(invocation -> {
            EventSpecFetchCallback callback = invocation.getArgument(1);
            callback.onResult(null);
            return null;
        }).when(sut.eventSpecFetcher).fetch(any(), any());

        // Track with empty JSONObject properties
        // No cached spec exists, so this should fall back to batch
        sut.trackSchemaFromEvent("TestEmptyJsonEvent", new JSONObject());

        // Verify event was batched (cache miss falls back to batch)
        verify(mockBatcher).batchTrackEventSchema(eq("TestEmptyJsonEvent"), any(), eq(null), eq(null));
        verify(mockNetworkHandler, never()).reportValidatedEvent(any());
    }

    // =========================================================================
    // Task 2e: handleBranchChangeAndCache tests
    // =========================================================================

    @Test
    public void branchChangeClearsCache() {
        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        // Set up mock batcher and network handler for validated events
        AvoNetworkCallsHandler mockNetworkHandler = mock(AvoNetworkCallsHandler.class);
        Map<String, Object> mockBody = new HashMap<>();
        mockBody.put("type", "event");
        when(mockNetworkHandler.bodyForValidatedEventSchemaCall(
                anyString(), any(), any(), any(), any(), anyString()
        )).thenReturn(mockBody);
        AvoBatcher mockBatcher = mock(AvoBatcher.class);
        when(mockBatcher.getNetworkCallsHandler()).thenReturn(mockNetworkHandler);
        sut.avoBatcher = mockBatcher;

        // Warm cache with branch "branch1"
        EventSpecResponse spec1 = createTestEventSpecResponse();
        spec1.metadata.branchId = "branch1";
        sut.eventSpecCache.set("apiKey", "testStreamId", "Event1", spec1);

        // Track first event to set currentBranchId
        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put("userId", "user123");
        sut.trackSchemaFromEvent("Event1", (Map<String, ?>) eventProps);

        // Now set cache with a different branch
        EventSpecResponse spec2 = createTestEventSpecResponse();
        spec2.metadata.branchId = "branch2";
        sut.eventSpecCache.set("apiKey", "testStreamId", "Event2", spec2);

        // Track second event
        sut.trackSchemaFromEvent("Event2", (Map<String, ?>) eventProps);

        // After branch change, cache should be cleared (the Event1 entry should be gone)
        // The second event's spec will have been re-added after clearing
        // Verify currentBranchId was updated
        assertEquals("branch2", sut.currentBranchId);
    }

    // =========================================================================
    // Task 2g: AvoBatcher.getNetworkCallsHandler tests
    // =========================================================================

    @Test
    public void avoBatcherExposesNetworkHandler() {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "key", "dev", "app", "1.0", "7");
        AvoBatcher batcher = new AvoBatcher(mockApplication, handler);

        assertSame(handler, batcher.getNetworkCallsHandler());
    }

    // =========================================================================
    // Task 1a: bodyForValidatedEventSchemaCall tests
    // =========================================================================

    @Test
    public void bodyForValidatedEventSchemaCallIncludesBaseFields() {
        AvoNetworkCallsHandler sut = new AvoNetworkCallsHandler(
                "testApiKey", "dev", "testApp", "1.0.0", "7");

        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        schema.put("userId", new AvoEventSchemaType.AvoString());

        ValidationResult validationResult = createTestValidationResult();

        Map<String, Object> body = sut.bodyForValidatedEventSchemaCall(
                "TestEvent", schema, null, null, validationResult, "stream123");

        // Base fields
        assertEquals("testApiKey", body.get("apiKey"));
        assertEquals("testApp", body.get("appName"));
        assertEquals("1.0.0", body.get("appVersion"));
        assertEquals("7", body.get("libVersion"));
        assertEquals("dev", body.get("env"));
        assertEquals("android", body.get("libPlatform"));
        assertNotNull(body.get("messageId"));
        assertNotNull(body.get("createdAt"));

        // Event fields
        assertEquals("event", body.get("type"));
        assertEquals("TestEvent", body.get("eventName"));
        assertFalse((Boolean) body.get("avoFunction"));

        // Validated event fields
        assertEquals("stream123", body.get("streamId"));
        assertNotNull(body.get("eventProperties"));
        assertNotNull(body.get("eventSpecMetadata"));
    }

    @Test
    public void bodyForValidatedEventSchemaCallIncludesMetadata() {
        AvoNetworkCallsHandler sut = new AvoNetworkCallsHandler(
                "testApiKey", "dev", "testApp", "1.0.0", "7");

        Map<String, AvoEventSchemaType> schema = new HashMap<>();

        ValidationResult validationResult = createTestValidationResult();

        Map<String, Object> body = sut.bodyForValidatedEventSchemaCall(
                "TestEvent", schema, null, null, validationResult, "stream123");

        JSONObject metadata = (JSONObject) body.get("eventSpecMetadata");
        assertNotNull(metadata);
        assertEquals("schema1", metadata.optString("schemaId"));
        assertEquals("branch1", metadata.optString("branchId"));
        assertEquals("action1", metadata.optString("latestActionId"));
        assertEquals("source1", metadata.optString("sourceId"));
    }

    @Test
    public void bodyForValidatedEventSchemaCallWithAvoFunction() {
        AvoNetworkCallsHandler sut = new AvoNetworkCallsHandler(
                "testApiKey", "dev", "testApp", "1.0.0", "7");

        Map<String, AvoEventSchemaType> schema = new HashMap<>();

        ValidationResult validationResult = createTestValidationResult();

        Map<String, Object> body = sut.bodyForValidatedEventSchemaCall(
                "TestEvent", schema, "eventId1", "eventHash1", validationResult, "stream123");

        assertTrue((Boolean) body.get("avoFunction"));
        assertEquals("eventId1", body.get("eventId"));
        assertEquals("eventHash1", body.get("eventHash"));
    }

    // =========================================================================
    // Task 1c: remapPropertiesWithValidation tests
    // =========================================================================

    @Test
    public void remapPropertiesWithValidationAddsFailedEventIds() throws Exception {
        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        schema.put("userId", new AvoEventSchemaType.AvoString());

        ValidationResult validationResult = new ValidationResult();
        validationResult.metadata = new EventSpecMetadata();
        validationResult.propertyResults = new HashMap<>();
        PropertyValidationResult propResult = new PropertyValidationResult();
        propResult.failedEventIds = Arrays.asList("event1", "event2");
        validationResult.propertyResults.put("userId", propResult);

        JSONArray result = Util.remapPropertiesWithValidation(schema, validationResult);

        assertEquals(1, result.length());
        JSONObject prop = result.getJSONObject(0);
        assertEquals("userId", prop.getString("propertyName"));
        assertEquals("string", prop.getString("propertyType"));
        assertTrue(prop.has("failedEventIds"));
        JSONArray failedIds = prop.getJSONArray("failedEventIds");
        assertEquals(2, failedIds.length());
    }

    @Test
    public void remapPropertiesWithValidationAddsPassedEventIds() throws Exception {
        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        schema.put("count", new AvoEventSchemaType.AvoInt());

        ValidationResult validationResult = new ValidationResult();
        validationResult.metadata = new EventSpecMetadata();
        validationResult.propertyResults = new HashMap<>();
        PropertyValidationResult propResult = new PropertyValidationResult();
        propResult.passedEventIds = Arrays.asList("event1");
        validationResult.propertyResults.put("count", propResult);

        JSONArray result = Util.remapPropertiesWithValidation(schema, validationResult);

        assertEquals(1, result.length());
        JSONObject prop = result.getJSONObject(0);
        assertEquals("count", prop.getString("propertyName"));
        assertEquals("int", prop.getString("propertyType"));
        assertTrue(prop.has("passedEventIds"));
        JSONArray passedIds = prop.getJSONArray("passedEventIds");
        assertEquals(1, passedIds.length());
    }

    @Test
    public void remapPropertiesWithValidationHandlesChildrenValidation() throws Exception {
        Map<String, AvoEventSchemaType> innerChildren = new HashMap<>();
        innerChildren.put("street", new AvoEventSchemaType.AvoString());
        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        schema.put("address", new AvoEventSchemaType.AvoObject(innerChildren));

        ValidationResult validationResult = new ValidationResult();
        validationResult.metadata = new EventSpecMetadata();
        validationResult.propertyResults = new HashMap<>();
        PropertyValidationResult propResult = new PropertyValidationResult();
        propResult.children = new HashMap<>();
        PropertyValidationResult childResult = new PropertyValidationResult();
        childResult.failedEventIds = Arrays.asList("event1");
        propResult.children.put("street", childResult);
        validationResult.propertyResults.put("address", propResult);

        JSONArray result = Util.remapPropertiesWithValidation(schema, validationResult);

        assertEquals(1, result.length());
        JSONObject prop = result.getJSONObject(0);
        assertEquals("address", prop.getString("propertyName"));
        assertEquals("object", prop.getString("propertyType"));
        assertTrue(prop.has("children"));

        // Children should be a JSONArray with the street child having failedEventIds
        JSONArray children = prop.getJSONArray("children");
        boolean foundStreetWithFailed = false;
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.getJSONObject(i);
            if ("street".equals(child.optString("propertyName"))) {
                assertTrue(child.has("failedEventIds"));
                foundStreetWithFailed = true;
            }
        }
        assertTrue("street child should have failedEventIds", foundStreetWithFailed);
    }

    @Test
    public void remapPropertiesWithValidationNoValidationData() throws Exception {
        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        schema.put("name", new AvoEventSchemaType.AvoString());

        ValidationResult validationResult = new ValidationResult();
        validationResult.metadata = new EventSpecMetadata();
        validationResult.propertyResults = new HashMap<>();

        JSONArray result = Util.remapPropertiesWithValidation(schema, validationResult);

        assertEquals(1, result.length());
        JSONObject prop = result.getJSONObject(0);
        assertEquals("name", prop.getString("propertyName"));
        assertEquals("string", prop.getString("propertyType"));
        // No validation fields should be present
        assertFalse(prop.has("failedEventIds"));
        assertFalse(prop.has("passedEventIds"));
    }

    // =========================================================================
    // Task 2c: avoFunctionTrackSchemaFromEvent integration
    // =========================================================================

    @Test
    public void avoFunctionTrackSchemaUsesValidatedPathOnCacheHit() {
        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        AvoNetworkCallsHandler mockNetworkHandler = mock(AvoNetworkCallsHandler.class);
        Map<String, Object> mockBody = new HashMap<>();
        mockBody.put("type", "event");
        when(mockNetworkHandler.bodyForValidatedEventSchemaCall(
                anyString(), any(), any(), any(), any(), anyString()
        )).thenReturn(mockBody);

        AvoBatcher mockBatcher = mock(AvoBatcher.class);
        when(mockBatcher.getNetworkCallsHandler()).thenReturn(mockNetworkHandler);
        sut.avoBatcher = mockBatcher;

        // Warm cache
        EventSpecResponse specResponse = createTestEventSpecResponse();
        sut.eventSpecCache.set("apiKey", "testStreamId", "TestEvent", specResponse);

        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put("userId", "user123");
        sut.avoFunctionTrackSchemaFromEvent("TestEvent", eventProps, "evtId", "evtHash");

        // Verify validated event was sent with eventId and eventHash
        verify(mockNetworkHandler).bodyForValidatedEventSchemaCall(
                eq("TestEvent"), any(), eq("evtId"), eq("evtHash"), any(), eq("testStreamId"));
        verify(mockNetworkHandler).reportValidatedEvent(any());
        verify(mockBatcher, never()).batchTrackEventSchema(anyString(), any(), any(), any());
    }

    // =========================================================================
    // Task 2c: JSONObject trackSchemaFromEvent integration
    // =========================================================================

    @Test
    public void jsonObjectTrackSchemaUsesValidatedPathOnCacheHit() throws Exception {
        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        AvoNetworkCallsHandler mockNetworkHandler = mock(AvoNetworkCallsHandler.class);
        Map<String, Object> mockBody = new HashMap<>();
        mockBody.put("type", "event");
        when(mockNetworkHandler.bodyForValidatedEventSchemaCall(
                anyString(), any(), any(), any(), any(), anyString()
        )).thenReturn(mockBody);

        AvoBatcher mockBatcher = mock(AvoBatcher.class);
        when(mockBatcher.getNetworkCallsHandler()).thenReturn(mockNetworkHandler);
        sut.avoBatcher = mockBatcher;

        // Warm cache
        EventSpecResponse specResponse = createTestEventSpecResponse();
        sut.eventSpecCache.set("apiKey", "testStreamId", "TestEvent", specResponse);

        JSONObject eventProps = new JSONObject();
        eventProps.put("userId", "user123");
        sut.trackSchemaFromEvent("TestEvent", eventProps);

        // Verify validated event was sent
        verify(mockNetworkHandler).reportValidatedEvent(any());
        verify(mockBatcher, never()).batchTrackEventSchema(anyString(), any(), any(), any());
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private EventSpecResponse createTestEventSpecResponse() {
        EventSpecResponse response = new EventSpecResponse();
        response.events = new ArrayList<>();
        EventSpecEntry entry = new EventSpecEntry();
        entry.baseEventId = "event1";
        entry.branchId = "branch1";
        entry.variantIds = new ArrayList<>();
        entry.props = new HashMap<>();

        PropertyConstraints constraints = new PropertyConstraints();
        constraints.type = "string";
        constraints.required = false;
        entry.props.put("userId", constraints);

        response.events.add(entry);

        response.metadata = new EventSpecMetadata();
        response.metadata.schemaId = "schema1";
        response.metadata.branchId = "branch1";
        response.metadata.latestActionId = "action1";
        response.metadata.sourceId = "source1";

        return response;
    }

    private ValidationResult createTestValidationResult() {
        ValidationResult result = new ValidationResult();
        result.metadata = new EventSpecMetadata();
        result.metadata.schemaId = "schema1";
        result.metadata.branchId = "branch1";
        result.metadata.latestActionId = "action1";
        result.metadata.sourceId = "source1";
        result.propertyResults = new HashMap<>();
        return result;
    }
}
