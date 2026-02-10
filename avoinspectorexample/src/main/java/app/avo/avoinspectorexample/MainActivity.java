package app.avo.avoinspectorexample;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import com.segment.analytics.Analytics;
import com.segment.analytics.Middleware;
import com.segment.analytics.integrations.BasePayload;
import com.segment.analytics.integrations.TrackPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import app.avo.androidanalyticsdebugger.DebuggerManager;
import app.avo.inspector.AvoEventSchemaType;
import app.avo.inspector.AvoInspector;
import app.avo.inspector.AvoInspectorEnv;
import app.avo.inspector.VisualInspectorMode;

@SuppressWarnings("unused")
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        String apiKey = BuildConfig.AVO_API_KEY.isEmpty() ? "MYEfq8E4FZ6Xkxlo9mTc" : BuildConfig.AVO_API_KEY;
        final AvoInspector avoInspector = new AvoInspector(apiKey,
                getApplication(), AvoInspectorEnv.Dev, this,
                "024ec9c17ea2fb3e727d2815941eeb7d7c6e551536c9e2dde37fbbf0ffb9850579");

        findViewById(R.id.send_event_button).setOnClickListener(view1 -> {
            String eventName = ((TextView)findViewById(R.id.event_name_input)).getText().toString();

            String paramName0 = ((TextView)findViewById(R.id.param_name_input_0)).getText().toString();
            Object paramValue0 = parseValue(((TextView)findViewById(R.id.param_value_input_0)).getText().toString());

            String paramName1 = ((TextView)findViewById(R.id.param_name_input_1)).getText().toString();
            Object paramValue1 = parseValue(((TextView)findViewById(R.id.param_value_input_1)).getText().toString());

            String paramName2 = ((TextView)findViewById(R.id.param_name_input_2)).getText().toString();
            Object paramValue2 = parseValue(((TextView)findViewById(R.id.param_value_input_2)).getText().toString());

            String paramName3 = ((TextView)findViewById(R.id.param_name_input_3)).getText().toString();
            Object paramValue3 = parseValue(((TextView)findViewById(R.id.param_value_input_3)).getText().toString());

            String paramName4 = ((TextView)findViewById(R.id.param_name_input_4)).getText().toString();
            Object paramValue4 = parseValue(((TextView)findViewById(R.id.param_value_input_4)).getText().toString());

            String paramName5 = ((TextView)findViewById(R.id.param_name_input_5)).getText().toString();
            Object paramValue5 = parseValue(((TextView)findViewById(R.id.param_value_input_5)).getText().toString());

            Map<String, Object> params = new HashMap<>();
            params.put(paramName0, paramValue0);
            params.put(paramName1, paramValue1);
            params.put(paramName2, paramValue2);
            params.put(paramName3, paramValue3);
            params.put(paramName4, paramValue4);
            params.put(paramName5, paramValue5);

            avoInspector.trackSchemaFromEvent(eventName, params);
        });
    }


    @SuppressWarnings({"Convert2Lambda"})
    private void exampleUsage(final AvoInspector avoInspector) {
        AvoInspector.enableLogging(true);

        Middleware avoInspectorMiddleware = new Middleware() {
            @Override
            public void intercept(Chain chain) {

                BasePayload payload = chain.payload();

                if (payload.type() == BasePayload.Type.track) {
                    TrackPayload trackPayload = (TrackPayload) payload;
                    avoInspector.trackSchemaFromEvent(trackPayload.event(), trackPayload.properties());
                }

                chain.proceed(payload);
            }
        };
        Analytics analytics = new Analytics.Builder(getApplicationContext(), "SEGMENT_ANALYTICS_WRITE_KEY")
                .useSourceMiddleware(avoInspectorMiddleware)
                .build();

        avoInspector.trackSchemaFromEvent("Event name", new HashMap<String, Object>() {{
            put("String Prop", "Prop Value");
            put("Float Name", 1.0);
            put("Bool Name", true);
        }});

        Map<String, AvoEventSchemaType> eventSchema = new HashMap<>();
        eventSchema.put("userId", new AvoEventSchemaType.AvoInt());
        eventSchema.put("emailAddress", new AvoEventSchemaType.AvoString());
        eventSchema.put("key", new AvoEventSchemaType.AvoString());

        avoInspector.trackSchema("My event", eventSchema);

        avoInspector.trackSchema("Event name", new HashMap<String, AvoEventSchemaType>() {{
            put("String Prop", new AvoEventSchemaType.AvoString());
            put("Float Name", new AvoEventSchemaType.AvoFloat());
            put("Bool Name", new AvoEventSchemaType.AvoBoolean());
        }});

        Map<String, AvoEventSchemaType> schema = avoInspector.extractSchema(new HashMap<String, Object>() {{
            put("String Prop", "Prop Value");
            put("Float Name", 1.0);
            put("Bool Name", true);
        }});

        avoInspector.hideVisualInspector(this);

        avoInspector.showVisualInspector(this, VisualInspectorMode.BUBBLE);

        DebuggerManager visualInspector = (DebuggerManager) avoInspector.getVisualInspector();

        AvoInspector.setBatchSize(15);
        AvoInspector.setBatchFlushSeconds(10);
    }

    private Object parseValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        if (value.equals("nested")) {
            return new HashMap<String, Object>() {{
                put("nested0", "some string");
                put("nested1", -1);
                put("nested2", null);
                put("nested3", new HashMap<String, Object>() {{
                    put("nestedNested0", "str");
                    put("nestedNested1", 2.3);
                    put("2xnested", new HashMap<String, Object>() {{
                        put("nestedNested0", "str");
                        put("nestedNested1", 2.3);
                    }});
                }});
            }};
        }

        if (value.equals("list")) {
            return new ArrayList<Object>() {{
                add("some string");
                add(-1);
                add(null);
                add(new HashMap<String, Object>() {{
                    put("nestedNested0", "str");
                    put("nestedNested1", 2.3);
                    put("2xnested", new HashMap<String, Object>() {{
                        put("nestedNested0", "str");
                        put("nestedNested1", 2.3);
                    }});
                }});
            }};
        }

        if (isNumeric(value)) {
            if (!value.contains(".")) {
                return Integer.parseInt(value);
            } else if (value.contains("f")) {
                return Float.parseFloat(value);
            } else return Double.parseDouble(value);
        } else if (value.equalsIgnoreCase("true")) {
            return true;
        } else if (value.equalsIgnoreCase("false")) {
            return false;
        } else if (value.equalsIgnoreCase("null")) {
            return null;
        }

        return value;
    }

    private static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch(NumberFormatException e){
            return false;
        }
    }
}
