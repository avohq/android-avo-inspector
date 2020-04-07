package app.avo.inspector;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class InitializationTests {

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

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mockApplication.getPackageManager()).thenReturn(mockPackageManager);
        when(mockApplication.getPackageName()).thenReturn("");
        when(mockPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mockPackageInfo);
        when(mockApplication.getApplicationInfo()).thenReturn(mockApplicationInfo);
        when(mockApplication.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPrefs);
        when(mockSharedPrefs.getString(eq(AvoInstallationId.cacheKey), anyString())).thenReturn("testInstallationId");
    }

    @Test
    public void initWithProperParameters() {
        mockPackageInfo.versionCode = 10;
        mockApplicationInfo.packageName = "testPckg";

        sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        assertEquals(10L, (long)sut.appVersion);
        assertEquals("apiKey", sut.apiKey);
        assertEquals("testPckg", sut.appName);
        assertEquals(1, sut.libVersion);

        assertEquals("10", sut.avoBatcher.networkCallsHandler.appVersion);
        assertEquals("apiKey", sut.avoBatcher.networkCallsHandler.apiKey);
        assertEquals("testPckg", sut.avoBatcher.networkCallsHandler.appName);
        assertEquals("1", sut.avoBatcher.networkCallsHandler.libVersion);
        assertEquals("testInstallationId", sut.avoBatcher.networkCallsHandler.installationId);
    }

    @Test
    public void setsFlushAndLogsInDevMode() {
        AvoInspector.enableLogging(false);
        AvoInspector.setBatchFlushSeconds(5);

        sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        assertEquals(1, AvoInspector.getBatchFlushSeconds());
        assertTrue(AvoInspector.isLogging());
    }

    @Test
    public void setsFlushAndLogsInProdMode() {
        AvoInspector.enableLogging(true);
        AvoInspector.setBatchFlushSeconds(5);

        sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Prod);

        assertEquals(30, AvoInspector.getBatchFlushSeconds());
        assertFalse(AvoInspector.isLogging());
    }

    @Test
    public void setsFlushAndLogsInStagingMode() {
        AvoInspector.enableLogging(true);
        AvoInspector.setBatchFlushSeconds(5);

        sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Staging);

        assertEquals(30, AvoInspector.getBatchFlushSeconds());
        assertFalse(AvoInspector.isLogging());
    }

    @Test
    public void initsWithProdEnv() {
        sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Prod);

        assertEquals("prod", sut.env);
        assertEquals("prod", sut.avoBatcher.networkCallsHandler.envName);
    }

    @Test
    public void initsWithDevEnv() {
        sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        assertEquals("dev", sut.env);
        assertEquals("dev", sut.avoBatcher.networkCallsHandler.envName);
    }

    @Test
    public void initsWithStagingEnv() {
        sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Staging);

        assertEquals("staging", sut.env);
        assertEquals("staging", sut.avoBatcher.networkCallsHandler.envName);
    }
}
