package app.avo.avoinspectorexample;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import com.segment.analytics.Analytics;
import com.segment.analytics.Middleware;
import com.segment.analytics.integrations.BasePayload;
import com.segment.analytics.integrations.TrackPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import app.avo.androidanalyticsdebugger.DebuggerManager;
import app.avo.androidanalyticsdebugger.DebuggerMode;
import app.avo.avoinspectorexample.databinding.ActivityMainBinding;
import app.avo.inspector.AvoEventSchemaType;
import app.avo.inspector.AvoInspector;
import app.avo.inspector.AvoInspectorEnv;

@SuppressWarnings("ALL")
public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        final AvoInspector avoInspector = new AvoInspector("MYEfq8E4FZ6Xkxlo9mTc",
                getApplication(), AvoInspectorEnv.Dev, this);

	    binding.sendEventButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String eventName = binding.eventNameInput.getText().toString();

                String paramName0 = binding.paramNameInput0.getText().toString();
                Object paramValue0 = parseValue(binding.paramValueInput0.getText().toString());

                String paramName1 = binding.paramNameInput1.getText().toString();
                Object paramValue1 = parseValue(binding.paramValueInput1.getText().toString());

                String paramName2 = binding.paramNameInput2.getText().toString();
                Object paramValue2 = parseValue(binding.paramValueInput2.getText().toString());

                String paramName3 = binding.paramNameInput3.getText().toString();
                Object paramValue3 = parseValue(binding.paramValueInput3.getText().toString());

                String paramName4 = binding.paramNameInput4.getText().toString();
                Object paramValue4 = parseValue(binding.paramValueInput4.getText().toString());

                String paramName5 = binding.paramNameInput5.getText().toString();
                Object paramValue5 = parseValue(binding.paramValueInput5.getText().toString());

                Map<String, Object> params = new HashMap<>();
                params.put(paramName0, paramValue0);
                params.put(paramName1, paramValue1);
                params.put(paramName2, paramValue2);
                params.put(paramName3, paramValue3);
                params.put(paramName4, paramValue4);
                params.put(paramName5, paramValue5);

                avoInspector.trackSchemaFromEvent(eventName, params);
            }
        });
    }

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
		        .middleware(avoInspectorMiddleware)
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

		avoInspector.showVisualInspector(this, DebuggerMode.bubble);

		DebuggerManager visualInspector = avoInspector.getVisualInspector();

		AvoInspector.setBatchSize(15);
		AvoInspector.setBatchFlushSeconds(10);
	}

	private Object parseValue(String value) {
        if (value == null || value.equals("")) {
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
        } else if (value.toLowerCase().equals("true")) {
            return true;
        } else if (value.toLowerCase().equals("false")) {
            return false;
        } else if (value.toLowerCase().equals("null")) {
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
