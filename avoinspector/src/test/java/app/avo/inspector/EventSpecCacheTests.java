package app.avo.inspector;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class EventSpecCacheTests {

    private EventSpecCache cache;

    @Before
    public void setUp() {
        cache = new EventSpecCache(false);
    }

    // --- Helper to create a minimal EventSpecResponse ---

    private EventSpecResponse createTestSpec(String schemaId, String branchId) {
        EventSpecResponse spec = new EventSpecResponse();
        spec.events = new ArrayList<>();

        EventSpecEntry entry = new EventSpecEntry();
        entry.branchId = branchId;
        entry.baseEventId = "evt_1";
        entry.variantIds = new ArrayList<>();
        entry.props = new HashMap<>();
        spec.events.add(entry);

        EventSpecMetadata metadata = new EventSpecMetadata();
        metadata.schemaId = schemaId;
        metadata.branchId = branchId;
        metadata.latestActionId = "action_1";
        metadata.sourceId = "source_1";
        spec.metadata = metadata;

        return spec;
    }

    // --- Test: Cache miss returns null ---

    @Test
    public void cacheMissReturnsNull() {
        EventSpecResponse result = cache.get("apiKey", "stream1", "nonExistentEvent");
        assertNull(result);
    }

    // --- Test: Cache hit returns stored spec ---

    @Test
    public void cacheHitReturnsStoredSpec() {
        EventSpecResponse spec = createTestSpec("schema_1", "branch_1");
        cache.set("apiKey", "stream1", "TestEvent", spec);

        EventSpecResponse result = cache.get("apiKey", "stream1", "TestEvent");

        assertNotNull(result);
        assertEquals("schema_1", result.metadata.schemaId);
        assertEquals("branch_1", result.metadata.branchId);
        assertEquals(1, result.events.size());
    }

    // --- Test: Different keys are independent ---

    @Test
    public void differentKeysAreIndependent() {
        EventSpecResponse spec1 = createTestSpec("schema_1", "branch_1");
        EventSpecResponse spec2 = createTestSpec("schema_2", "branch_2");

        cache.set("apiKey", "stream1", "Event1", spec1);
        cache.set("apiKey", "stream1", "Event2", spec2);

        EventSpecResponse result1 = cache.get("apiKey", "stream1", "Event1");
        EventSpecResponse result2 = cache.get("apiKey", "stream1", "Event2");

        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals("schema_1", result1.metadata.schemaId);
        assertEquals("schema_2", result2.metadata.schemaId);
    }

    // --- Test: Set overwrites existing entry ---

    @Test
    public void setOverwritesExistingEntry() {
        EventSpecResponse spec1 = createTestSpec("schema_old", "branch_old");
        EventSpecResponse spec2 = createTestSpec("schema_new", "branch_new");

        cache.set("apiKey", "stream1", "TestEvent", spec1);
        cache.set("apiKey", "stream1", "TestEvent", spec2);

        EventSpecResponse result = cache.get("apiKey", "stream1", "TestEvent");

        assertNotNull(result);
        assertEquals("schema_new", result.metadata.schemaId);
    }

    // --- Test: Clear empties the cache ---

    @Test
    public void clearEmptiesTheCache() {
        cache.set("apiKey", "stream1", "Event1", createTestSpec("s1", "b1"));
        cache.set("apiKey", "stream1", "Event2", createTestSpec("s2", "b2"));
        assertEquals(2, cache.size());

        cache.clear();

        assertEquals(0, cache.size());
        assertNull(cache.get("apiKey", "stream1", "Event1"));
        assertNull(cache.get("apiKey", "stream1", "Event2"));
    }

    // --- Test: Size returns correct count ---

    @Test
    public void sizeReturnsCorrectCount() {
        assertEquals(0, cache.size());

        cache.set("apiKey", "stream1", "Event1", createTestSpec("s1", "b1"));
        assertEquals(1, cache.size());

        cache.set("apiKey", "stream1", "Event2", createTestSpec("s2", "b2"));
        assertEquals(2, cache.size());

        cache.set("apiKey", "stream2", "Event1", createTestSpec("s3", "b3"));
        assertEquals(3, cache.size());
    }

    // --- Test: TTL expiry returns null ---
    // We test this by creating a cache entry with an old timestamp
    // using reflection to set the timestamp field on the entry

    @Test
    public void ttlExpiryReturnsNull() throws Exception {
        EventSpecResponse spec = createTestSpec("schema_1", "branch_1");
        cache.set("apiKey", "stream1", "TestEvent", spec);

        // Use reflection to access the internal cache map and set an old timestamp
        java.lang.reflect.Field cacheField = EventSpecCache.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        HashMap<String, EventSpecCacheEntry> internalCache =
                (HashMap<String, EventSpecCacheEntry>) cacheField.get(cache);

        EventSpecCacheEntry entry = internalCache.get("apiKey:stream1:TestEvent");
        assertNotNull("Cache entry should exist", entry);

        // Set timestamp to more than 60 seconds ago
        entry.timestamp = (double) (System.currentTimeMillis() - 61_000);

        EventSpecResponse result = cache.get("apiKey", "stream1", "TestEvent");
        assertNull("Expired entry should return null", result);
    }

    // --- Test: Event count expiry returns null ---

    @Test
    public void eventCountExpiryReturnsNull() throws Exception {
        EventSpecResponse spec = createTestSpec("schema_1", "branch_1");
        cache.set("apiKey", "stream1", "TestEvent", spec);

        // Use reflection to set eventCount to MAX_EVENT_COUNT
        java.lang.reflect.Field cacheField = EventSpecCache.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        HashMap<String, EventSpecCacheEntry> internalCache =
                (HashMap<String, EventSpecCacheEntry>) cacheField.get(cache);

        EventSpecCacheEntry entry = internalCache.get("apiKey:stream1:TestEvent");
        assertNotNull("Cache entry should exist", entry);

        // Set eventCount to 50 (MAX_EVENT_COUNT), which should trigger eviction
        entry.eventCount = 50;

        EventSpecResponse result = cache.get("apiKey", "stream1", "TestEvent");
        assertNull("Entry with eventCount >= MAX_EVENT_COUNT should return null", result);
    }

    // --- Test: LRU eviction removes oldest entry when globalEventCount hits MAX_EVENT_COUNT ---

    @Test
    public void lruEvictionRemovesOldestEntry() throws Exception {
        // Add two entries with different lastAccessed times
        EventSpecResponse spec1 = createTestSpec("schema_old", "branch_old");
        EventSpecResponse spec2 = createTestSpec("schema_new", "branch_new");

        cache.set("apiKey", "stream1", "OldEvent", spec1);
        cache.set("apiKey", "stream1", "NewEvent", spec2);

        // Use reflection to make OldEvent have an older lastAccessed
        java.lang.reflect.Field cacheField = EventSpecCache.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        HashMap<String, EventSpecCacheEntry> internalCache =
                (HashMap<String, EventSpecCacheEntry>) cacheField.get(cache);

        EventSpecCacheEntry oldEntry = internalCache.get("apiKey:stream1:OldEvent");
        assertNotNull(oldEntry);
        oldEntry.lastAccessed = 1000.0; // Very old lastAccessed

        EventSpecCacheEntry newEntry = internalCache.get("apiKey:stream1:NewEvent");
        assertNotNull(newEntry);
        newEntry.lastAccessed = (double) System.currentTimeMillis(); // Recent lastAccessed

        // Set globalEventCount to just below threshold so the next cache hit triggers eviction
        java.lang.reflect.Field globalCountField = EventSpecCache.class.getDeclaredField("globalEventCount");
        globalCountField.setAccessible(true);
        globalCountField.setInt(cache, 49);

        // Access NewEvent - this should trigger LRU eviction (globalEventCount becomes 50)
        EventSpecResponse result = cache.get("apiKey", "stream1", "NewEvent");
        assertNotNull("NewEvent should still be accessible", result);
        assertEquals("schema_new", result.metadata.schemaId);

        // OldEvent should have been evicted (it had the oldest lastAccessed)
        // But we need to verify the size decreased
        // Note: After eviction, the cache should have 1 entry (NewEvent)
        assertEquals("Cache should have 1 entry after eviction", 1, cache.size());

        // Verify OldEvent was the one evicted
        EventSpecResponse oldResult = cache.get("apiKey", "stream1", "OldEvent");
        assertNull("OldEvent should have been evicted", oldResult);
    }

    // --- Test: Cache hit increments eventCount and globalEventCount ---

    @Test
    public void cacheHitIncrementsCounters() throws Exception {
        EventSpecResponse spec = createTestSpec("schema_1", "branch_1");
        cache.set("apiKey", "stream1", "TestEvent", spec);

        // First hit
        cache.get("apiKey", "stream1", "TestEvent");

        // Use reflection to check counters
        java.lang.reflect.Field cacheField = EventSpecCache.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        HashMap<String, EventSpecCacheEntry> internalCache =
                (HashMap<String, EventSpecCacheEntry>) cacheField.get(cache);

        EventSpecCacheEntry entry = internalCache.get("apiKey:stream1:TestEvent");
        assertNotNull(entry);
        assertEquals("eventCount should be 1 after one hit", 1.0, entry.eventCount, 0.01);

        java.lang.reflect.Field globalCountField = EventSpecCache.class.getDeclaredField("globalEventCount");
        globalCountField.setAccessible(true);
        int globalCount = globalCountField.getInt(cache);
        assertEquals("globalEventCount should be 1 after one hit", 1, globalCount);

        // Second hit
        cache.get("apiKey", "stream1", "TestEvent");

        entry = internalCache.get("apiKey:stream1:TestEvent");
        assertNotNull(entry);
        assertEquals("eventCount should be 2 after two hits", 2.0, entry.eventCount, 0.01);

        globalCount = globalCountField.getInt(cache);
        assertEquals("globalEventCount should be 2 after two hits", 2, globalCount);
    }

    // --- Test: Cache key format includes apiKey, streamId, eventName ---

    @Test
    public void cacheKeyIncludesAllComponents() {
        EventSpecResponse spec = createTestSpec("schema_1", "branch_1");

        cache.set("key1", "stream1", "Event", spec);

        // Same event name but different apiKey should be a miss
        assertNull(cache.get("key2", "stream1", "Event"));

        // Same event name but different streamId should be a miss
        assertNull(cache.get("key1", "stream2", "Event"));

        // Exact match should be a hit
        assertNotNull(cache.get("key1", "stream1", "Event"));
    }

    // --- Test: Clear with logging enabled ---

    @Test
    public void clearWithLoggingEnabled() {
        EventSpecCache loggingCache = new EventSpecCache(true);
        loggingCache.set("apiKey", "stream1", "Event1", createTestSpec("s1", "b1"));

        // Should not throw even with logging enabled (Log returns defaults in unit tests)
        loggingCache.clear();
        assertEquals(0, loggingCache.size());
    }

    // --- Test: globalEventCount resets after eviction ---

    @Test
    public void globalEventCountResetsAfterEviction() throws Exception {
        EventSpecResponse spec = createTestSpec("schema_1", "branch_1");
        cache.set("apiKey", "stream1", "TestEvent", spec);

        // Set globalEventCount to 49 so next hit triggers eviction + reset
        java.lang.reflect.Field globalCountField = EventSpecCache.class.getDeclaredField("globalEventCount");
        globalCountField.setAccessible(true);
        globalCountField.setInt(cache, 49);

        // This hit should trigger eviction and reset globalEventCount to 0
        cache.get("apiKey", "stream1", "TestEvent");

        int globalCount = globalCountField.getInt(cache);
        assertEquals("globalEventCount should be reset to 0 after eviction", 0, globalCount);
    }
}
