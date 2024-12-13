package app.avo.inspector;

import android.app.Application;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.segment.analytics.Properties;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class NestedObjectExtractionTest {


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

        mockPackageInfo.versionName = "myVersion";

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
    public void extractsSegmentPayload() {
        Properties segmentProps = new Properties().putValue("key",
                new Properties().putValue("nested", "str"));

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(segmentProps);

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            Map expected = new HashMap<String,
                    AvoEventSchemaType>();

            AvoEventSchemaType.AvoObject nestedObj = new AvoEventSchemaType.AvoObject(expected);
            expected.put("nested", new AvoEventSchemaType.AvoString());

            assertEquals(new AvoEventSchemaType.AvoObject(expected), value);
        }
    }

    @Test
    public void canExtractNestedInt() {
        Map testMap = new ConcurrentHashMap();

        Map nestedMap = new ConcurrentHashMap();
        short sh = 1;
        byte bt = 2;
        nestedMap.put("v0", Integer.valueOf(3));
        nestedMap.put("v1", 4);
        nestedMap.put("v2", 5L);
        nestedMap.put("v3", Long.valueOf(6));
        nestedMap.put("v4", Short.valueOf("7"));
        nestedMap.put("v5", sh);
        nestedMap.put("v6", Byte.valueOf("8"));
        nestedMap.put("v7", bt);

        testMap.put("nested", nestedMap);

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testMap);

        assertEquals(testMap.size(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            Map expected = new HashMap<String,
                    AvoEventSchemaType>();
            expected.put("v0", new AvoEventSchemaType.AvoInt());
            expected.put("v1", new AvoEventSchemaType.AvoInt());
            expected.put("v2", new AvoEventSchemaType.AvoInt());
            expected.put("v3", new AvoEventSchemaType.AvoInt());
            expected.put("v4", new AvoEventSchemaType.AvoInt());
            expected.put("v5", new AvoEventSchemaType.AvoInt());
            expected.put("v6", new AvoEventSchemaType.AvoInt());
            expected.put("v7", new AvoEventSchemaType.AvoInt());
            assertEquals(new AvoEventSchemaType.AvoObject(expected), value);
        }
    }

    @Test
    public void canExtractDoubleNestedInt() {
        Map testMap = new HashMap();
        Map nestedMap = new HashMap();
        testMap.put("nested", nestedMap);
        Map doubleNestedMap = new HashMap();
        short sh = 1;
        byte bt = 2;
        doubleNestedMap.put("v0", Integer.valueOf(3));
        doubleNestedMap.put("v1", 4);
        doubleNestedMap.put("v2", 5L);
        doubleNestedMap.put("v3", Long.valueOf(6));
        doubleNestedMap.put("v4", Short.valueOf("7"));
        doubleNestedMap.put("v5", sh);
        doubleNestedMap.put("v6", Byte.valueOf("8"));
        doubleNestedMap.put("v7", bt);

        nestedMap.put("double_nested", doubleNestedMap);

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testMap);

        assertEquals(testMap.size(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            Map expected = new HashMap<String,
                    AvoEventSchemaType>();
            Map nestedExpected = new HashMap<String,
                    AvoEventSchemaType>();
            nestedExpected.put("v0", new AvoEventSchemaType.AvoInt());
            nestedExpected.put("v1", new AvoEventSchemaType.AvoInt());
            nestedExpected.put("v2", new AvoEventSchemaType.AvoInt());
            nestedExpected.put("v3", new AvoEventSchemaType.AvoInt());
            nestedExpected.put("v4", new AvoEventSchemaType.AvoInt());
            nestedExpected.put("v5", new AvoEventSchemaType.AvoInt());
            nestedExpected.put("v6", new AvoEventSchemaType.AvoInt());
            nestedExpected.put("v7", new AvoEventSchemaType.AvoInt());

            AvoEventSchemaType.AvoObject nestedObj = new AvoEventSchemaType.AvoObject(nestedExpected);
            expected.put("double_nested", nestedObj);

            assertEquals(new AvoEventSchemaType.AvoObject(expected), value);
        }
    }
}
