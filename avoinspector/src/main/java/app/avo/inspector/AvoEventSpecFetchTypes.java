package app.avo.inspector;

import java.util.*;
import java.util.concurrent.*;

class PropertyConstraintsWire {
    public String t;
    public boolean r;
    public Boolean l;
    public Map<String, List<String>> p;
    public Map<String, List<String>> v;
    public Map<String, List<String>> rx;
    public Map<String, List<String>> minmax;
    public Map<String, PropertyConstraintsWire> children;
}

class EventSpecEntryWire {
    public String b;
    public String id;
    public List<String> vids;
    public Map<String, PropertyConstraintsWire> p;
}

class EventSpecMetadata {
    public String schemaId;
    public String branchId;
    public String latestActionId;
    public String sourceId;
}

class EventSpecResponseWire {
    public List<EventSpecEntryWire> events;
    public EventSpecMetadata metadata;
}

class PropertyConstraints {
    public String type;
    public boolean required;
    public Boolean isList;
    public Map<String, List<String>> pinnedValues;
    public Map<String, List<String>> allowedValues;
    public Map<String, List<String>> regexPatterns;
    public Map<String, List<String>> minMaxRanges;
    public Map<String, PropertyConstraints> children;
}

class EventSpecEntry {
    public String branchId;
    public String baseEventId;
    public List<String> variantIds;
    public Map<String, PropertyConstraints> props;
}

class EventSpecResponse {
    public List<EventSpecEntry> events;
    public EventSpecMetadata metadata;
}

class EventSpecCacheEntry {
    public EventSpecResponse spec;
    public double timestamp;
    public double lastAccessed;
    public double eventCount;
}

class FetchEventSpecParams {
    public String apiKey;
    public String streamId;
    public String eventName;
}

class PropertyValidationResult {
    public List<String> failedEventIds;
    public List<String> passedEventIds;
    public Map<String, PropertyValidationResult> children;
}

class ValidationResult {
    public EventSpecMetadata metadata;
    public Map<String, PropertyValidationResult> propertyResults;
}

