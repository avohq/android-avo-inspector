package app.avo.inspector;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

class AvoInspectorNetworkCallsHandler {

    String apiKey;
    String envName;
    String appName;
    String appVersion;
    String libVersion;

    double samplingRate;

    /*public AvoInspectorNetworkCallsHandler(String apiKey, String envName, String appName,
                                           String appVersion, String libVersion) {
        this.apiKey = apiKey;
        this.envName = envName;
        this.appName = appName;
        this.appVersion = appVersion;
        this.libVersion = libVersion;
    }

    Map<String, String> bodyForSessionStartedCall() {
     //    [baseBody setValue:@"sessionStarted" forKey:@"type"];
    }*/

    private Map<String, String> createBaseCallBody() {
        Map<String, String> result = new HashMap<>();

        result.put("apiKey", apiKey);
        result.put("appName", appName);
        result.put("appVersion", appVersion);
        result.put("libVersion", libVersion);
        result.put("env", envName);
        result.put("libPlatform", "android");
        result.put("messageId", UUID.randomUUID().toString());
        result.put("trackingId", ""); //TODO installation id
        result.put("createdAt", ""); // TODO ISO8601UTCString

        return result;
    }

    void reportInspectorWithBatchBody(List<Map<String, String>> data, Callback completionHandler) {
        completionHandler.call(null);

       /* new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL apiUrl = new URL("https://api.avo.app/datascope/v0/track");

                    JSONObject body = buildRequestBody(deviceId, eventName, eventProperties);

                    HttpsURLConnection connection = null;
                    try {
                        connection = (HttpsURLConnection) apiUrl.openConnection();

                        connection.setRequestMethod("POST");
                        connection.setDoInput(true);

                        writeTrackingCallHeader(connection);
                        writeTrackingCallBody(body, connection);

                        connection.connect();

                        int responseCode = connection.getResponseCode();
                        if (responseCode != HttpsURLConnection.HTTP_OK) {
                            throw new IOException("HTTP error code: " + responseCode);
                        }
                    } finally {
                        if (connection != null) {
                            connection.disconnect();
                        }
                    }
                } catch (JSONException e) {
                    Log.e("AvoInspector", "Failed to parse data");
                } catch (IOException e) {
                    Log.e("AvoInspector", "Failed to perform network call");
                }
            }
        }).start();*/

    }

    private void writeTrackingCallBody(JSONObject body, HttpsURLConnection connection) throws IOException {
        byte[] bodyBytes = body.toString().getBytes("UTF-8");
        OutputStream os = connection.getOutputStream();
        os.write(bodyBytes);
        os.close();
    }

    private JSONObject buildRequestBody(Map<String, String> data) throws JSONException {

        JSONObject body = new JSONObject(data);

        return body;
    }

    private void writeTrackingCallHeader(HttpsURLConnection connection) {
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
    }

    interface Callback {
        void call(@Nullable String error);
    }

}
