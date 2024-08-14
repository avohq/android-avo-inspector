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
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeduplicatorTests {

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
	public void testMapsEqual() {
		Map<String, Object> firstMap = new HashMap<>();
		firstMap.put("0", "same string");
		firstMap.put("1", new int[]{1,2,3});
		List<Object> firstArrayList = new ArrayList<>();
		firstArrayList.add("1");
		firstArrayList.add(2);
		firstArrayList.add(3d);
		List<Object> firstInnerArrayList = new ArrayList<>();
		firstInnerArrayList.add("hm");
		firstInnerArrayList.add(new boolean[]{});
		firstArrayList.add(firstInnerArrayList);
		firstMap.put("2", firstArrayList);
		Map<String, Object> firstInnerMap = new HashMap<>();
		firstInnerMap.put("avo", new float[]{1f,2f,3f});
		firstMap.put("3", firstInnerMap);

		Map<String, Object> secondMap = new HashMap<>();
		secondMap.put("0", "same string");
		secondMap.put("1", new int[]{1,2,3});
		List<Object> secondArrayList = new ArrayList<>();
		secondArrayList.add("1");
		secondArrayList.add(2);
		secondArrayList.add(3d);
		List<Object> secondInnerArrayList = new ArrayList<>();
		secondInnerArrayList.add("hm");
		secondInnerArrayList.add(new boolean[]{});
		secondArrayList.add(secondInnerArrayList);
		secondMap.put("2", secondArrayList);
		Map<String, Object> secondInnerMap = new HashMap<>();
		secondInnerMap.put("avo", new float[]{1f,2f,3f});
		secondMap.put("3", secondInnerMap);

		assertTrue(Util.mapsEqual(firstMap, secondMap));
	}

	@Test
	public void detectsDuplicationsWhenTrackInAvoAndThenManually() {
		boolean shouldRegisterFromAvo = AvoDeduplicator.shouldRegisterEvent("Test", testMap, true);
		boolean shouldRegisterManual = AvoDeduplicator.shouldRegisterEvent("Test", testMap, false);

		assertTrue(shouldRegisterFromAvo);
		assertFalse(shouldRegisterManual);
	}

	@Test
	public void detectsDuplicationsWhenTrackInAvoAndThenSchemaManually() {
		boolean shouldRegisterFromAvo = AvoDeduplicator.shouldRegisterEvent("Test", testMap, true);
		boolean shouldRegisterManualSchema = AvoDeduplicator.shouldRegisterSchemaFromManually("Test", testSchema);

		assertTrue(shouldRegisterFromAvo);
		assertFalse(shouldRegisterManualSchema);
	}

	@Test
	public void detectsDuplicationsWhenTrackManuallyAndThenInAvo() {
		boolean shouldRegisterManual = AvoDeduplicator.shouldRegisterEvent("Test", testMap, false);
		boolean shouldRegisterFromAvo = AvoDeduplicator.shouldRegisterEvent("Test", testMap, true);

		assertTrue(shouldRegisterManual);
		assertFalse(shouldRegisterFromAvo);
	}

	@Test
	public void inspectorDeduplicatesOnlyOneEventWhenTrackManuallyInAvoAndThenManually() {
		AvoInspector avoInspector = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

		Map<String, AvoEventSchemaType> manuallyTrackedSchema = avoInspector.trackSchemaFromEvent("Test", testMap);
		Map<String, AvoEventSchemaType> avoTrackedSchema = avoInspector.avoFunctionTrackSchemaFromEvent("Test", testMap, "eventId", "eventHash");
		Map<String, AvoEventSchemaType> manuallyTrackedSchemaAgain = avoInspector.trackSchemaFromEvent("Test", testMap);

		assertFalse(manuallyTrackedSchema.isEmpty());
		assertTrue(avoTrackedSchema.isEmpty());
		assertFalse(manuallyTrackedSchemaAgain.isEmpty());
	}

	@Test
	public void inspectorDeduplicatesOnlyOneEventWhenTrackInAvoManuallyAndThenInAvo() {
		AvoInspector avoInspector = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

		Map<String, AvoEventSchemaType> avoTrackedSchema = avoInspector.avoFunctionTrackSchemaFromEvent("Test", testMap, "eventId", "eventHash");
		Map<String, AvoEventSchemaType> manuallyTrackedSchema = avoInspector.trackSchemaFromEvent("Test", testMap);
		Map<String, AvoEventSchemaType> avoTrackedSchemaAgain = avoInspector.avoFunctionTrackSchemaFromEvent("Test", testMap, "eventId", "eventHash");

		assertFalse(avoTrackedSchema.isEmpty());
		assertTrue(manuallyTrackedSchema.isEmpty());
		assertFalse(avoTrackedSchemaAgain.isEmpty());
	}

	@Test
	public void allowsTwoSameManualEventsInARow() {
		boolean shouldRegisterManual = AvoDeduplicator.shouldRegisterEvent("Test", testMap, false);
		boolean shouldRegisterManualAgain = AvoDeduplicator.shouldRegisterEvent("Test", testMap, false);

		assertTrue(shouldRegisterManual);
		assertTrue(shouldRegisterManualAgain);
	}

	@Test
	public void allowsTwoSameAvoFunctionsEventsInARow() {
		boolean shouldRegisterAvoFunction = AvoDeduplicator.shouldRegisterEvent("Test", testMap, true);
		boolean shouldRegisterAvoFunctionAgain = AvoDeduplicator.shouldRegisterEvent("Test", testMap, true);

		assertTrue(shouldRegisterAvoFunction);
		assertTrue(shouldRegisterAvoFunctionAgain);
	}

	@Test
	public void doesNotDeduplicateIfMoreThan300msPass() throws InterruptedException {
		boolean shouldRegisterFromAvo = AvoDeduplicator.shouldRegisterEvent("Test", testMap, true);
		TimeUnit.MILLISECONDS.sleep(301);
		boolean shouldRegisterManual = AvoDeduplicator.shouldRegisterEvent("Test", testMap, false);

		assertTrue(shouldRegisterFromAvo);
		assertTrue(shouldRegisterManual);
	}
}
