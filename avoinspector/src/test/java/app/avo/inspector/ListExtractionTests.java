package app.avo.inspector;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ListExtractionTests {

    AvoInspector sut;

    @Before
    public void setUp() {
        sut = new AvoInspector("api key", Mockito.mock(Context.class), AvoInspectorEnv.Dev);
    }

    @Test
    public void canExtractJSONArrayOfInts() {
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
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(testJsonObj);

        assertEquals(testJsonObj.length(), schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            AvoEventSchemaType.List expected = new AvoEventSchemaType.List(new HashSet<AvoEventSchemaType>());
            expected.subtypes.add(new AvoEventSchemaType.Int());
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
            AvoEventSchemaType.List expected = new AvoEventSchemaType.List(new HashSet<AvoEventSchemaType>());
            expected.subtypes.add(new AvoEventSchemaType.Int());
            expected.subtypes.add(new AvoEventSchemaType.String());
            assertEquals(expected, value);
        }
    }
}
