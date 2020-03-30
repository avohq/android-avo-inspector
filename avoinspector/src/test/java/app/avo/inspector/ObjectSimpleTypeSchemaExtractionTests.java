package app.avo.inspector;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ObjectSimpleTypeSchemaExtractionTests {

    AvoInspector sut;

    @Before
    public void setUp() {
        sut = new AvoInspector("api key", Mockito.mock(Context.class), AvoInspectorEnv.Dev);
    }

    @Test
    public void canExtractInt() {
        IntsSchema event = new IntsSchema();

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(event);

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.Int(), value);
        }
    }

    @Test
    public void canExtractFloat() {
        FloatSchema event = new FloatSchema();

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(event);

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.Float(), value);
        }
    }


    @Test
    public void canExtractBoolean() {
        BooleanSchema event = new BooleanSchema();

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(event);

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.Boolean(), value);
        }
    }

    @Test
    public void canExtractString() {
        StringSchema event = new StringSchema();

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(event);

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.String(), value);
        }
    }

    @Test
    public void canExtractNull() {
        NullSchema event = new NullSchema();

        Map<String, AvoEventSchemaType> schema = sut.extractSchema(event);

        assertEquals(2, schema.size());

        for (String key: schema.keySet()) {
            AvoEventSchemaType value = schema.get(key);
            assertEquals(new AvoEventSchemaType.Null(), value);
        }
    }

    static class IntsSchema {
        short v0;
        byte v1;
        Integer v2;
        int v3;
        long v4;
        Long v5;
        Short v6;
        Byte v7;

        public IntsSchema() {
            this.v0 = 1;
            this.v1 = 2;
            this.v2 = 3;
            this.v3 = 4;
            this.v4 = 5;
            this.v5 = 6L;
            this.v6 = 7;
            this.v7 = 8;
        }
    }

    static class FloatSchema {
        Float v0;
        float v1;
        Double v2;
        double v3;

        public FloatSchema() {
            this.v0 = new Float(1);
            this.v1 = 2f;
            this.v2 = 3.0;
            this.v3 = new Double(4);
        }
    }

    static class BooleanSchema {
        Boolean v0;
        boolean v1;

        public BooleanSchema() {
            this.v0 = new Boolean(true);
            this.v1 = false;
        }
    }

    static class StringSchema {
        String v0;
        char v1;
        Character v2;

        public StringSchema() {
            this.v0 = "Str";
            this.v1 = 'a';
            this.v2 = 'b';
        }
    }

    static class NullSchema {
        String v0;
        AvoEventSchemaType v1;

        public NullSchema() {
            this.v0 = null;
            this.v1 = new AvoEventSchemaType.Null();
        }
    }
}
