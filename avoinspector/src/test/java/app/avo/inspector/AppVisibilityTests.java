package app.avo.inspector;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AppVisibilityTests {

    @Mock
    Application mockApplication;
    @Mock
    PackageManager mockPackageManager;
    @Mock
    PackageInfo mockPackageInfo;
    @Mock
    ApplicationInfo mockApplicationInfo;
    @Mock
    AvoSessionTracker mockSessionTracker;
    @Mock
    SharedPreferences mockSharedPrefs;
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
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
        when(mockApplication.getContentResolver()).thenReturn(mock(ContentResolver.class));
    }

    @Test
    public void startsAsHidden() {
        // When
        AvoInspector sut = new AvoInspector("api key", mockApplication, AvoInspectorEnv.Dev);

        // Then
        assertTrue(sut.isHidden);
    }

    @Test
    public void notHiddenOnActivityStarted() {
        ArgumentCaptor<Application.ActivityLifecycleCallbacks> activityLifecycleCallbackCaptor
                = ArgumentCaptor.forClass(Application.ActivityLifecycleCallbacks.class);

        // When
        AvoInspector sut = new AvoInspector("api key", mockApplication, AvoInspectorEnv.Dev);
        sut.sessionTracker = mockSessionTracker;

        // Then
        verify(mockApplication).registerActivityLifecycleCallbacks(activityLifecycleCallbackCaptor.capture());
        activityLifecycleCallbackCaptor.getValue().onActivityStarted(mock(Activity.class));

        assertFalse(sut.isHidden);
    }

    @Test
    public void hiddenOnActivityStartedAndAppBackground() {
        ArgumentCaptor<Application.ActivityLifecycleCallbacks> activityLifecycleCallbackCaptor
                = ArgumentCaptor.forClass(Application.ActivityLifecycleCallbacks.class);
        ArgumentCaptor<ComponentCallbacks2> componentCallbackCaptor
                = ArgumentCaptor.forClass(ComponentCallbacks2.class);

        // When
        AvoInspector sut = new AvoInspector("api key", mockApplication, AvoInspectorEnv.Dev);

        // Then
        verify(mockApplication).registerActivityLifecycleCallbacks(activityLifecycleCallbackCaptor.capture());
        verify(mockApplication).registerComponentCallbacks(componentCallbackCaptor.capture());

        // When
        activityLifecycleCallbackCaptor.getValue().onActivityStarted(mock(Activity.class));
        componentCallbackCaptor.getValue().onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);

        // Then
        assertTrue(sut.isHidden);
    }

    @Test
    public void notHiddenOnActivityStartedAndAppBackgroundAndActivityStarted() {
        ArgumentCaptor<Application.ActivityLifecycleCallbacks> activityLifecycleCallbackCaptor
                = ArgumentCaptor.forClass(Application.ActivityLifecycleCallbacks.class);
        ArgumentCaptor<ComponentCallbacks2> componentCallbackCaptor
                = ArgumentCaptor.forClass(ComponentCallbacks2.class);

        // When
        AvoInspector sut = new AvoInspector("api key", mockApplication, AvoInspectorEnv.Dev);

        // Then
        verify(mockApplication).registerActivityLifecycleCallbacks(activityLifecycleCallbackCaptor.capture());
        verify(mockApplication).registerComponentCallbacks(componentCallbackCaptor.capture());

        // When
        activityLifecycleCallbackCaptor.getValue().onActivityStarted(mock(Activity.class));
        componentCallbackCaptor.getValue().onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
        activityLifecycleCallbackCaptor.getValue().onActivityStarted(mock(Activity.class));

        // Then
        assertFalse(sut.isHidden);
    }
}
