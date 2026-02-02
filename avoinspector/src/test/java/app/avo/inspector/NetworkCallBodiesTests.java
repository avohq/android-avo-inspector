package app.avo.inspector;

import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NetworkCallBodiesTests {

    private AvoStorage prevAvoStorage;

    @Before
    public void setUp() {
        prevAvoStorage = AvoInspector.avoStorage;
        AvoAnonymousId.clearCache();
        AvoStorage mockStorage = mock(AvoStorage.class);
        when(mockStorage.isInitialized()).thenReturn(true);
        when(mockStorage.getItem(any())).thenReturn("testAnonymousId");
        AvoInspector.avoStorage = mockStorage;
    }

    @After
    public void tearDown() {
        AvoInspector.avoStorage = prevAvoStorage;
        AvoAnonymousId.clearCache();
    }

    @Test
    public void testEventSchemaBodyFromAvoFunction() throws JSONException {
        AvoNetworkCallsHandler sut = new AvoNetworkCallsHandler(
                "testApiKey", "testEnvName", "testAppName",
                "testAppVersion", "testLibVersion"
        );

        sut.samplingRate = 1;

        Map<String, AvoEventSchemaType> testSchema = new HashMap<>();

        Map<String, Object> body = sut.bodyForEventSchemaCall("avoObjectEvent",
                testSchema, "event Id", "event Hash");

        Assert.assertEquals(true, body.get("avoFunction"));
        Assert.assertEquals("event Id", body.get("eventId"));
        Assert.assertEquals("event Hash", body.get("eventHash"));
    }

    @Test
    public void testEventSchemaBodyWithAvoObject() throws JSONException {

        AvoNetworkCallsHandler sut = new AvoNetworkCallsHandler(
                "testApiKey", "testEnvName", "testAppName",
                "testAppVersion", "testLibVersion"
        );

        sut.samplingRate = 1;

        Map<String, AvoEventSchemaType> testSchema = new HashMap<>();
        AvoEventSchemaType.AvoObject avoObject = new AvoEventSchemaType.AvoObject(
                new HashMap<String, AvoEventSchemaType>());
        testSchema.put("nested", avoObject);

        avoObject.children.put("v0", new AvoEventSchemaType.AvoInt());
        avoObject.children.put("v1", new AvoEventSchemaType.AvoBoolean());
        avoObject.children.put("v2", new AvoEventSchemaType.AvoFloat());
        avoObject.children.put("v3", new AvoEventSchemaType.AvoString());
        avoObject.children.put("v4", new AvoEventSchemaType.AvoUnknownType());
        avoObject.children.put("v5", new AvoEventSchemaType.AvoNull());
        avoObject.children.put("v6", new AvoEventSchemaType.AvoObject(new HashMap<String, AvoEventSchemaType>() {{
            put("a", new AvoEventSchemaType.AvoInt());
        }}));
        avoObject.children.put("v7", new AvoEventSchemaType.AvoList(new HashSet<AvoEventSchemaType>() {{
            add(new AvoEventSchemaType.AvoInt());
            add(new AvoEventSchemaType.AvoObject(new HashMap<String, AvoEventSchemaType>() {{
                put("key", new AvoEventSchemaType.AvoFloat());
            }}));
        }}));

        Map<String, Object> body = sut.bodyForEventSchemaCall("avoObjectEvent",
                testSchema, null, null);

        Assert.assertEquals("event", body.get("type"));
        Assert.assertEquals("[{\"propertyName\":\"nested\",\"children\":[{\"propertyName\":\"v6\",\"children\":[{\"propertyName\":\"a\",\"propertyType\":\"int\"}],\"propertyType\":\"object\"},{\"propertyName\":\"v7\",\"propertyType\":\"list<{\\\"propertyName\\\":\\\"key\\\",\\\"propertyType\\\":\\\"float\\\"}|int>\"},{\"propertyName\":\"v0\",\"propertyType\":\"int\"},{\"propertyName\":\"v1\",\"propertyType\":\"boolean\"},{\"propertyName\":\"v2\",\"propertyType\":\"float\"},{\"propertyName\":\"v3\",\"propertyType\":\"string\"},{\"propertyName\":\"v4\",\"propertyType\":\"unknown\"},{\"propertyName\":\"v5\",\"propertyType\":\"null\"}],\"propertyType\":\"object\"}]", body.get("eventProperties").toString());

        Assert.assertNotNull(body.get("createdAt"));
        Assert.assertEquals("testAppVersion", body.get("appVersion"));
        Assert.assertEquals("testApiKey", body.get("apiKey"));
        Assert.assertEquals("testAppName", body.get("appName"));
        Assert.assertNotNull(body.get("messageId"));
        Assert.assertEquals("testEnvName", body.get("env"));
        Assert.assertEquals("testLibVersion", body.get("libVersion"));
        Assert.assertEquals("android", body.get("libPlatform"));
        Assert.assertEquals("", body.get("trackingId"));
        Assert.assertEquals(1.0, body.get("samplingRate"));
        Assert.assertEquals("", body.get("sessionId"));
        Assert.assertEquals("testAnonymousId", body.get("anonymousId"));
        Assert.assertEquals(false, body.get("avoFunction"));
        Assert.assertNull(body.get("eventId"));
        Assert.assertNull(body.get("eventHash"));
    }
}
