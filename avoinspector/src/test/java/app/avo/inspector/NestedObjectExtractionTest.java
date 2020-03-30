package app.avo.inspector;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("unchecked")
public class NestedObjectExtractionTest {


    AvoInspector sut;

    @Before
    public void setUp() {
        sut = new AvoInspector("api key", Mockito.mock(Context.class), AvoInspectorEnv.Dev);
    }

    @Test
    public void canExtractNestedInt() {
        Map testMap = new HashMap();

        Map nestedMap = new HashMap();
        short sh = 1;
        byte bt = 2;
        nestedMap.put("v0", new Integer(3));
        nestedMap.put("v1", 4);
        nestedMap.put("v2", 5L);
        nestedMap.put("v3", new Long(6));
        nestedMap.put("v4", new Short("7"));
        nestedMap.put("v5", sh);
        nestedMap.put("v6", new Byte("8"));
        nestedMap.put("v7", bt);

        testMap.put("nested", nestedMap);

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testMap);

        assertEquals(testMap.size(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            Map expected = new HashMap<String,
                    AvoEventSchemaType>();
            expected.put("v0", new AvoEventSchemaType.Int());
            expected.put("v1", new AvoEventSchemaType.Int());
            expected.put("v2", new AvoEventSchemaType.Int());
            expected.put("v3", new AvoEventSchemaType.Int());
            expected.put("v4", new AvoEventSchemaType.Int());
            expected.put("v5", new AvoEventSchemaType.Int());
            expected.put("v6", new AvoEventSchemaType.Int());
            expected.put("v7", new AvoEventSchemaType.Int());
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
        doubleNestedMap.put("v0", new Integer(3));
        doubleNestedMap.put("v1", 4);
        doubleNestedMap.put("v2", 5L);
        doubleNestedMap.put("v3", new Long(6));
        doubleNestedMap.put("v4", new Short("7"));
        doubleNestedMap.put("v5", sh);
        doubleNestedMap.put("v6", new Byte("8"));
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
            nestedExpected.put("v0", new AvoEventSchemaType.Int());
            nestedExpected.put("v1", new AvoEventSchemaType.Int());
            nestedExpected.put("v2", new AvoEventSchemaType.Int());
            nestedExpected.put("v3", new AvoEventSchemaType.Int());
            nestedExpected.put("v4", new AvoEventSchemaType.Int());
            nestedExpected.put("v5", new AvoEventSchemaType.Int());
            nestedExpected.put("v6", new AvoEventSchemaType.Int());
            nestedExpected.put("v7", new AvoEventSchemaType.Int());

            AvoEventSchemaType.AvoObject nestedObj = new AvoEventSchemaType.AvoObject(nestedExpected);
            expected.put("double_nested", nestedObj);

            assertEquals(new AvoEventSchemaType.AvoObject(expected), value);
        }
    }
}
