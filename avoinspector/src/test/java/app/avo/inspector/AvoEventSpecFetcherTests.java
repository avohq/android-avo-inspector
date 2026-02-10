package app.avo.inspector;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class AvoEventSpecFetcherTests {

    @Before
    public void setUp() {
        AvoInspector.enableLogging(false);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private FetchEventSpecParams createParams() {
        return createParams("testApiKey", "testStreamId", "TestEvent");
    }

    private FetchEventSpecParams createParams(String apiKey, String streamId, String eventName) {
        FetchEventSpecParams params = new FetchEventSpecParams();
        params.apiKey = apiKey;
        params.streamId = streamId;
        params.eventName = eventName;
        return params;
    }

    private EventSpecResponseWire createValidWireResponse() {
        EventSpecResponseWire wire = new EventSpecResponseWire();
        wire.events = new ArrayList<>();

        EventSpecEntryWire entry = new EventSpecEntryWire();
        entry.b = "branch1";
        entry.id = "event1";
        entry.vids = new ArrayList<>(Arrays.asList("v1", "v2"));
        entry.p = new HashMap<>();

        PropertyConstraintsWire constraint = new PropertyConstraintsWire();
        constraint.t = "string";
        constraint.r = false;
        entry.p.put("userId", constraint);

        wire.events.add(entry);

        wire.metadata = new EventSpecMetadata();
        wire.metadata.schemaId = "schema1";
        wire.metadata.branchId = "branch1";
        wire.metadata.latestActionId = "action1";
        wire.metadata.sourceId = "source1";

        return wire;
    }

    // =========================================================================
    // Constructor & environment filtering
    // =========================================================================

    @Test
    public void fetchInProdEnvironmentReturnsNull() {
        AtomicInteger clientCallCount = new AtomicInteger(0);
        EventSpecRequestClient mockClient = (url, timeout) -> {
            clientCallCount.incrementAndGet();
            return createValidWireResponse();
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "prod", "https://api.avo.app", mockClient);

        // Use a non-null sentinel to distinguish "callback invoked with null" from "callback not invoked"
        AtomicReference<EventSpecResponse> result = new AtomicReference<>(new EventSpecResponse());
        fetcher.fetch(createParams(), response -> result.set(response));

        // Prod delivers null synchronously without calling client
        assertNull(result.get());
        assertEquals(0, clientCallCount.get());
    }

    @Test
    public void fetchInStagingCallsClient() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger clientCallCount = new AtomicInteger(0);
        EventSpecRequestClient mockClient = (url, timeout) -> {
            clientCallCount.incrementAndGet();
            return createValidWireResponse();
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "staging", "https://api.avo.app", mockClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>();
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, clientCallCount.get());
        assertNotNull(result.get());
    }

    @Test
    public void fetchInDevCallsClient() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger clientCallCount = new AtomicInteger(0);
        EventSpecRequestClient mockClient = (url, timeout) -> {
            clientCallCount.incrementAndGet();
            return createValidWireResponse();
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>();
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, clientCallCount.get());
        assertNotNull(result.get());
    }

    // =========================================================================
    // Request deduplication
    // =========================================================================

    @Test
    public void duplicateRequestsShareSingleFetch() throws Exception {
        AtomicInteger clientCallCount = new AtomicInteger(0);
        CountDownLatch clientStarted = new CountDownLatch(1);
        CountDownLatch clientRelease = new CountDownLatch(1);
        CountDownLatch resultLatch = new CountDownLatch(2);

        EventSpecRequestClient mockClient = (url, timeout) -> {
            clientCallCount.incrementAndGet();
            clientStarted.countDown();
            clientRelease.await();
            return createValidWireResponse();
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        AtomicReference<EventSpecResponse> result1 = new AtomicReference<>();
        AtomicReference<EventSpecResponse> result2 = new AtomicReference<>();

        FetchEventSpecParams params1 = createParams();
        fetcher.fetch(params1, response -> {
            result1.set(response);
            resultLatch.countDown();
        });

        // Wait for the client to actually start blocking
        assertTrue(clientStarted.await(5, TimeUnit.SECONDS));

        // Second fetch with same key — should deduplicate
        FetchEventSpecParams params2 = createParams();
        fetcher.fetch(params2, response -> {
            result2.set(response);
            resultLatch.countDown();
        });

        // Release the client
        clientRelease.countDown();

        assertTrue(resultLatch.await(5, TimeUnit.SECONDS));
        assertEquals(1, clientCallCount.get());
        assertNotNull(result1.get());
        assertNotNull(result2.get());
    }

    @Test
    public void differentRequestKeysFetchIndependently() throws Exception {
        AtomicInteger clientCallCount = new AtomicInteger(0);
        CountDownLatch resultLatch = new CountDownLatch(2);

        EventSpecRequestClient mockClient = (url, timeout) -> {
            clientCallCount.incrementAndGet();
            return createValidWireResponse();
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        FetchEventSpecParams params1 = createParams("key", "stream", "Event1");
        FetchEventSpecParams params2 = createParams("key", "stream", "Event2");

        fetcher.fetch(params1, response -> resultLatch.countDown());
        fetcher.fetch(params2, response -> resultLatch.countDown());

        assertTrue(resultLatch.await(5, TimeUnit.SECONDS));
        assertEquals(2, clientCallCount.get());
    }

    // =========================================================================
    // Response parsing (happy path)
    // =========================================================================

    @Test
    public void validResponseParsedCorrectly() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        EventSpecRequestClient mockClient = (url, timeout) -> createValidWireResponse();

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>();
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        EventSpecResponse response = result.get();
        assertNotNull(response);
        assertNotNull(response.events);
        assertEquals(1, response.events.size());

        EventSpecEntry entry = response.events.get(0);
        assertEquals("branch1", entry.branchId);
        assertEquals("event1", entry.baseEventId);
        assertEquals(2, entry.variantIds.size());
        assertTrue(entry.variantIds.contains("v1"));
        assertTrue(entry.variantIds.contains("v2"));
        assertNotNull(entry.props.get("userId"));
        assertEquals("string", entry.props.get("userId").type);

        assertEquals("schema1", response.metadata.schemaId);
        assertEquals("branch1", response.metadata.branchId);
        assertEquals("action1", response.metadata.latestActionId);
        assertEquals("source1", response.metadata.sourceId);
    }

    @Test
    public void responseWithChildrenParsedRecursively() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        EventSpecRequestClient mockClient = (url, timeout) -> {
            EventSpecResponseWire wire = new EventSpecResponseWire();
            wire.events = new ArrayList<>();

            EventSpecEntryWire entry = new EventSpecEntryWire();
            entry.b = "branch1";
            entry.id = "event1";
            entry.vids = new ArrayList<>();
            entry.p = new HashMap<>();

            PropertyConstraintsWire parent = new PropertyConstraintsWire();
            parent.t = "object";
            parent.children = new HashMap<>();
            PropertyConstraintsWire child = new PropertyConstraintsWire();
            child.t = "string";
            child.r = true;
            parent.children.put("street", child);

            entry.p.put("address", parent);
            wire.events.add(entry);

            wire.metadata = new EventSpecMetadata();
            wire.metadata.schemaId = "s1";
            wire.metadata.branchId = "b1";
            wire.metadata.latestActionId = "a1";

            return wire;
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>();
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        PropertyConstraints address = result.get().events.get(0).props.get("address");
        assertNotNull(address);
        assertEquals("object", address.type);
        assertNotNull(address.children);

        PropertyConstraints street = address.children.get("street");
        assertNotNull(street);
        assertEquals("string", street.type);
        assertTrue(street.required);
    }

    @Test
    public void responseWithAllConstraintTypesParsed() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        EventSpecRequestClient mockClient = (url, timeout) -> {
            EventSpecResponseWire wire = new EventSpecResponseWire();
            wire.events = new ArrayList<>();

            EventSpecEntryWire entry = new EventSpecEntryWire();
            entry.b = "branch1";
            entry.id = "event1";
            entry.vids = new ArrayList<>();
            entry.p = new HashMap<>();

            PropertyConstraintsWire constraint = new PropertyConstraintsWire();
            constraint.t = "string";
            constraint.r = true;
            constraint.l = true;
            constraint.p = new HashMap<>();
            constraint.p.put("pinned", Arrays.asList("e1"));
            constraint.v = new HashMap<>();
            constraint.v.put("[\"a\",\"b\"]", Arrays.asList("e1"));
            constraint.rx = new HashMap<>();
            constraint.rx.put("^[A-Z]+$", Arrays.asList("e1"));
            constraint.minmax = new HashMap<>();
            constraint.minmax.put("0,100", Arrays.asList("e1"));

            entry.p.put("field", constraint);
            wire.events.add(entry);

            wire.metadata = new EventSpecMetadata();
            wire.metadata.schemaId = "s1";
            wire.metadata.branchId = "b1";
            wire.metadata.latestActionId = "a1";

            return wire;
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>();
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        PropertyConstraints field = result.get().events.get(0).props.get("field");
        assertNotNull(field);
        assertTrue(field.required);
        assertTrue(field.isList);
        assertNotNull(field.pinnedValues);
        assertEquals(1, field.pinnedValues.get("pinned").size());
        assertNotNull(field.allowedValues);
        assertNotNull(field.regexPatterns);
        assertNotNull(field.minMaxRanges);
    }

    // =========================================================================
    // Response validation (hasExpectedShape)
    // =========================================================================

    @Test
    public void nullResponseReturnsNull() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        EventSpecRequestClient mockClient = (url, timeout) -> null;

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>(new EventSpecResponse());
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(result.get());
    }

    @Test
    public void responseWithNullEventsReturnsNull() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        EventSpecRequestClient mockClient = (url, timeout) -> {
            EventSpecResponseWire wire = new EventSpecResponseWire();
            wire.events = null;
            wire.metadata = new EventSpecMetadata();
            wire.metadata.schemaId = "s1";
            wire.metadata.branchId = "b1";
            wire.metadata.latestActionId = "a1";
            return wire;
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>(new EventSpecResponse());
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(result.get());
    }

    @Test
    public void responseWithNullMetadataReturnsNull() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        EventSpecRequestClient mockClient = (url, timeout) -> {
            EventSpecResponseWire wire = new EventSpecResponseWire();
            wire.events = new ArrayList<>();
            wire.metadata = null;
            return wire;
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>(new EventSpecResponse());
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(result.get());
    }

    @Test
    public void responseWithNullSchemaIdReturnsNull() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        EventSpecRequestClient mockClient = (url, timeout) -> {
            EventSpecResponseWire wire = new EventSpecResponseWire();
            wire.events = new ArrayList<>();
            wire.metadata = new EventSpecMetadata();
            wire.metadata.schemaId = null;
            wire.metadata.branchId = "b1";
            wire.metadata.latestActionId = "a1";
            return wire;
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>(new EventSpecResponse());
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(result.get());
    }

    @Test
    public void responseWithNullBranchIdReturnsNull() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        EventSpecRequestClient mockClient = (url, timeout) -> {
            EventSpecResponseWire wire = new EventSpecResponseWire();
            wire.events = new ArrayList<>();
            wire.metadata = new EventSpecMetadata();
            wire.metadata.schemaId = "s1";
            wire.metadata.branchId = null;
            wire.metadata.latestActionId = "a1";
            return wire;
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>(new EventSpecResponse());
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(result.get());
    }

    @Test
    public void responseWithNullLatestActionIdReturnsNull() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        EventSpecRequestClient mockClient = (url, timeout) -> {
            EventSpecResponseWire wire = new EventSpecResponseWire();
            wire.events = new ArrayList<>();
            wire.metadata = new EventSpecMetadata();
            wire.metadata.schemaId = "s1";
            wire.metadata.branchId = "b1";
            wire.metadata.latestActionId = null;
            return wire;
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>(new EventSpecResponse());
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(result.get());
    }

    @Test
    public void validShapeWithOptionalSourceIdNull() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        EventSpecRequestClient mockClient = (url, timeout) -> {
            EventSpecResponseWire wire = new EventSpecResponseWire();
            wire.events = new ArrayList<>();
            wire.metadata = new EventSpecMetadata();
            wire.metadata.schemaId = "s1";
            wire.metadata.branchId = "b1";
            wire.metadata.latestActionId = "a1";
            wire.metadata.sourceId = null;
            return wire;
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>();
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertNull(result.get().metadata.sourceId);
    }

    // =========================================================================
    // Error handling
    // =========================================================================

    @Test
    public void clientThrowsExceptionReturnsNull() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        EventSpecRequestClient mockClient = (url, timeout) -> {
            throw new RuntimeException("Network error");
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>(new EventSpecResponse());
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(result.get());
    }

    // =========================================================================
    // URL building
    // =========================================================================

    @Test
    public void buildUrlEncodesSpecialCharacters() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> capturedUrl = new AtomicReference<>();

        EventSpecRequestClient mockClient = (url, timeout) -> {
            capturedUrl.set(url);
            return createValidWireResponse();
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, "dev", "https://api.avo.app", mockClient);

        FetchEventSpecParams params = createParams("my key", "stream id", "Event Name&special=true");
        fetcher.fetch(params, response -> latch.countDown());

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        String url = capturedUrl.get();
        assertNotNull(url);
        assertTrue("URL should contain encoded apiKey", url.contains("apiKey=my+key"));
        assertTrue("URL should contain encoded streamId", url.contains("streamId=stream+id"));
        assertTrue("URL should contain encoded eventName with & and =",
                url.contains("eventName=Event+Name%26special%3Dtrue"));
    }

    // =========================================================================
    // Wall-clock timeout
    // =========================================================================

    @Test
    public void hangingRequestTimesOutWithinWallTimeout() throws Exception {
        int wallTimeout = 500; // 500ms wall timeout
        CountDownLatch latch = new CountDownLatch(1);

        EventSpecRequestClient hangingClient = (url, timeout) -> {
            // Simulate DNS hang — block until interrupted
            Thread.sleep(60_000);
            return createValidWireResponse();
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, wallTimeout, "dev", "https://api.avo.app", hangingClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>(new EventSpecResponse());
        long start = System.currentTimeMillis();
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue("Callback should fire within wall timeout", latch.await(3, TimeUnit.SECONDS));
        long elapsed = System.currentTimeMillis() - start;
        assertNull("Result should be null on timeout", result.get());
        assertTrue("Should complete within ~wallTimeout, not 60s. Elapsed: " + elapsed + "ms",
                elapsed < 5000);
    }

    @Test
    public void deduplicatedCallbacksBothReceiveNullOnTimeout() throws Exception {
        int wallTimeout = 500;
        CountDownLatch clientStarted = new CountDownLatch(1);
        CountDownLatch resultLatch = new CountDownLatch(2);

        EventSpecRequestClient hangingClient = (url, timeout) -> {
            clientStarted.countDown();
            Thread.sleep(60_000);
            return createValidWireResponse();
        };

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, wallTimeout, "dev", "https://api.avo.app", hangingClient);

        AtomicReference<EventSpecResponse> result1 = new AtomicReference<>(new EventSpecResponse());
        AtomicReference<EventSpecResponse> result2 = new AtomicReference<>(new EventSpecResponse());

        fetcher.fetch(createParams(), response -> {
            result1.set(response);
            resultLatch.countDown();
        });

        assertTrue(clientStarted.await(3, TimeUnit.SECONDS));

        fetcher.fetch(createParams(), response -> {
            result2.set(response);
            resultLatch.countDown();
        });

        assertTrue("Both callbacks should fire", resultLatch.await(5, TimeUnit.SECONDS));
        assertNull("First callback should receive null", result1.get());
        assertNull("Second callback should receive null", result2.get());
    }

    @Test
    public void normalRequestCompletesWithinWallTimeout() throws Exception {
        int wallTimeout = 5000;
        CountDownLatch latch = new CountDownLatch(1);

        EventSpecRequestClient fastClient = (url, timeout) -> createValidWireResponse();

        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(5000, wallTimeout, "dev", "https://api.avo.app", fastClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>();
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull("Fast request should succeed", result.get());
    }

    @Test
    public void backwardCompatibleConstructorDefaultsWallTimeout() throws Exception {
        // The 4-arg constructor (without wallTimeout) should default to timeout * 2
        int wallTimeout = 200; // If default were used (5000*2=10000), this would pass easily
        CountDownLatch latch = new CountDownLatch(1);

        EventSpecRequestClient hangingClient = (url, timeout) -> {
            Thread.sleep(60_000);
            return createValidWireResponse();
        };

        // Use the backward-compatible 4-arg constructor with timeout=100
        // wallTimeout should default to 200 (100*2)
        AvoEventSpecFetcher fetcher = new AvoEventSpecFetcher(100, "dev", "https://api.avo.app", hangingClient);

        AtomicReference<EventSpecResponse> result = new AtomicReference<>(new EventSpecResponse());
        long start = System.currentTimeMillis();
        fetcher.fetch(createParams(), response -> {
            result.set(response);
            latch.countDown();
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        long elapsed = System.currentTimeMillis() - start;
        assertNull("Should timeout and return null", result.get());
        assertTrue("Should complete near default wallTimeout (200ms), elapsed: " + elapsed + "ms",
                elapsed < 3000);
    }
}
