package app.avo.avoinspectorexample;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import app.avo.avoinspectorexample.databinding.ActivityMainBinding;
import app.avo.inspector.AvoEventSchemaType;
import app.avo.inspector.AvoInspector;
import app.avo.inspector.AvoInspectorEnv;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        final AvoInspector avoInspector = new AvoInspector("apiKey", getApplication(), AvoInspectorEnv.Dev);

        avoInspector.enableLogging(true);

        Map testMap = new HashMap();
        testMap.put("asdfa", true);
        testMap.put(false, null);
        testMap.put("hhhhh", new Double(5));

        JSONObject testJsonObj = new JSONObject();
        try {
            testJsonObj.put("asdfa", true);
            testJsonObj.put("false", "null");
            testJsonObj.put("ssd", new Double(5));
            testJsonObj.put("hhhhh", 5);
            testJsonObj.put("alist", new String[10]);
            testJsonObj.put("ilist", new Integer[10]);
            testJsonObj.put("blist", new Boolean[10]);
            testJsonObj.put("dlist", new Double[10]);
            testJsonObj.put("flist", new Float[10]);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Map<String, AvoEventSchemaType> schema = avoInspector.extractSchema(testMap);

        binding.sendEventButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String eventName = binding.eventNameInput.getText().toString();

                String paramName = binding.paramNameInput.getText().toString();
                String paramValue = binding.paramValueInput.getText().toString();

                Map<String, String> params = new HashMap<>();
                params.put(paramName, paramValue);

                avoInspector.trackSchemaFromEvent(eventName, params);
            }
        });
    }
}
