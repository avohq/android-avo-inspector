package app.avo.inspector;

import android.app.Application;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class EncryptionIntegrationTests {

    @Mock Application mockApplication;
    @Mock PackageManager mockPackageManager;
    @Mock PackageInfo mockPackageInfo;
    @Mock ApplicationInfo mockApplicationInfo;
    @Mock SharedPreferences mockSharedPrefs;
    @Mock SharedPreferences.Editor mockEditor;

    private AvoStorage prevAvoStorage;
    private String testPublicKeyHex;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Swap base64Encoder for unit tests
        AvoEncryption.base64Encoder = new AvoEncryption.Base64Encoder() {
            @Override
            public String encode(byte[] data) {
                return java.util.Base64.getEncoder().encodeToString(data);
            }
        };

        mockPackageInfo.versionName = "1.0.0";
        mockApplicationInfo.packageName = "testPckg";

        when(mockApplication.getPackageManager()).thenReturn(mockPackageManager);
        when(mockApplication.getPackageName()).thenReturn("");
        when(mockPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mockPackageInfo);
        when(mockApplication.getApplicationInfo()).thenReturn(mockApplicationInfo);
        when(mockApplication.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPrefs);
        when(mockSharedPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        when(mockEditor.remove(anyString())).thenReturn(mockEditor);
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
        when(mockApplication.getContentResolver()).thenReturn(mock(ContentResolver.class));

        // Set up AvoStorage mock
        prevAvoStorage = AvoInspector.avoStorage;
        AvoAnonymousId.clearCache();
        AvoStorage mockStorage = mock(AvoStorage.class);
        when(mockStorage.isInitialized()).thenReturn(true);
        when(mockStorage.getItem(any())).thenReturn("testStreamId");
        AvoInspector.avoStorage = mockStorage;

        // Generate test key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyGen.generateKeyPair();

        ECPublicKey pubKey = (ECPublicKey) keyPair.getPublic();
        byte[] x = toUnsigned32Bytes(pubKey.getW().getAffineX());
        byte[] y = toUnsigned32Bytes(pubKey.getW().getAffineY());
        StringBuilder hex = new StringBuilder("04");
        for (byte b : x) hex.append(String.format("%02x", b));
        for (byte b : y) hex.append(String.format("%02x", b));
        testPublicKeyHex = hex.toString();
    }

    @After
    public void tearDown() {
        AvoInspector.avoStorage = prevAvoStorage;
        AvoAnonymousId.clearCache();
        AvoDeduplicator.clearEvents();
    }

    // =========================================================================
    // Batched event encryption tests
    // =========================================================================

    @Test
    public void batchedEventBodyIncludesEncryptedValuesInDevMode() throws Exception {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "testApiKey", "dev", "testApp", "1.0.0", "7", testPublicKeyHex);

        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        schema.put("userId", new AvoEventSchemaType.AvoString());
        schema.put("count", new AvoEventSchemaType.AvoInt());

        Map<String, Object> eventProperties = new HashMap<>();
        eventProperties.put("userId", "user123");
        eventProperties.put("count", 42);

        Map<String, Object> body = handler.bodyForEventSchemaCall(
                "TestEvent", schema, null, null, eventProperties);

        JSONArray properties = (JSONArray) body.get("eventProperties");
        assertNotNull(properties);

        boolean foundEncryptedUserId = false;
        boolean foundEncryptedCount = false;
        for (int i = 0; i < properties.length(); i++) {
            JSONObject prop = properties.getJSONObject(i);
            if ("userId".equals(prop.optString("propertyName"))) {
                assertTrue("userId should have encryptedPropertyValue", prop.has("encryptedPropertyValue"));
                foundEncryptedUserId = true;
            }
            if ("count".equals(prop.optString("propertyName"))) {
                assertTrue("count should have encryptedPropertyValue", prop.has("encryptedPropertyValue"));
                foundEncryptedCount = true;
            }
        }
        assertTrue("Should find userId property", foundEncryptedUserId);
        assertTrue("Should find count property", foundEncryptedCount);
    }

    @Test
    public void batchedEventBodyIncludesEncryptedValuesInStagingMode() throws Exception {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "testApiKey", "staging", "testApp", "1.0.0", "7", testPublicKeyHex);

        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        schema.put("name", new AvoEventSchemaType.AvoString());

        Map<String, Object> eventProperties = new HashMap<>();
        eventProperties.put("name", "test");

        Map<String, Object> body = handler.bodyForEventSchemaCall(
                "TestEvent", schema, null, null, eventProperties);

        JSONArray properties = (JSONArray) body.get("eventProperties");
        JSONObject prop = properties.getJSONObject(0);
        assertTrue(prop.has("encryptedPropertyValue"));
    }

    @Test
    public void noEncryptionInProdEvenWithKey() throws Exception {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "testApiKey", "prod", "testApp", "1.0.0", "7", testPublicKeyHex);

        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        schema.put("userId", new AvoEventSchemaType.AvoString());

        Map<String, Object> eventProperties = new HashMap<>();
        eventProperties.put("userId", "user123");

        Map<String, Object> body = handler.bodyForEventSchemaCall(
                "TestEvent", schema, null, null, eventProperties);

        JSONArray properties = (JSONArray) body.get("eventProperties");
        for (int i = 0; i < properties.length(); i++) {
            JSONObject prop = properties.getJSONObject(i);
            assertFalse("Prod should not have encrypted values", prop.has("encryptedPropertyValue"));
        }
    }

    @Test
    public void noEncryptionWhenKeyIsNull() throws Exception {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "testApiKey", "dev", "testApp", "1.0.0", "7", null);

        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        schema.put("userId", new AvoEventSchemaType.AvoString());

        Map<String, Object> eventProperties = new HashMap<>();
        eventProperties.put("userId", "user123");

        Map<String, Object> body = handler.bodyForEventSchemaCall(
                "TestEvent", schema, null, null, eventProperties);

        JSONArray properties = (JSONArray) body.get("eventProperties");
        for (int i = 0; i < properties.length(); i++) {
            JSONObject prop = properties.getJSONObject(i);
            assertFalse("Null key should not produce encrypted values", prop.has("encryptedPropertyValue"));
        }
    }

    @Test
    public void publicEncryptionKeyInBaseBody() throws Exception {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "testApiKey", "dev", "testApp", "1.0.0", "7", testPublicKeyHex);

        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        Map<String, Object> body = handler.bodyForEventSchemaCall(
                "TestEvent", schema, null, null, null);

        assertEquals(testPublicKeyHex, body.get("publicEncryptionKey"));
    }

    @Test
    public void noPublicEncryptionKeyInBaseBodyWhenNull() throws Exception {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "testApiKey", "dev", "testApp", "1.0.0", "7", null);

        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        Map<String, Object> body = handler.bodyForEventSchemaCall(
                "TestEvent", schema, null, null, null);

        assertFalse(body.containsKey("publicEncryptionKey"));
    }

    // =========================================================================
    // Validated event encryption tests
    // =========================================================================

    @Test
    public void validatedEventBodyIncludesEncryptedValues() throws Exception {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "testApiKey", "dev", "testApp", "1.0.0", "7", testPublicKeyHex);

        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        schema.put("userId", new AvoEventSchemaType.AvoString());

        ValidationResult validationResult = new ValidationResult();
        validationResult.metadata = new EventSpecMetadata();
        validationResult.propertyResults = new HashMap<>();

        Map<String, Object> eventProperties = new HashMap<>();
        eventProperties.put("userId", "user123");

        Map<String, Object> body = handler.bodyForValidatedEventSchemaCall(
                "TestEvent", schema, null, null, validationResult, "stream123", eventProperties);

        JSONArray properties = (JSONArray) body.get("eventProperties");
        JSONObject prop = properties.getJSONObject(0);
        assertEquals("userId", prop.getString("propertyName"));
        assertTrue("Validated event should have encrypted value", prop.has("encryptedPropertyValue"));
    }

    // =========================================================================
    // Nested object and list tests
    // =========================================================================

    @Test
    public void nestedObjectChildrenAreEncrypted() throws Exception {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "testApiKey", "dev", "testApp", "1.0.0", "7", testPublicKeyHex);

        Map<String, AvoEventSchemaType> innerChildren = new HashMap<>();
        innerChildren.put("street", new AvoEventSchemaType.AvoString());
        innerChildren.put("zip", new AvoEventSchemaType.AvoInt());

        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        schema.put("address", new AvoEventSchemaType.AvoObject(innerChildren));

        Map<String, Object> innerProps = new HashMap<>();
        innerProps.put("street", "123 Main St");
        innerProps.put("zip", 90210);

        Map<String, Object> eventProperties = new HashMap<>();
        eventProperties.put("address", innerProps);

        Map<String, Object> body = handler.bodyForEventSchemaCall(
                "TestEvent", schema, null, null, eventProperties);

        JSONArray properties = (JSONArray) body.get("eventProperties");
        JSONObject addressProp = null;
        for (int i = 0; i < properties.length(); i++) {
            JSONObject p = properties.getJSONObject(i);
            if ("address".equals(p.optString("propertyName"))) {
                addressProp = p;
                break;
            }
        }
        assertNotNull("Should find address property", addressProp);
        assertFalse("Object itself should not be encrypted", addressProp.has("encryptedPropertyValue"));

        JSONArray children = addressProp.getJSONArray("children");
        boolean foundEncryptedStreet = false;
        boolean foundEncryptedZip = false;
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.getJSONObject(i);
            if ("street".equals(child.optString("propertyName"))) {
                assertTrue("street child should have encryptedPropertyValue", child.has("encryptedPropertyValue"));
                foundEncryptedStreet = true;
            }
            if ("zip".equals(child.optString("propertyName"))) {
                assertTrue("zip child should have encryptedPropertyValue", child.has("encryptedPropertyValue"));
                foundEncryptedZip = true;
            }
        }
        assertTrue("Should find street child", foundEncryptedStreet);
        assertTrue("Should find zip child", foundEncryptedZip);
    }

    @Test
    public void listValuesAreNotEncrypted() throws Exception {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "testApiKey", "dev", "testApp", "1.0.0", "7", testPublicKeyHex);

        java.util.Set<AvoEventSchemaType> subtypes = new java.util.HashSet<>();
        subtypes.add(new AvoEventSchemaType.AvoString());

        Map<String, AvoEventSchemaType> schema = new HashMap<>();
        schema.put("tags", new AvoEventSchemaType.AvoList(subtypes));

        java.util.List<String> tags = java.util.Arrays.asList("a", "b", "c");
        Map<String, Object> eventProperties = new HashMap<>();
        eventProperties.put("tags", tags);

        Map<String, Object> body = handler.bodyForEventSchemaCall(
                "TestEvent", schema, null, null, eventProperties);

        JSONArray properties = (JSONArray) body.get("eventProperties");
        for (int i = 0; i < properties.length(); i++) {
            JSONObject prop = properties.getJSONObject(i);
            if ("tags".equals(prop.optString("propertyName"))) {
                assertFalse("List values should NOT be encrypted", prop.has("encryptedPropertyValue"));
            }
        }
    }

    // =========================================================================
    // shouldEncrypt tests
    // =========================================================================

    @Test
    public void shouldEncryptReturnsTrueForDevWithKey() {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "key", "dev", "app", "1.0", "7", testPublicKeyHex);
        assertTrue(handler.shouldEncrypt());
    }

    @Test
    public void shouldEncryptReturnsTrueForStagingWithKey() {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "key", "staging", "app", "1.0", "7", testPublicKeyHex);
        assertTrue(handler.shouldEncrypt());
    }

    @Test
    public void shouldEncryptReturnsFalseForProd() {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "key", "prod", "app", "1.0", "7", testPublicKeyHex);
        assertFalse(handler.shouldEncrypt());
    }

    @Test
    public void shouldEncryptReturnsFalseForNullKey() {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "key", "dev", "app", "1.0", "7", null);
        assertFalse(handler.shouldEncrypt());
    }

    @Test
    public void shouldEncryptReturnsFalseForEmptyKey() {
        AvoNetworkCallsHandler handler = new AvoNetworkCallsHandler(
                "key", "dev", "app", "1.0", "7", "");
        assertFalse(handler.shouldEncrypt());
    }

    // =========================================================================
    // JSON stringify value tests
    // =========================================================================

    @Test
    public void jsonStringifyString() {
        assertEquals("\"hello\"", Util.jsonStringifyValue("hello"));
    }

    @Test
    public void jsonStringifyInteger() {
        assertEquals("42", Util.jsonStringifyValue(42));
    }

    @Test
    public void jsonStringifyDouble() {
        assertEquals("3.14", Util.jsonStringifyValue(3.14));
    }

    @Test
    public void jsonStringifyBoolean() {
        assertEquals("true", Util.jsonStringifyValue(true));
        assertEquals("false", Util.jsonStringifyValue(false));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static byte[] toUnsigned32Bytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == 32) {
            return bytes;
        } else if (bytes.length > 32) {
            byte[] trimmed = new byte[32];
            System.arraycopy(bytes, bytes.length - 32, trimmed, 0, 32);
            return trimmed;
        } else {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 32 - bytes.length, bytes.length);
            return padded;
        }
    }
}
