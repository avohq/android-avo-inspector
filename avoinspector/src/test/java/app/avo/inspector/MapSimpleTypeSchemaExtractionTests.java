package app.avo.inspector;

import android.app.Application;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class MapSimpleTypeSchemaExtractionTests {

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
        when(mockSharedPrefs.getString(anyString(), eq(null))).thenReturn("");
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
        when(mockApplication.getContentResolver()).thenReturn(mock(ContentResolver.class));
        when(mockSharedPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);

        sut = new AvoInspector("api key", mockApplication, AvoInspectorEnv.Dev);
    }

    @Test
    public void canExtractInt() {
        Map testMap = new HashMap();
        short sh = 1;
        byte bt = 2;
        testMap.put("v0", Integer.valueOf(3));
        testMap.put("v1", 4);
        testMap.put("v2", 5L);
        testMap.put("v3", Long.valueOf(6));
        testMap.put("v4", Short.valueOf("7"));
        testMap.put("v5", sh);
        testMap.put("v6", Byte.valueOf("8"));
        testMap.put("v7", bt);

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testMap);

        assertEquals(testMap.size(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.AvoInt(), value);
        }
    }

    @Test
    public void canExtractFloat() {
        Map testMap = new HashMap();

        testMap.put("v0", new Float(3));
        testMap.put("v1", 4f);
        testMap.put("v2", 5.0);
        testMap.put("v3", new Double(6));

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testMap);

        assertEquals(testMap.size(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.AvoFloat(), value);
        }
    }


    @Test
    public void canExtractBoolean() {
        Map testMap = new HashMap();

        testMap.put("v0", Boolean.TRUE);
        testMap.put("v1", false);

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testMap);

        assertEquals(testMap.size(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.AvoBoolean(), value);
        }
    }

    @Test
    public void canExtractString() {
        Map testMap = new HashMap();

        testMap.put("v0", "Str");
        testMap.put("v2", new Character('b'));

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testMap);

        assertEquals(testMap.size(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.AvoString(), value);
        }
    }

    @Test
    public void canExtractNull() {
        Map testMap = new HashMap();

        testMap.put("v0", null);
        testMap.put("v1", new AvoEventSchemaType.AvoNull());

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testMap);

        assertEquals(testMap.size(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.AvoNull(), value);
        }
    }

}
