package app.avo.inspector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import android.util.Log;

interface EventSpecRequestClient {
    EventSpecResponseWire get(String url, int timeoutMillis) throws Exception;
}

class DefaultEventSpecRequestClient implements EventSpecRequestClient {
    @Override
    public EventSpecResponseWire get(String url, int timeoutMillis) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setRequestProperty("Content-Type", "application/json");
            int status = connection.getResponseCode();
            if (status != 200) {
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line = reader.readLine();
            while (line != null) {
                response.append(line);
                line = reader.readLine();
            }
            reader.close();
            JSONObject json = new JSONObject(response.toString());
            return parseResponse(json);
        } catch (JSONException e) {
            return null;
        } finally {
            connection.disconnect();
        }
    }

    private EventSpecResponseWire parseResponse(JSONObject json) {
        EventSpecResponseWire response = new EventSpecResponseWire();
        response.events = new ArrayList<>();
        JSONArray events = json.optJSONArray("events");
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                JSONObject entryJson = events.optJSONObject(i);
                if (entryJson != null) {
                    response.events.add(parseEntry(entryJson));
                }
            }
        }
        JSONObject metadataJson = json.optJSONObject("metadata");
        if (metadataJson != null) {
            EventSpecMetadata metadata = new EventSpecMetadata();
            metadata.schemaId = metadataJson.optString("schemaId", null);
            metadata.branchId = metadataJson.optString("branchId", null);
            metadata.latestActionId = metadataJson.optString("latestActionId", null);
            if (metadataJson.has("sourceId")) {
                metadata.sourceId = metadataJson.optString("sourceId", null);
            }
            response.metadata = metadata;
        }
        return response;
    }

    private EventSpecEntryWire parseEntry(JSONObject json) {
        EventSpecEntryWire entry = new EventSpecEntryWire();
        entry.b = json.optString("b", null);
        entry.id = json.optString("id", null);
        entry.vids = new ArrayList<>();
        JSONArray vids = json.optJSONArray("vids");
        if (vids != null) {
            for (int i = 0; i < vids.length(); i++) {
                entry.vids.add(vids.optString(i));
            }
        }
        entry.p = new HashMap<>();
        JSONObject propsJson = json.optJSONObject("p");
        if (propsJson != null) {
            Iterator<String> keys = propsJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject constraintJson = propsJson.optJSONObject(key);
                if (constraintJson != null) {
                    entry.p.put(key, parseConstraints(constraintJson));
                }
            }
        }
        return entry;
    }

    private PropertyConstraintsWire parseConstraints(JSONObject json) {
        PropertyConstraintsWire constraints = new PropertyConstraintsWire();
        constraints.t = json.optString("t", null);
        if (json.has("r")) {
            constraints.r = json.optBoolean("r");
        }
        if (json.has("l")) {
            constraints.l = json.optBoolean("l");
        }
        if (json.has("p")) {
            constraints.p = parseStringListMap(json.optJSONObject("p"));
        }
        if (json.has("v")) {
            constraints.v = parseStringListMap(json.optJSONObject("v"));
        }
        if (json.has("rx")) {
            constraints.rx = parseStringListMap(json.optJSONObject("rx"));
        }
        if (json.has("minmax")) {
            constraints.minmax = parseStringListMap(json.optJSONObject("minmax"));
        }
        if (json.has("children")) {
            constraints.children = parseChildren(json.optJSONObject("children"));
        }
        return constraints;
    }

    private Map<String, List<String>> parseStringListMap(JSONObject json) {
        Map<String, List<String>> result = new HashMap<>();
        if (json == null) {
            return result;
        }
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONArray values = json.optJSONArray(key);
            if (values != null) {
                List<String> items = new ArrayList<>();
                for (int i = 0; i < values.length(); i++) {
                    items.add(values.optString(i));
                }
                result.put(key, items);
            }
        }
        return result;
    }

    private Map<String, PropertyConstraintsWire> parseChildren(JSONObject json) {
        Map<String, PropertyConstraintsWire> result = new HashMap<>();
        if (json == null) {
            return result;
        }
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject childJson = json.optJSONObject(key);
            if (childJson != null) {
                result.put(key, parseConstraints(childJson));
            }
        }
        return result;
    }
}

interface EventSpecFetchCallback {
    void onResult(EventSpecResponse response);
}

public class AvoEventSpecFetcher {
    private final String baseUrl;
    private final int timeout;
    private final int wallTimeout;
    private final Map<String, List<EventSpecFetchCallback>> inFlightCallbacks = new HashMap<>();
    private final String env;
    private final EventSpecRequestClient requestClient;

    public AvoEventSpecFetcher(int timeout, String env) {
        this(timeout, timeout * 2, env, "https://api.avo.app", new DefaultEventSpecRequestClient());
    }

    public AvoEventSpecFetcher(int timeout, int wallTimeout, String env) {
        this(timeout, wallTimeout, env, "https://api.avo.app", new DefaultEventSpecRequestClient());
    }

    public AvoEventSpecFetcher(int timeout, String env, String baseUrl) {
        this(timeout, timeout * 2, env, baseUrl, new DefaultEventSpecRequestClient());
    }

    public AvoEventSpecFetcher(int timeout, String env, String baseUrl, EventSpecRequestClient requestClient) {
        this(timeout, timeout * 2, env, baseUrl, requestClient);
    }

    public AvoEventSpecFetcher(int timeout, int wallTimeout, String env, String baseUrl, EventSpecRequestClient requestClient) {
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.wallTimeout = wallTimeout;
        this.env = env;
        this.requestClient = requestClient;
    }

