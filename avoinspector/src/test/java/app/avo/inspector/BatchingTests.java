package app.avo.inspector;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;

import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BatchingTests {

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
    AvoNetworkCallsHandler mockNetworkCallsHandler;
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
        when(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        when(mockEditor.remove(anyString())).thenReturn(mockEditor);
    }

    @Test
    public void callsBatcherForegroundOnceOnSubsequentActivityStarts() {
        ArgumentCaptor<Application.ActivityLifecycleCallbacks> activityLifecycleCallbackCaptor
                = ArgumentCaptor.forClass(Application.ActivityLifecycleCallbacks.class);

        AvoInspector sut = new AvoInspector("api key", mockApplication, AvoInspectorEnv.Dev);
        sut.avoBatcher = mockBatcher;

        verify(mockApplication).registerActivityLifecycleCallbacks(activityLifecycleCallbackCaptor.capture());

        activityLifecycleCallbackCaptor.getValue().onActivityStarted(mock(Activity.class));
        activityLifecycleCallbackCaptor.getValue().onActivityStarted(mock(Activity.class));

        verify(mockBatcher, times(1)).enterForeground();
    }

    @Test
    public void callsBatcherForegroundAndBatcherBackgroundAndBatcherForegroundOnAppForegroundAndBackgroundAndForeground() {
        ArgumentCaptor<Application.ActivityLifecycleCallbacks> activityLifecycleCallbackCaptor
                = ArgumentCaptor.forClass(Application.ActivityLifecycleCallbacks.class);
        ArgumentCaptor<ComponentCallbacks2> componentCallbackCaptor
                = ArgumentCaptor.forClass(ComponentCallbacks2.class);

        AvoInspector sut = new AvoInspector("api key", mockApplication, AvoInspectorEnv.Dev);
        sut.avoBatcher = mockBatcher;

        verify(mockApplication).registerActivityLifecycleCallbacks(activityLifecycleCallbackCaptor.capture());
        verify(mockApplication).registerComponentCallbacks(componentCallbackCaptor.capture());

        activityLifecycleCallbackCaptor.getValue().onActivityStarted(mock(Activity.class));
        componentCallbackCaptor.getValue().onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
        activityLifecycleCallbackCaptor.getValue().onActivityStarted(mock(Activity.class));

        verify(mockBatcher, times(2)).enterForeground();
        verify(mockBatcher, times(1)).enterBackground();
    }

    @Test
    public void saveUpTo1000EventsOnBackgroundAndRestoresOnForeground() throws InterruptedException {
        ArgumentCaptor<String> stringCaptor
                = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Runnable> runnableCaptor
                = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<AvoNetworkCallsHandler.Callback> networkCallbackCaptor
                = ArgumentCaptor.forClass(AvoNetworkCallsHandler.Callback.class);
        ArgumentCaptor<List<Map<String, String>>> listCaptor
                = ArgumentCaptor.forClass(List.class);

        AvoBatcher sut = new AvoBatcher(mockApplication, mockNetworkCallsHandler);

        sut.mainHandler = mock(Handler.class);

        for (int i = 0; i < 1005; i++) {
            Map<String, String> event = new HashMap<>();
            event.put("type", "test");
            sut.events.add(event);
        }


        // When
        sut.enterBackground();

        Thread.sleep(500);

        verify(mockEditor).putString(eq(AvoBatcher.avoInspectorBatchKey), stringCaptor.capture());

        // Given
        when(mockSharedPrefs.getString(AvoBatcher.avoInspectorBatchKey, null))
                .thenReturn(stringCaptor.getValue());

        // When
        sut.enterForeground();

        Thread.sleep(500);

        verify(sut.mainHandler).post(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        Thread.sleep(500);

        // Then
        verify(mockNetworkCallsHandler).reportInspectorWithBatchBody(listCaptor.capture(),
                networkCallbackCaptor.capture());

        // When
        networkCallbackCaptor.getValue().call(null);

        // Then
        assertEquals(1000, listCaptor.getValue().size());

        verify(mockEditor).remove(anyString());
        verify(mockEditor, times(2)).apply();
    }

    @Test
    public void clearsCacheOnForegroundWithoutTypedEvents() throws InterruptedException {
        ArgumentCaptor<Runnable> runnableCaptor
                = ArgumentCaptor.forClass(Runnable.class);

        AvoBatcher sut = new AvoBatcher(mockApplication, mockNetworkCallsHandler);

        sut.mainHandler = mock(Handler.class);

        for (int i = 0; i < AvoBatcher.batchSize; i++) {
            Map<String, String> event = new HashMap<>();
            event.put("no-type", "test");
            sut.events.add(event);
        }

        when(mockSharedPrefs.getString(AvoBatcher.avoInspectorBatchKey, null))
                .thenReturn(new JSONArray(sut.events).toString());

        // When
        sut.enterForeground();

        Thread.sleep(500);

        verify(sut.mainHandler).post(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        Thread.sleep(500);

        // Then
        verify(mockNetworkCallsHandler, never()).reportInspectorWithBatchBody(ArgumentMatchers.<Map<String, String>>anyList(),
                any(AvoNetworkCallsHandler.Callback.class));
        assertEquals(0, sut.events.size());

        verify(mockEditor).remove(anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void restoresEventsOnFailedNetworkPost() throws InterruptedException {
        ArgumentCaptor<Runnable> runnableCaptor
                = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<AvoNetworkCallsHandler.Callback> networkCallbackCaptor
                = ArgumentCaptor.forClass(AvoNetworkCallsHandler.Callback.class);
        ArgumentCaptor<List<Map<String, String>>> listCaptor
                = ArgumentCaptor.forClass(List.class);

        AvoBatcher sut = new AvoBatcher(mockApplication, mockNetworkCallsHandler);

        sut.mainHandler = mock(Handler.class);

        for (int i = 0; i < AvoBatcher.batchSize; i++) {
            Map<String, String> event = new HashMap<>();
            event.put("type", "test");
            sut.events.add(event);
        }

        // When
        sut.checkIfBatchNeedsToBeSent();

        verify(sut.mainHandler).post(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        Thread.sleep(500);

        // Then
        verify(mockNetworkCallsHandler).reportInspectorWithBatchBody(listCaptor.capture(),
                networkCallbackCaptor.capture());

        // Then
        assertEquals(0, sut.events.size());

        // When
        networkCallbackCaptor.getValue().call("Failed");

        assertEquals(AvoBatcher.batchSize, sut.events.size());
    }

    @Test
    public void clearsEventsOnSuccessNetworkPost() throws InterruptedException {
        ArgumentCaptor<Runnable> runnableCaptor
                = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<AvoNetworkCallsHandler.Callback> networkCallbackCaptor
                = ArgumentCaptor.forClass(AvoNetworkCallsHandler.Callback.class);
        ArgumentCaptor<List<Map<String, String>>> listCaptor
                = ArgumentCaptor.forClass(List.class);

        AvoBatcher sut = new AvoBatcher(mockApplication, mockNetworkCallsHandler);

        sut.mainHandler = mock(Handler.class);
        sut.batchFlushAttemptMillis = 0;

        for (int i = 0; i < AvoBatcher.batchSize; i++) {
            Map<String, String> event = new HashMap<>();
            event.put("type", "test");
            sut.events.add(event);
        }

        // When
        sut.checkIfBatchNeedsToBeSent();

        verify(sut.mainHandler).post(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        Thread.sleep(500);

        verify(mockNetworkCallsHandler).reportInspectorWithBatchBody(listCaptor.capture(),
                networkCallbackCaptor.capture());

        // Then
        assertEquals(0, sut.events.size());

        // When
        networkCallbackCaptor.getValue().call(null);

        // Then
        assertEquals(0, sut.events.size());
        assertNotEquals(0, sut.batchFlushAttemptMillis);
    }

    @Test
    public void doesNotAttemptToMakeNetworkCallIdNoTypedEvents() throws InterruptedException {
        ArgumentCaptor<Runnable> runnableCaptor
                = ArgumentCaptor.forClass(Runnable.class);

        AvoBatcher sut = new AvoBatcher(mockApplication, mockNetworkCallsHandler);

        sut.mainHandler = mock(Handler.class);
        sut.batchFlushAttemptMillis = 0;

        for (int i = 0; i < AvoBatcher.batchSize; i++) {
            Map<String, String> event = new HashMap<>();
            event.put("no-type", "test");
            sut.events.add(event);
        }

        // When
        sut.checkIfBatchNeedsToBeSent();

        verify(sut.mainHandler).post(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        Thread.sleep(500);

        // Then
        verify(mockNetworkCallsHandler, never()).reportInspectorWithBatchBody(ArgumentMatchers.<Map<String, String>>anyList(),
                any(AvoNetworkCallsHandler.Callback.class));
        assertEquals(0, sut.events.size());
        assertEquals(0, sut.batchFlushAttemptMillis);
    }

    @Test
    public void attemptsToSendIfSizeIsMultipleOfBatchSize() throws InterruptedException {
        ArgumentCaptor<Runnable> runnableCaptor
                = ArgumentCaptor.forClass(Runnable.class);

        AvoBatcher sut = new AvoBatcher(mockApplication, mockNetworkCallsHandler);

        sut.mainHandler = mock(Handler.class);

        //When
        for (int i = 0; i < AvoBatcher.batchSize; i++) {
            sut.batchSessionStarted();
        }

        // Then
        verify(sut.mainHandler).post(any(Runnable.class));

        // When
        sut.batchTrackEventSchema("Test Event",
                new HashMap<String, AvoEventSchemaType>());

        // Then
        verify(sut.mainHandler).post(any(Runnable.class));

        // When
        for (int i = 0; i < AvoBatcher.batchSize - 1; i++) {
            sut.batchTrackEventSchema("Test Event",
                    new HashMap<String, AvoEventSchemaType>());
        }

        // Then
        verify(sut.mainHandler, times(2)).post(any(Runnable.class));
    }

    @Test
    public void attemptsToSendIfTimeIsGreaterThanFlushTime() throws InterruptedException {
        ArgumentCaptor<Runnable> runnableCaptor
                = ArgumentCaptor.forClass(Runnable.class);

        AvoBatcher sut = new AvoBatcher(mockApplication, mockNetworkCallsHandler);

        sut.mainHandler = mock(Handler.class);

        long flushMillis = TimeUnit.SECONDS.toMillis(AvoInspector.getBatchFlushSeconds());

        // Given
        sut.batchFlushAttemptMillis = System.currentTimeMillis() - flushMillis + 1000;

        //When
        sut.batchSessionStarted();

        // Then
        verify(sut.mainHandler, never()).post(any(Runnable.class)); // the handler does not run, so we adjust batchFlushAttemptMillis every time manually

        // Given
        sut.batchFlushAttemptMillis = System.currentTimeMillis() - flushMillis;

        //When
        sut.batchSessionStarted();

        // Then
        verify(sut.mainHandler).post(any(Runnable.class));

        // Given
        sut.batchFlushAttemptMillis = System.currentTimeMillis();

        //When
        sut.batchSessionStarted();

        // Then
        verify(sut.mainHandler).post(any(Runnable.class));

        // Given
        sut.batchFlushAttemptMillis = System.currentTimeMillis() - flushMillis;

        //When
        sut.batchSessionStarted();

        // Then
        verify(sut.mainHandler, times(2)).post(any(Runnable.class));
    }
}
