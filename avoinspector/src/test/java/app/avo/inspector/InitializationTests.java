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

        mockPackageInfo.versionName = "myVersion";

        when(mockApplication.getPackageManager()).thenReturn(mockPackageManager);
        when(mockApplication.getPackageName()).thenReturn("");
        when(mockPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mockPackageInfo);
        when(mockApplication.getApplicationInfo()).thenReturn(mockApplicationInfo);
        when(mockApplication.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPrefs);
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
        when(mockApplication.getContentResolver()).thenReturn(mock(ContentResolver.class));
    }

    @Test
    public void initWithProperParameters() {
        //noinspection deprecation
        mockPackageInfo.versionCode = 10;
        mockApplicationInfo.packageName = "testPckg";

        sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        assertEquals("10", sut.appVersionString);
        assertEquals("apiKey", sut.apiKey);
        assertEquals("testPckg", sut.appName);
        assertEquals(5, sut.libVersion);

        assertEquals("10", sut.avoBatcher.networkCallsHandler.appVersion);
        assertEquals("apiKey", sut.avoBatcher.networkCallsHandler.apiKey);
        assertEquals("testPckg", sut.avoBatcher.networkCallsHandler.appName);
        assertEquals("5", sut.avoBatcher.networkCallsHandler.libVersion);
    }

    @Test
    public void initWithSemanticVersion() {
        String[] versionNames = {
                "1.2.3",
                "1.2.3-alpha",
                "1.2.3-alpha+build.45",
                "1.2.3+exp.sha.5114f85",
                "1.2.3+10",
                "MICROFRONTEND-1.2.3",
                "1.not.semantic.9"
        };

        for (String versionName : versionNames) {
            mockPackageInfo.versionName = versionName;
            mockApplicationInfo.packageName = "testPckg";

            sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

            String expectedVersionName = versionName.equals("1.not.semantic.9") ? "0" : versionName;

            assertEquals(expectedVersionName, sut.appVersionString);
            assertEquals("apiKey", sut.apiKey);
            assertEquals("testPckg", sut.appName);
            assertEquals(5, sut.libVersion);

            assertEquals(expectedVersionName, sut.avoBatcher.networkCallsHandler.appVersion);
            assertEquals("apiKey", sut.avoBatcher.networkCallsHandler.apiKey);
            assertEquals("testPckg", sut.avoBatcher.networkCallsHandler.appName);
            assertEquals("5", sut.avoBatcher.networkCallsHandler.libVersion);
        }
    }

    @Test
    public void setsFlushAndLogsInDevMode() {
        AvoInspector.enableLogging(false);
        AvoInspector.setBatchFlushSeconds(5);

        sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Dev);

        assertEquals(1, AvoInspector.getBatchSize());
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
    public void internalInitsWithProdEnv() {
        sut = new AvoInspector("apiKey", mockApplication, "Prod", null);

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
    public void internalInitsWithDevEnv() {
        sut = new AvoInspector("apiKey", mockApplication, "dev", null);

        assertEquals("dev", sut.env);
        assertEquals("dev", sut.avoBatcher.networkCallsHandler.envName);
    }

    @Test
    public void initsWithStagingEnv() {
        sut = new AvoInspector("apiKey", mockApplication, AvoInspectorEnv.Staging);

        assertEquals("staging", sut.env);
        assertEquals("staging", sut.avoBatcher.networkCallsHandler.envName);
    }

    @Test
    public void internalInitsWithStagingEnv() {
        sut = new AvoInspector("apiKey", mockApplication, "staging", null);

        assertEquals("staging", sut.env);
        assertEquals("staging", sut.avoBatcher.networkCallsHandler.envName);
    }

    @Test
    public void internalInitsAsDevWithUnknownEnv() {
        sut = new AvoInspector("apiKey", mockApplication, "whatever", null);

        assertEquals("dev", sut.env);
        assertEquals("dev", sut.avoBatcher.networkCallsHandler.envName);
    }
}