    private String generateRequestKey(FetchEventSpecParams params) {
        return params.apiKey + ":" + params.streamId + ":" + params.eventName;
    }

    public void fetch(FetchEventSpecParams params, EventSpecFetchCallback callback) {
        String requestKey = generateRequestKey(params);
        synchronized (inFlightCallbacks) {
            List<EventSpecFetchCallback> existing = inFlightCallbacks.get(requestKey);
            if (existing != null) {
                existing.add(callback);
                return;
            }
            List<EventSpecFetchCallback> callbacks = new ArrayList<>();
            callbacks.add(callback);
            inFlightCallbacks.put(requestKey, callbacks);
        }
        fetchInternal(params, requestKey);
    }

    private void fetchInternal(FetchEventSpecParams params, String requestKey) {
        if (!("dev".equals(env) || "staging".equals(env))) {
            deliverResult(requestKey, null);
            return;
        }
        new Thread(() -> {
            EventSpecResponse result = null;
            ExecutorService httpExecutor = Executors.newSingleThreadExecutor();
            try {
                String url = buildUrl(params);
                if (AvoInspector.isLogging()) {
                    Log.d("Avo Inspector", "Fetching event spec for event: " + params.eventName + " url: " + url);
                }
                Future<EventSpecResponseWire> future = httpExecutor.submit(() -> makeRequest(url));
                EventSpecResponseWire wireResponse;
                try {
                    wireResponse = future.get(wallTimeout, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    if (AvoInspector.isLogging()) {
                        Log.e("Avo Inspector", "Event spec fetch timed out (wall-clock " + wallTimeout + "ms) for: " + params.eventName);
                    }
                    wireResponse = null;
                } catch (ExecutionException e) {
                    throw e.getCause() != null ? e.getCause() : e;
                }
                if (wireResponse == null) {
                    if (AvoInspector.isLogging()) {
                        Log.e("Avo Inspector", "Failed to fetch event spec for: " + params.eventName);
                    }
                } else if (!hasExpectedShape(wireResponse)) {
                    if (AvoInspector.isLogging()) {
                        Log.e("Avo Inspector", "Invalid event spec response for: " + params.eventName);
                    }
                } else {
                    result = parseEventSpecResponse(wireResponse);
                    if (AvoInspector.isLogging()) {
                        Log.d("Avo Inspector", "Successfully fetched event spec for: " + params.eventName
                                + " with " + (result.events != null ? result.events.size() : 0) + " events");
                    }
                }
            } catch (Throwable e) {
                if (AvoInspector.isLogging()) {
                    Log.e("Avo Inspector", "Error fetching event spec for: " + params.eventName + " " + e);
                }
            } finally {
                httpExecutor.shutdownNow();
            }
            deliverResult(requestKey, result);
        }).start();
    }

    private void deliverResult(String requestKey, EventSpecResponse result) {
        List<EventSpecFetchCallback> callbacks;
        synchronized (inFlightCallbacks) {
            callbacks = inFlightCallbacks.remove(requestKey);
        }
        if (callbacks != null) {
            for (EventSpecFetchCallback cb : callbacks) {
                cb.onResult(result);
            }
        }
    }

    private String buildUrl(FetchEventSpecParams params) {
        String apiKey = encode(params.apiKey);
        String streamId = encode(params.streamId);
        String eventName = encode(params.eventName);
        return baseUrl + "/trackingPlan/eventSpec?apiKey=" + apiKey + "&streamId=" + streamId + "&eventName=" + eventName;
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private EventSpecResponseWire makeRequest(String url) throws Exception {
        return requestClient.get(url, timeout);
    }

    private boolean hasExpectedShape(EventSpecResponseWire response) {
        return response != null
                && response.events != null
                && response.metadata != null
                && response.metadata.schemaId != null
                && response.metadata.branchId != null
                && response.metadata.latestActionId != null;
    }

    private static EventSpecResponse parseEventSpecResponse(EventSpecResponseWire wire) {
        EventSpecResponse response = new EventSpecResponse();
        response.events = new ArrayList<>();
        if (wire.events != null) {
            for (EventSpecEntryWire entryWire : wire.events) {
                response.events.add(parseEventSpecEntry(entryWire));
            }
        }
        response.metadata = wire.metadata;
        return response;
    }

    private static EventSpecEntry parseEventSpecEntry(EventSpecEntryWire wire) {
        EventSpecEntry entry = new EventSpecEntry();
        entry.branchId = wire.b;
        entry.baseEventId = wire.id;
        entry.variantIds = wire.vids;
        entry.props = new HashMap<>();
        if (wire.p != null) {
            for (Map.Entry<String, PropertyConstraintsWire> prop : wire.p.entrySet()) {
                entry.props.put(prop.getKey(), parsePropertyConstraints(prop.getValue()));
            }
        }
        return entry;
    }

    private static PropertyConstraints parsePropertyConstraints(PropertyConstraintsWire wire) {
        PropertyConstraints result = new PropertyConstraints();
        result.type = wire.t;
        result.required = wire.r;
        if (wire.l != null) {
            result.isList = wire.l;
        }
        result.pinnedValues = wire.p;
        result.allowedValues = wire.v;
        result.regexPatterns = wire.rx;
        result.minMaxRanges = wire.minmax;
        if (wire.children != null) {
            result.children = new HashMap<>();
            for (Map.Entry<String, PropertyConstraintsWire> child : wire.children.entrySet()) {
                result.children.put(child.getKey(), parsePropertyConstraints(child.getValue()));
            }
        }
        return result;
    }
}
