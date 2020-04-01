package app.avo.inspector;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class AvoInspectorBatcher {

    static String avoInspectorBatchKey = "avo_inspector_batch_key";

    static int batchSize = 20;
    static int batchFlushSeconds = 20;

    List<Map<String, String>> events;

    volatile long batchFlushAttemptMillis = System.currentTimeMillis();

    private SharedPreferences sharedPrefs;

    AvoInspectorNetworkCallsHandler networkCallsHandler;

    Handler mainHandler = new Handler(Looper.getMainLooper());

    AvoInspectorBatcher(Context context) {
        sharedPrefs = context.getSharedPreferences(Util.AVO_SHARED_PREFS_KEY, Context.MODE_PRIVATE);

        events = Collections.synchronizedList(new ArrayList<Map<String, String>>());

        networkCallsHandler = new AvoInspectorNetworkCallsHandler();
    }

    void enterBackground() {
        if (events.size() == 0) {
            return;
        }

        removeExtraElements();

        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (events) {
                    SharedPreferences.Editor editor = sharedPrefs.edit();
                    editor.putString(avoInspectorBatchKey, new JSONArray(events).toString()).apply();
                }
            }
        }).start();
    }

    private void removeExtraElements() {
        if (events.size() > 1000) {
            int extraElements = events.size() - 1000;
            events = events.subList(extraElements, events.size());
        }
    }

    void enterForeground() {
        final String savedData = sharedPrefs.getString(avoInspectorBatchKey, null);
        events = Collections.synchronizedList(new ArrayList<Map<String, String>>());
        if (savedData != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    JSONArray jsonEvents;
                    try {
                        jsonEvents = new JSONArray(savedData);
                        synchronized (events) {
                            for (int i = 0; i < jsonEvents.length(); i++) {
                                try {
                                    JSONObject jsonEvent = jsonEvents.getJSONObject(i);

                                    Map<String, String> event = new HashMap<>();
                                    Iterator keys = jsonEvent.keys();
                                    while (keys.hasNext()) {
                                        String key = (String) keys.next();
                                        event.put(key, jsonEvent.getString(key));
                                    }

                                    events.add(event);
                                } catch (JSONException ignored) {}
                            }
                        }
                    } catch (JSONException ignored) { }
                    postAllAvailableEvents(true);
                }
            }).start();
        }
    }

    void batchSessionStarted() {
        Map<String, String> sessionStarted = new HashMap<>();
        sessionStarted.put("type", "sessionStarted");
        sessionStarted.put("timestamp", System.currentTimeMillis() + "");

        events.add(sessionStarted);

        checkIfBatchNeedsToBeSent();
    }

    void batchTrackEventSchema(String eventName, Map<String, AvoEventSchemaType> type) {
        Map<String, String> trackEvent = new HashMap<>();
        trackEvent.put("type", "trackEvent");
        trackEvent.put("timestamp", System.currentTimeMillis() + "");
        trackEvent.put("name", eventName);
        trackEvent.put("schema", type.toString());

        events.add(trackEvent);

        checkIfBatchNeedsToBeSent();
    }

    void checkIfBatchNeedsToBeSent() {
        int batchSize = events.size();
        long now = System.currentTimeMillis();
        long millisSinceLastFlushAttempt = now - this.batchFlushAttemptMillis;

        if (batchSize % AvoInspectorBatcher.batchSize == 0 || millisSinceLastFlushAttempt >=
                TimeUnit.SECONDS.toMillis(batchFlushSeconds)) {
            postAllAvailableEvents(false);
        }
    }

    private void postAllAvailableEvents(final boolean clearCache) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                filterEvents();

                if (events.size() == 0) {

                    if (clearCache) {
                        sharedPrefs.edit().remove(avoInspectorBatchKey).apply();
                    }

                    return;
                }

                batchFlushAttemptMillis = System.currentTimeMillis();

                final List<Map<String, String>> sendingEvents = events;
                events = new ArrayList<>();

                networkCallsHandler.reportInspectorWithBatchBody(sendingEvents,
                        new AvoInspectorNetworkCallsHandler.Callback() {
                    @Override
                    public void call(@Nullable String error) {
                        if (clearCache) {
                            sharedPrefs.edit().remove(avoInspectorBatchKey).apply();
                        }

                        if (error != null) {
                            events.addAll(sendingEvents);
                        }
                    }
                });
            }
        });
    }

    private void filterEvents() {
        //noinspection SynchronizeOnNonFinalField
        synchronized (events) {
            Iterator<Map<String, String>> iter = events.iterator();

            while (iter.hasNext()) {
                Map<String, String> item = iter.next();
                if (!item.containsKey("type")) {
                    iter.remove();
                }
            }
        }
    }
}
