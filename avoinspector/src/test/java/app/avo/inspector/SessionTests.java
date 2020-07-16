package app.avo.inspector;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class SessionTests {

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
    AvoSessionTracker mockSessionTracker;
    @Mock
    AvoBatcher mockBatcher;
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
        when(mockSharedPrefs.edit()).thenReturn(mockEditor);
        when(mockSharedPrefs.getString(anyString(), (String) eq(null))).thenReturn("");
        when(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
        when(mockApplication.getContentResolver()).thenReturn(mock(ContentResolver.class));
    }

    @Test
    public void startsSessionOnForeground() {
        ArgumentCaptor<Application.ActivityLifecycleCallbacks> activityLifecycleCallbackCaptor
                = ArgumentCaptor.forClass(Application.ActivityLifecycleCallbacks.class);

        sut = new AvoInspector("api key", mockApplication, AvoInspectorEnv.Dev);
        sut.sessionTracker = mockSessionTracker;

        verify(mockApplication).registerActivityLifecycleCallbacks(activityLifecycleCallbackCaptor.capture());

        activityLifecycleCallbackCaptor.getValue().onActivityStarted(mock(Activity.class));

        verify(mockSessionTracker).startOrProlongSession(anyLong());
    }

    @Test
    public void startSessionCalledOnEventSchemaTrack() {
        ArgumentCaptor<Long> timestampCaptor
                = ArgumentCaptor.forClass(Long.class);

        sut = new AvoInspector("api key", mockApplication, AvoInspectorEnv.Dev);
        sut.sessionTracker = mockSessionTracker;

        sut.trackSchema("Event name", new HashMap<String, AvoEventSchemaType>());

        verify(mockSessionTracker).startOrProlongSession(timestampCaptor.capture());

        Assert.assertTrue(timestampCaptor.getValue() > System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(1));
        Assert.assertTrue(timestampCaptor.getValue() <= System.currentTimeMillis());
    }

    @Test
    public void startSessionCalledOnEventSchemaTrackFromEventWithMap() {
        ArgumentCaptor<Long> timestampCaptor
                = ArgumentCaptor.forClass(Long.class);

        sut = new AvoInspector("api key", mockApplication, AvoInspectorEnv.Dev);
        sut.sessionTracker = mockSessionTracker;

        sut.trackSchemaFromEvent("Map name", new HashMap<String, AvoEventSchemaType>());

        verify(mockSessionTracker).startOrProlongSession(timestampCaptor.capture());

        Assert.assertTrue(timestampCaptor.getValue() > System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(1));
        Assert.assertTrue(timestampCaptor.getValue() <= System.currentTimeMillis());
    }

    @Test
    public void startSessionCalledOnEventSchemaTrackFromEventWithJsonObject() {
        ArgumentCaptor<Long> timestampCaptor
                = ArgumentCaptor.forClass(Long.class);

        sut = new AvoInspector("api key", mockApplication, AvoInspectorEnv.Dev);
        sut.sessionTracker = mockSessionTracker;

        sut.trackSchemaFromEvent("Map name", new JSONObject());

        verify(mockSessionTracker).startOrProlongSession(timestampCaptor.capture());

        Assert.assertTrue(timestampCaptor.getValue() > System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(1));
        Assert.assertTrue(timestampCaptor.getValue() <= System.currentTimeMillis());
    }

    @Test
    public void readsLastSessionTimestampFromPrefs() {
        when(mockSharedPrefs.getLong(anyString(), eq(-1L))).thenReturn(999L);

        AvoSessionTracker sut = new AvoSessionTracker(mockApplication, mockBatcher);

        Assert.assertEquals(999L, sut.lastSessionTimestamp);
    }

    @Test
    public void readsLastSessionIdFromPrefs() {
        when(mockSharedPrefs.getString(anyString(), (String) eq(null))).thenReturn("stored session id");

        new AvoSessionTracker(mockApplication, mockBatcher);

        Assert.assertEquals("stored session id", AvoSessionTracker.sessionId);
    }

    @Test
    public void createsSessionIdIfNothingStoredInPrefs() {
        when(mockSharedPrefs.getString(anyString(), (String) eq(null))).thenReturn(null);

        new AvoSessionTracker(mockApplication, mockBatcher);

        assertNotNull(AvoSessionTracker.sessionId);
        verify(mockEditor).putString(AvoSessionTracker.sessionIdKey, AvoSessionTracker.sessionId);
        verify(mockEditor).apply();
    }

    @Test
    public void createsSessionIdOnNewSession() {
        AvoSessionTracker sut = new AvoSessionTracker(mockApplication, mockBatcher);

        sut.startOrProlongSession(System.currentTimeMillis());

        assertNotNull(AvoSessionTracker.sessionId);
        verify(mockEditor).putString(AvoSessionTracker.sessionIdKey, AvoSessionTracker.sessionId);
        verify(mockEditor, times(2)).apply();
    }

    @Test
    public void sessionIsBatchedOnFirstSession() {
        AvoSessionTracker sut = new AvoSessionTracker(mockApplication, mockBatcher);

        sut.startOrProlongSession(System.currentTimeMillis());

        verify(mockBatcher).batchSessionStarted();

        verify(mockEditor, times(1)).putLong(eq(AvoSessionTracker.sessionStartKey), anyLong());
        verify(mockEditor, times(2)).apply();
    }

    @Test
    public void sessionIsBatchedOnceIfProlongedMultipleTimes() {
        AvoSessionTracker sut = new AvoSessionTracker(mockApplication, mockBatcher);

        long sessionMinutes = TimeUnit.MILLISECONDS.toMinutes(sut.sessionMillis);

        sut.startOrProlongSession(System.currentTimeMillis());
        sut.startOrProlongSession(System.currentTimeMillis()
                + TimeUnit.SECONDS.toMillis(sessionMinutes * 60 - 1));
        sut.startOrProlongSession(System.currentTimeMillis()
                + TimeUnit.SECONDS.toMillis(sessionMinutes * 60 - 1));
        sut.startOrProlongSession(System.currentTimeMillis()
                + TimeUnit.SECONDS.toMillis(sessionMinutes * 60 - 1));
        sut.startOrProlongSession(System.currentTimeMillis()
                + TimeUnit.SECONDS.toMillis(sessionMinutes * 60 - 1));

        verify(mockBatcher, times(1)).batchSessionStarted();
    }

    @Test
    public void twoSessionStartEventsWithDelayGreaterThanSessionLengthBatchTwoSessions() {
        AvoSessionTracker sut = new AvoSessionTracker(mockApplication, mockBatcher);

        long sessionMinutes = TimeUnit.MILLISECONDS.toMinutes(sut.sessionMillis);

        sut.startOrProlongSession(System.currentTimeMillis());
        sut.startOrProlongSession(System.currentTimeMillis()
                + TimeUnit.SECONDS.toMillis(sessionMinutes * 60 + 1));

        verify(mockBatcher, times(2)).batchSessionStarted();
        verify(mockEditor, times(2)).putLong(eq(AvoSessionTracker.sessionStartKey), anyLong());
        verify(mockEditor, times(4)).apply();
    }
}
