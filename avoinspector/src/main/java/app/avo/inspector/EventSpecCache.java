package app.avo.inspector;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

class EventSpecCache {

    private final HashMap<String, EventSpecCacheEntry> cache;

    private static final long TTL_MS = 60_000;

    private static final int MAX_EVENT_COUNT = 50;

    private int globalEventCount = 0;

    private final boolean shouldLog;

    EventSpecCache(boolean shouldLog) {
        this.cache = new HashMap<>();
        this.shouldLog = shouldLog;
    }

    private String generateKey(String apiKey, String streamId, String eventName) {
        return apiKey + ":" + streamId + ":" + eventName;
    }

    synchronized EventSpecResponse get(String apiKey, String streamId, String eventName) {
        String key = generateKey(apiKey, streamId, eventName);
        EventSpecCacheEntry entry = cache.get(key);

        if (entry == null) {
            return null;
        }

        if (shouldEvict(entry)) {
            cache.remove(key);
            return null;
        }

        if (shouldLog) {
            Log.d("Avo Inspector", "Cache hit for key: " + key);
        }

        entry.lastAccessed = (double) System.currentTimeMillis();

        entry.eventCount++;
        globalEventCount++;

        if (globalEventCount >= MAX_EVENT_COUNT) {
            evictOldest();
            globalEventCount = 0;
        }

        return entry.spec;
    }

    synchronized void set(String apiKey, String streamId, String eventName, EventSpecResponse spec) {
        String key = generateKey(apiKey, streamId, eventName);

        double now = (double) System.currentTimeMillis();

        EventSpecCacheEntry entry = new EventSpecCacheEntry();
        entry.spec = spec;
        entry.timestamp = now;
        entry.lastAccessed = now;
        entry.eventCount = 0;

        cache.put(key, entry);
    }

    synchronized void clear() {
        cache.clear();
        globalEventCount = 0;
        if (shouldLog) {
            Log.d("Avo Inspector", "Cache cleared");
        }
    }

    synchronized int size() {
        return cache.size();
    }

    private boolean shouldEvict(EventSpecCacheEntry entry) {
        double age = (double) System.currentTimeMillis() - entry.timestamp;
        boolean ageExpired = age > TTL_MS;
        boolean countExpired = entry.eventCount >= MAX_EVENT_COUNT;
        return ageExpired || countExpired;
    }

    private void evictOldest() {
        if (cache.isEmpty()) {
            return;
        }

        String lruKey = null;
        double oldestAccessTime = Double.MAX_VALUE;

        for (Map.Entry<String, EventSpecCacheEntry> mapEntry : cache.entrySet()) {
            if (mapEntry.getValue().lastAccessed < oldestAccessTime) {
                oldestAccessTime = mapEntry.getValue().lastAccessed;
                lruKey = mapEntry.getKey();
            }
        }

        if (lruKey != null) {
            cache.remove(lruKey);
        }
    }
}
