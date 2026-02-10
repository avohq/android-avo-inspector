package app.avo.inspector;

import android.app.Application;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class FetchAndValidateTests {

    @Mock Application mockApplication;
    @Mock PackageManager mockPackageManager;
    @Mock PackageInfo mockPackageInfo;
    @Mock ApplicationInfo mockApplicationInfo;
    @Mock SharedPreferences mockSharedPrefs;
    @Mock SharedPreferences.Editor mockEditor;

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
    // HELPERS
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

    // =========================================================================
    // Guard clause isolation
    // =========================================================================

    // Note: nullEventPropertiesFallsBackToBatch is not testable through the public API because
    // AvoDeduplicator.shouldRegisterEvent crashes with null properties (ConcurrentHashMap
    // does not allow null keys/values). The null properties guard in fetchAndValidateAsync
    // is defensive code.

    @Test
    public void nullFetcherFallsBackToBatch() {
        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        AvoBatcher mockBatcher = mock(AvoBatcher.class);
        AvoNetworkCallsHandler mockNetworkHandler = mock(AvoNetworkCallsHandler.class);
        when(mockBatcher.getNetworkCallsHandler()).thenReturn(mockNetworkHandler);
        sut.avoBatcher = mockBatcher;

        // Set fetcher to null
        sut.eventSpecFetcher = null;

        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put("userId", "user123");
        sut.trackSchemaFromEvent("TestEvent", (Map<String, ?>) eventProps);

        verify(mockBatcher).batchTrackEventSchema(eq("TestEvent"), any(), eq(null), eq(null));
        verify(mockNetworkHandler, never()).reportValidatedEvent(any());
    }

    @Test
    public void nullCacheFallsBackToBatch() {
        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        AvoBatcher mockBatcher = mock(AvoBatcher.class);
        AvoNetworkCallsHandler mockNetworkHandler = mock(AvoNetworkCallsHandler.class);
        when(mockBatcher.getNetworkCallsHandler()).thenReturn(mockNetworkHandler);
        sut.avoBatcher = mockBatcher;

        // Set cache to null
        sut.eventSpecCache = null;

        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put("userId", "user123");
        sut.trackSchemaFromEvent("TestEvent", (Map<String, ?>) eventProps);

        verify(mockBatcher).batchTrackEventSchema(eq("TestEvent"), any(), eq(null), eq(null));
        verify(mockNetworkHandler, never()).reportValidatedEvent(any());
    }

    // =========================================================================
    // Cache hit with null spec (empty cached response)
    // =========================================================================

    @Test
    public void cachedNullSpecFallsBackToBatch() {
        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        AvoBatcher mockBatcher = mock(AvoBatcher.class);
        AvoNetworkCallsHandler mockNetworkHandler = mock(AvoNetworkCallsHandler.class);
        when(mockBatcher.getNetworkCallsHandler()).thenReturn(mockNetworkHandler);
        sut.avoBatcher = mockBatcher;

        // Cache a null spec (empty response)
        sut.eventSpecCache.set("apiKey", "testStreamId", "TestEvent", null);

        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put("userId", "user123");
        sut.trackSchemaFromEvent("TestEvent", (Map<String, ?>) eventProps);

        verify(mockBatcher).batchTrackEventSchema(eq("TestEvent"), any(), eq(null), eq(null));
        verify(mockNetworkHandler, never()).reportValidatedEvent(any());
    }

    // =========================================================================
    // Async fetch success
    // =========================================================================

    @Test
    public void cacheMissFetchSuccessSendsValidatedEvent() {
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

        // Mock fetcher to return valid spec synchronously via callback
        EventSpecResponse specResponse = createTestEventSpecResponse();
        sut.eventSpecFetcher = mock(AvoEventSpecFetcher.class);
        doAnswer(invocation -> {
            EventSpecFetchCallback callback = invocation.getArgument(1);
            callback.onResult(specResponse);
            return null;
        }).when(sut.eventSpecFetcher).fetch(any(), any());

        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put("userId", "user123");
        sut.trackSchemaFromEvent("TestEvent", (Map<String, ?>) eventProps);

        verify(mockNetworkHandler).reportValidatedEvent(any());
        verify(mockBatcher, never()).batchTrackEventSchema(anyString(), any(), any(), any());
    }

    // =========================================================================
    // Exception during validation (cache-hit path)
    // =========================================================================

    @Test
    public void validationExceptionFallsBackToBatch() {
        // Use Staging so Util.handleException doesn't re-throw
        AvoInspector sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Staging);

        AvoBatcher mockBatcher = mock(AvoBatcher.class);
        AvoNetworkCallsHandler mockNetworkHandler = mock(AvoNetworkCallsHandler.class);
        when(mockBatcher.getNetworkCallsHandler()).thenReturn(mockNetworkHandler);
        sut.avoBatcher = mockBatcher;

        // Create a spec that will cause NPE during validation (null variantIds)
        EventSpecResponse badSpec = new EventSpecResponse();
        badSpec.events = new ArrayList<>();
        EventSpecEntry entry = new EventSpecEntry();
        entry.baseEventId = "event1";
        entry.branchId = "branch1";
        entry.variantIds = null; // Will cause NPE in collectAllEventIds
        entry.props = new HashMap<>();
        badSpec.events.add(entry);

        badSpec.metadata = new EventSpecMetadata();
        badSpec.metadata.schemaId = "s1";
        badSpec.metadata.branchId = "b1";
        badSpec.metadata.latestActionId = "a1";

        sut.eventSpecCache.set("apiKey", "testStreamId", "TestEvent", badSpec);

        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put("userId", "user123");
        sut.trackSchemaFromEvent("TestEvent", (Map<String, ?>) eventProps);

        // Should fall back to batch due to exception
        verify(mockBatcher).batchTrackEventSchema(eq("TestEvent"), any(), eq(null), eq(null));
        verify(mockNetworkHandler, never()).reportValidatedEvent(any());
    }

    // =========================================================================
    // handleBranchChangeAndCache gaps
    // =========================================================================

    @Test
    public void nullMetadataCachesWithoutBranchLogic() {
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

        // Create spec with null metadata
        EventSpecResponse specResponse = new EventSpecResponse();
        specResponse.events = new ArrayList<>();
        EventSpecEntry entry = new EventSpecEntry();
        entry.baseEventId = "event1";
        entry.branchId = "branch1";
        entry.variantIds = new ArrayList<>();
        entry.props = new HashMap<>();
        specResponse.events.add(entry);
        specResponse.metadata = null;

        sut.eventSpecCache.set("apiKey", "testStreamId", "TestEvent", specResponse);

        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put("userId", "user123");
        sut.trackSchemaFromEvent("TestEvent", (Map<String, ?>) eventProps);

        // Should not crash and should send validated event
        verify(mockNetworkHandler).reportValidatedEvent(any());
        // currentBranchId should remain null
        assertNull(sut.currentBranchId);
    }

    @Test
    public void sameBranchDoesNotClearCache() {
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

        // Create two specs with same branchId
        EventSpecResponse spec1 = createTestEventSpecResponse();
        spec1.metadata.branchId = "sameBranch";
        EventSpecResponse spec2 = createTestEventSpecResponse();
        spec2.metadata.branchId = "sameBranch";

        sut.eventSpecCache.set("apiKey", "testStreamId", "Event1", spec1);
        sut.eventSpecCache.set("apiKey", "testStreamId", "Event2", spec2);

        // Track first event
        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put("userId", "user123");
        sut.trackSchemaFromEvent("Event1", (Map<String, ?>) eventProps);

        assertEquals("sameBranch", sut.currentBranchId);

        // Track second event with same branch
        sut.trackSchemaFromEvent("Event2", (Map<String, ?>) eventProps);

        // Cache should NOT have been cleared — both entries remain
        assertEquals("sameBranch", sut.currentBranchId);
        assertTrue(sut.eventSpecCache.contains("apiKey", "testStreamId", "Event1"));
        assertTrue(sut.eventSpecCache.contains("apiKey", "testStreamId", "Event2"));
    }

    @Test
    public void firstEventSetsBranchIdWithoutClearing() {
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

        // Verify initial state
        assertNull(sut.currentBranchId);

        EventSpecResponse specResponse = createTestEventSpecResponse();
        specResponse.metadata.branchId = "initialBranch";
        sut.eventSpecCache.set("apiKey", "testStreamId", "TestEvent", specResponse);

        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put("userId", "user123");
        sut.trackSchemaFromEvent("TestEvent", (Map<String, ?>) eventProps);

        // Branch should be set
        assertEquals("initialBranch", sut.currentBranchId);
        // Cache should still contain the entry (not cleared)
        assertTrue(sut.eventSpecCache.contains("apiKey", "testStreamId", "TestEvent"));
    }
}
