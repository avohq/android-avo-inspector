package app.avo.inspector;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

class AvoNetworkCallsHandler {

    String apiKey;
    String envName;
    String appName;
    String appVersion;
    String libVersion;

    double samplingRate = 1.0;

    Handler callbackHandler = new Handler(Looper.getMainLooper());

    AvoNetworkCallsHandler(String apiKey, String envName, String appName,
                           String appVersion, String libVersion) {
        this.apiKey = apiKey;
        this.envName = envName;
        this.appName = appName;
        this.appVersion = appVersion;
        this.libVersion = libVersion;
    }

    Map<String, Object> bodyForEventSchemaCall(String eventName,
                                               Map<String, AvoEventSchemaType> schema,
                                               @Nullable String eventId, @Nullable String eventHash) {
        JSONArray properties = Util.remapProperties(schema);

        Map<String, Object> eventSchemaBody = createBaseCallBody();

        if (eventId != null) {
            eventSchemaBody.put("avoFunction", true);
            eventSchemaBody.put("eventId", eventId);
            eventSchemaBody.put("eventHash", eventHash);
        } else {
            eventSchemaBody.put("avoFunction", false);
        }

        eventSchemaBody.put("type", "event");
        eventSchemaBody.put("eventName", eventName);
        eventSchemaBody.put("eventProperties", properties);

        return eventSchemaBody;
    }

    private Map<String, Object> createBaseCallBody() {
        Map<String, Object> result = new HashMap<>();

        result.put("apiKey", apiKey);
        result.put("appName", appName);
        result.put("appVersion", appVersion);
        result.put("libVersion", libVersion);
        result.put("env", envName);
        result.put("libPlatform", "android");
        result.put("messageId", UUID.randomUUID().toString());
        result.put("trackingId", "");
        result.put("createdAt", Util.currentTimeAsISO8601UTCString());
        result.put("sessionId", "");
        result.put("anonymousId", AvoAnonymousId.anonymousId());
        result.put("samplingRate", samplingRate);

        return result;
    }

    void reportInspectorWithBatchBody(final List<Map<String, Object>> data, final Callback completionHandler) {
        if (Math.random() > samplingRate) {
            if (AvoInspector.isLogging()) {
                Log.d("Avo Inspector", "Last event schema dropped due to sampling rate");
            }
            return;
        }

        if (AvoInspector.isLogging()) {
            for (Map<String, Object> item : data) {
                Object type = item.get("type");

                if (type != null && type.equals("event")) {
                    Object eventName = item.get("eventName");
                    Object eventProps = item.get("eventProperties");

                    if (eventName != null && eventProps != null) {
                        Log.d("Avo Inspector", "Sending event " + eventName + " with schema {\n" + eventProps + "\n}");
                    }
                } else {
                    Log.d("Avo Inspector", "Error! Unknown event type.");
                }
            }
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL apiUrl = new URL("https://api.avo.app/inspector/v1/track");

                    HttpsURLConnection connection = null;
                    try {
                        connection = (HttpsURLConnection) apiUrl.openConnection();

                        connection.setRequestMethod("POST");
                        connection.setDoInput(true);
                        connection.setDoOutput(true);

                        writeTrackingCallHeader(connection);
                        writeTrackingCallBody(data, connection);

                        connection.connect();

                        final int responseCode = connection.getResponseCode();
                        if (responseCode != HttpsURLConnection.HTTP_OK) {
                            callbackHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    completionHandler.call(true);
                                }
                            });
                        } else {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                            //noinspection TryFinallyCanBeTryWithResources
                            try {
                                StringBuilder response = new StringBuilder();
                                String inputLine = reader.readLine();
                                while (inputLine != null) {
                                    response.append(inputLine);
                                    inputLine = reader.readLine();
                                }
                                JSONObject json;
                                try {
                                    json = new JSONObject(response.toString());
                                } catch (JSONException e) {
                                    json = new JSONObject();
                                }

                                final JSONObject finalJson = json;
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            samplingRate = finalJson.getDouble("samplingRate");
                                        } catch (JSONException ignored) {}
                                    }
                                });
                            } finally {
                                reader.close();
                            }
                            callbackHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    completionHandler.call(false);
                                }
                            });
                        }
                    } finally {
                        if (connection != null) {
                            connection.disconnect();
                        }
                    }
                } catch (IOException e) {
                    if (AvoInspector.isLogging()) {
                        Log.e("AvoInspector", "Failed to perform network call, will retry later");
                    }
                    callbackHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            completionHandler.call(true);
                        }
                    });
                } catch (final Exception e) {
                    Util.handleException(e, envName);
                    callbackHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            completionHandler.call(false);
                        }
                    });
                }
            }
        }).start();
    }

    private void writeTrackingCallBody(List<Map<String, Object>> data, HttpsURLConnection connection) throws IOException {

        JSONArray body = new JSONArray();
        for (Iterator<Map<String, Object>> iterator = data.iterator(); iterator.hasNext(); ) {
            Map<String, Object> event = iterator.next();
            JSONObject eventJson = new JSONObject(event);
            body.put(eventJson);
        }
        String bodyString = body.toString();

        @SuppressWarnings("CharsetObjectCanBeUsed")
        byte[] bodyBytes = bodyString.getBytes("UTF-8");
        OutputStream os = connection.getOutputStream();
        os.write(bodyBytes);
        os.close();
    }

    private void writeTrackingCallHeader(HttpsURLConnection connection) {
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
    }

    interface Callback {
        void call(boolean retry);
    }
}
