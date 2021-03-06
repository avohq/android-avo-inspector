package app.avo.inspector;

import android.app.Application;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JsonSimpleTypeSchemaExtractionTests {

    AvoInspector sut;

    @Mock
    Application mockApplication;
    @Mock
    PackageManager mockPackageManager;
    @Mock
    PackageInfo mockPackageInfo;
    @Mock
    ApplicationInfo mockApplicationInfo;
    @Mock
    SharedPreferences mockSharedPrefs;
    @Mock
    SharedPreferences.Editor mockEditor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mockApplication.getPackageManager()).thenReturn(mockPackageManager);
        when(mockApplication.getPackageName()).thenReturn("");
        when(mockPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mockPackageInfo);
        when(mockApplication.getApplicationInfo()).thenReturn(mockApplicationInfo);
        when(mockApplication.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPrefs);
        when(mockSharedPrefs.getString(anyString(), (String) eq(null))).thenReturn("");
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
        when(mockApplication.getContentResolver()).thenReturn(mock(ContentResolver.class));
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
        when(mockApplication.getContentResolver()).thenReturn(mock(ContentResolver.class));
        when(mockSharedPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);

        sut = new AvoInspector("api key", mockApplication, AvoInspectorEnv.Dev);
    }

    @Test
    public void canExtractInt() {
        JSONObject testJsonObj = new JSONObject();
        try {
            short sh = 1;
            byte bt = 2;
            testJsonObj.put("v0", new Integer(3));
            testJsonObj.put("v1", 4);
            testJsonObj.put("v2", 5L);
            testJsonObj.put("v3", new Long(6));
            testJsonObj.put("v4", new Short("7"));
            testJsonObj.put("v5", sh);
            testJsonObj.put("v6", new Byte("8"));
            testJsonObj.put("v7", bt);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testJsonObj);

        assertEquals(testJsonObj.length(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.AvoInt(), value);
        }
    }

    @Test
    public void canExtractFloat() {
        JSONObject testJsonObj = new JSONObject();
        try {
            testJsonObj.put("v0", new Float(3));
            testJsonObj.put("v1", 4f);
            testJsonObj.put("v2", 5.0);
            testJsonObj.put("v3", new Double(6));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testJsonObj);

        assertEquals(testJsonObj.length(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.AvoFloat(), value);
        }
    }


    @Test
    public void canExtractBoolean() {
        JSONObject testJsonObj = new JSONObject();
        try {
            testJsonObj.put("v0", new Boolean(true));
            testJsonObj.put("v1", false);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testJsonObj);

        assertEquals(testJsonObj.length(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.AvoBoolean(), value);
        }
    }

    @Test
    public void canExtractString() {
        JSONObject testJsonObj = new JSONObject();
        try {
            testJsonObj.put("v0", "Str");
            //testJsonObj.put("v1", (char)'a'); // Wrapped in Int
            testJsonObj.put("v2", new Character('b'));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testJsonObj);

        assertEquals(testJsonObj.length(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.AvoString(), value);
        }
    }

    @Test
    public void canExtractNull() {
        JSONObject testJsonObj = new JSONObject();
        try {
            testJsonObj.put("v0", null);
            testJsonObj.put("v1", new AvoEventSchemaType.AvoNull());
            testJsonObj.put("v2", JSONObject.NULL);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testJsonObj);

        assertEquals(testJsonObj.length(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.AvoNull(), value);
        }
    }
}
