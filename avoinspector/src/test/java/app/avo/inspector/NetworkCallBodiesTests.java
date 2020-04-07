package app.avo.inspector;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class NetworkCallBodiesTests {

    @Test
    public void testSessionStartedBody() {

        AvoNetworkCallsHandler sut = new AvoNetworkCallsHandler(
                "testApiKey", "testEnvName", "testAppName",
                "testAppVersion", "testLibVersion",
                "testInstallationId"
        );
        sut.samplingRate = 0.7;
        AvoSessionTracker.sessionId = "testSessionId";

        Map<String, Object> body = sut.bodyForSessionStartedCall();

        Assert.assertEquals("sessionStarted", body.get("type"));

        Assert.assertNotNull(body.get("createdAt"));
        Assert.assertEquals("testAppVersion", body.get("appVersion"));
        Assert.assertEquals("testApiKey", body.get("apiKey"));
        Assert.assertEquals("testAppName", body.get("appName"));
        Assert.assertNotNull(body.get("messageId"));
        Assert.assertEquals("testEnvName", body.get("env"));
        Assert.assertEquals("testLibVersion", body.get("libVersion"));
        Assert.assertEquals("android", body.get("libPlatform"));
        Assert.assertEquals("testInstallationId", body.get("trackingId"));
        Assert.assertEquals(0.7, body.get("samplingRate"));
        Assert.assertEquals("testSessionId", body.get("sessionId"));
    }

    @Test
    public void testEventSchemaBodyWithAvoObject() throws JSONException {

        AvoNetworkCallsHandler sut = new AvoNetworkCallsHandler(
                "testApiKey", "testEnvName", "testAppName",
                "testAppVersion", "testLibVersion",
                "testInstallationId"
        );

        sut.samplingRate = 1;
        AvoSessionTracker.sessionId = "testSessionId";

        Map<String, AvoEventSchemaType> testSchema = new HashMap<>();
        AvoEventSchemaType.AvoObject avoObject = new AvoEventSchemaType.AvoObject(
                new HashMap<String, AvoEventSchemaType>());
        testSchema.put("nested", avoObject);

        avoObject.children.put("v0", new AvoEventSchemaType.Int());
        avoObject.children.put("v1", new AvoEventSchemaType.Boolean());
        avoObject.children.put("v2", new AvoEventSchemaType.Float());
        avoObject.children.put("v3", new AvoEventSchemaType.String());
        avoObject.children.put("v4", new AvoEventSchemaType.Unknown());
        avoObject.children.put("v5", new AvoEventSchemaType.Null());
        avoObject.children.put("v6", new AvoEventSchemaType.AvoObject(new HashMap<String, AvoEventSchemaType>() {{
            put("a", new AvoEventSchemaType.Int());
        }}));
        avoObject.children.put("v7", new AvoEventSchemaType.List(new HashSet<AvoEventSchemaType>() {{
            add(new AvoEventSchemaType.Int());
        }}));

        Map<String, Object> body = sut.bodyForEventSchemaCall("avoObjectEvent",
                testSchema);

        Assert.assertEquals("event", body.get("type"));
        Assert.assertEquals("[{\"propertyName\":\"nested\",\"children\":\"[{\"propertyName\":\"v6\",\"children\":\"[{\"propertyName\":\"a\",\"propertyType\":\"int\"}]\",\"propertyType\":\"object\"},{\"propertyName\":\"v7\",\"propertyType\":\"list<int>\"},{\"propertyName\":\"v0\",\"propertyType\":\"int\"},{\"propertyName\":\"v1\",\"propertyType\":\"boolean\"},{\"propertyName\":\"v2\",\"propertyType\":\"float\"},{\"propertyName\":\"v3\",\"propertyType\":\"string\"},{\"propertyName\":\"v4\",\"propertyType\":\"unknown\"},{\"propertyName\":\"v5\",\"propertyType\":\"null\"}]\",\"propertyType\":\"object\"}]", body.get("eventProperties"));

        Assert.assertNotNull(body.get("createdAt"));
        Assert.assertEquals("testAppVersion", body.get("appVersion"));
        Assert.assertEquals("testApiKey", body.get("apiKey"));
        Assert.assertEquals("testAppName", body.get("appName"));
        Assert.assertNotNull(body.get("messageId"));
        Assert.assertEquals("testEnvName", body.get("env"));
        Assert.assertEquals("testLibVersion", body.get("libVersion"));
        Assert.assertEquals("android", body.get("libPlatform"));
        Assert.assertEquals("testInstallationId", body.get("trackingId"));
        Assert.assertEquals(1.0, body.get("samplingRate"));
        Assert.assertEquals("testSessionId", body.get("sessionId"));
    }
}
