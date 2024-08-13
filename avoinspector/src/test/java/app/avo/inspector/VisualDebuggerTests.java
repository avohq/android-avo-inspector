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
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.avo.androidanalyticsdebugger.DebuggerManager;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class VisualDebuggerTests {

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
	@Mock
	DebuggerManager mockDebugger;

	private final Map<String, Map<String, Number>> testMap = new ConcurrentHashMap<>();
	private final Map<String, AvoEventSchemaType> testSchema = new HashMap<>();

	@Before
	public void setUp() throws Exception {
		MockitoAnnotations.initMocks(this);

		when(mockApplication.getPackageManager()).thenReturn(mockPackageManager);
		when(mockApplication.getPackageName()).thenReturn("");
		when(mockPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mockPackageInfo);
		when(mockApplication.getApplicationInfo()).thenReturn(mockApplicationInfo);
		when(mockApplication.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPrefs);
		when(mockSharedPrefs.edit()).thenReturn(mockEditor);
		when(mockSharedPrefs.getString(anyString(), eq(null))).thenReturn("");
		when(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor);
		when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
		when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
		when(mockApplication.getContentResolver()).thenReturn(mock(ContentResolver.class));

		AvoDeduplicator.clearEvents();

		Map<String, Number> nestedMap = new ConcurrentHashMap<>();
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

		testSchema.put("nested", new AvoEventSchemaType.AvoObject(new HashMap<String, AvoEventSchemaType>(){
			{
				put("v0", new AvoEventSchemaType.AvoInt());
				put("v1", new AvoEventSchemaType.AvoInt());
				put("v2", new AvoEventSchemaType.AvoInt());
				put("v3", new AvoEventSchemaType.AvoInt());
				put("v4", new AvoEventSchemaType.AvoInt());
				put("v5", new AvoEventSchemaType.AvoInt());
				put("v6", new AvoEventSchemaType.AvoInt());
				put("v7", new AvoEventSchemaType.AvoInt());
			}
		}));
	}

	@Test
	public void visualInspectorIsInitializedInDevAndStagingAndNotInProd() {
		AvoInspector avoInspector = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);
		assertNotNull(avoInspector.visualInspector.debugger);

		avoInspector = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Staging);
		assertNotNull(avoInspector.visualInspector.debugger);

		avoInspector = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Prod);
		assertNull(avoInspector.visualInspector.debugger);
	}

	@Test
	public void avoFunctionIntegrationCallsVisualInspector() {
		AvoInspector avoInspector = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);
		avoInspector.visualInspector.debugger = mockDebugger;

		avoInspector.avoFunctionTrackSchemaFromEvent("Test", testMap, "eventId", "eventHash");

		verify(mockDebugger).publishEvent(Mockito.anyLong(), Mockito.eq("Event: Test"), Mockito.any(List.class),
				Mockito.any(List.class));
	}

	@Test
	public void manualTrackingCallsVisualInspector() {
		AvoInspector avoInspector = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);
		avoInspector.visualInspector.debugger = mockDebugger;

		avoInspector.trackSchemaFromEvent("Test", testMap);

		verify(mockDebugger).publishEvent(Mockito.anyLong(), Mockito.eq("Event: Test"), Mockito.any(List.class),
				Mockito.any(List.class));
	}

}
