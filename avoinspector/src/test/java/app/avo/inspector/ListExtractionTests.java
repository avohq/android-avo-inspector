package app.avo.inspector;

import android.app.Application;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ListExtractionTests {

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
        when(mockSharedPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
        when(mockApplication.getContentResolver()).thenReturn(mock(ContentResolver.class));

        sut = new AvoInspector("api key", mockApplication, AvoInspectorEnv.Dev);
    }

    @Test
    public void canExtractJSONArrayOfIntsAndStrings() {
        JSONObject testJsonObj = new JSONObject();
        try {
            JSONArray items = new JSONArray();
            testJsonObj.put("list_key", items);

            short sh = 1;
            byte bt = 2;
            items.put(new Integer(3));
            items.put(4);
            items.put(5L);
            items.put(new Long(6));
            items.put( new Short("7"));
            items.put(sh);
            items.put(new Byte("8"));
            items.put(bt);
            items.put("Str");
            items.put(new Character('b'));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testJsonObj);

        assertEquals(testJsonObj.length(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            AvoEventSchemaType.AvoList expected = new AvoEventSchemaType.AvoList(new HashSet<AvoEventSchemaType>());
            expected.subtypes.add(new AvoEventSchemaType.AvoInt());
            expected.subtypes.add(new AvoEventSchemaType.AvoString());
            assertEquals(expected, value);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void canExtractListOfIntsAndStrings() {
        JSONObject testJsonObj = new JSONObject();
        try {
            List items = new ArrayList();
            testJsonObj.put("list_key", items);

            short sh = 1;
            byte bt = 2;
            items.add(new Integer(3));
            items.add(4);
            items.add(5L);
            items.add(new Long(6));
            items.add( new Short("7"));
            items.add(sh);
            items.add(new Byte("8"));
            items.add(bt);
            items.add("Str");
            items.add(new Character('b'));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testJsonObj);

        assertEquals(testJsonObj.length(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            AvoEventSchemaType.AvoList expected = new AvoEventSchemaType.AvoList(new HashSet<AvoEventSchemaType>());
            expected.subtypes.add(new AvoEventSchemaType.AvoInt());
            expected.subtypes.add(new AvoEventSchemaType.AvoString());
            assertEquals(expected, value);
        }
    }

    @Test
    public void canExtractArrayOfInts() {
        JSONObject testJsonObj = new JSONObject();
        try {
            int[] items = new int[3];
            testJsonObj.put("array_key", items);

            items[0] = new Integer(3);
            items[1] = (short)1;
            items[2] = 5;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testJsonObj);

        assertEquals(testJsonObj.length(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            AvoEventSchemaType.AvoList expected = new AvoEventSchemaType.AvoList(new HashSet<AvoEventSchemaType>());
            expected.subtypes.add(new AvoEventSchemaType.AvoInt());
            assertEquals(expected, value);
        }
    }

    @Test
    public void canExtractArrayOfStrings() {
        JSONObject testJsonObj = new JSONObject();
        try {
            String[] items = new String[3];
            testJsonObj.put("array_key", items);

            items[0] = "3";
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testJsonObj);

        assertEquals(testJsonObj.length(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            AvoEventSchemaType.AvoList expected = new AvoEventSchemaType.AvoList(new HashSet<AvoEventSchemaType>());
            expected.subtypes.add(new AvoEventSchemaType.AvoString());
            expected.subtypes.add(new AvoEventSchemaType.AvoNull());
            assertEquals(expected, value);
        }
    }

    @Test
    public void canExtractArrayOfFloats() {
        JSONObject testJsonObj = new JSONObject();
        try {
            Double[] items = new Double[3];
            testJsonObj.put("array_key", items);

            items[0] = 3.0;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testJsonObj);

        assertEquals(testJsonObj.length(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            AvoEventSchemaType.AvoList expected = new AvoEventSchemaType.AvoList(new HashSet<AvoEventSchemaType>());
            expected.subtypes.add(new AvoEventSchemaType.AvoFloat());
            expected.subtypes.add(new AvoEventSchemaType.AvoNull());
            assertEquals(expected, value);
        }
    }

    @Test
    public void canExtractArrayOfBooleans() {
        JSONObject testJsonObj = new JSONObject();
        try {
            boolean[] items = new boolean[3];
            testJsonObj.put("array_key", items);

            items[0] = true;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testJsonObj);

        assertEquals(testJsonObj.length(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            AvoEventSchemaType.AvoList expected = new AvoEventSchemaType.AvoList(new HashSet<AvoEventSchemaType>());
            expected.subtypes.add(new AvoEventSchemaType.AvoBoolean());
            assertEquals(expected, value);
        }
    }

    @Test
    public void canExtractArrayOfLists() {
        JSONObject testJsonObj = new JSONObject();
        try {
            List[] items = new List[3];
            testJsonObj.put("array_key", items);

            items[0] = new ArrayList();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testJsonObj);

        assertEquals(testJsonObj.length(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            AvoEventSchemaType.AvoList expected = new AvoEventSchemaType.AvoList(new HashSet<AvoEventSchemaType>());
            expected.subtypes.add(new AvoEventSchemaType.AvoList(new HashSet<AvoEventSchemaType>()));
            expected.subtypes.add(new AvoEventSchemaType.AvoNull());
            assertEquals(expected, value);
        }
    }

    @Test
    public void canExtractArrayOfObjects() {
        JSONObject testJsonObj = new JSONObject();
        try {
            Map[] items = new Map[3];
            testJsonObj.put("array_key", items);

            items[0] = new HashMap();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testJsonObj);

        assertEquals(testJsonObj.length(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            AvoEventSchemaType.AvoList expected = new AvoEventSchemaType.AvoList(new HashSet<AvoEventSchemaType>());
            expected.subtypes.add(new AvoEventSchemaType.AvoObject(new HashMap<String, AvoEventSchemaType>()));
            expected.subtypes.add(new AvoEventSchemaType.AvoNull());
            assertEquals(expected, value);
        }
    }
}
