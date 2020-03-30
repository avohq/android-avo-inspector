package app.avo.avoinspectorexample;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import app.avo.inspector.AvoEventSchemaType;
import app.avo.inspector.AvoInspector;
import app.avo.inspector.AvoInspectorEnv;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AvoInspector avoInspector = new AvoInspector();

        avoInspector.enableLogging(true);

        avoInspector.init("apiKey", getApplicationContext(), AvoInspectorEnv.Dev);

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

        int i = 0;
    }
}
