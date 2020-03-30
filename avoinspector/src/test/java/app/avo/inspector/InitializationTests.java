package app.avo.inspector;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class InitializationTests {

    AvoInspector sut;

    @Mock
    Context mockContext;
    @Mock
    PackageManager mockPackageManager;
    @Mock
    PackageInfo mockPackageInfo;
    @Mock
    ApplicationInfo mockApplicationInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mockContext.getPackageManager()).thenReturn(mockPackageManager);
        when(mockContext.getPackageName()).thenReturn("");
        when(mockPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mockPackageInfo);
        when(mockContext.getApplicationInfo()).thenReturn(mockApplicationInfo);
    }

    @Test
    public void initWithProperParameters() {
        mockPackageInfo.versionCode = 10;
        mockApplicationInfo.packageName = "testPckg";

        sut = new AvoInspector("apiKey", mockContext, AvoInspectorEnv.Dev);

        assertEquals(10L, (long)sut.appVersion);
        assertEquals("apiKey", sut.apiKey);
        assertEquals("testPckg", sut.appName);
        assertEquals(1, sut.libVersion);
    }

    @Test
    public void initsWithProdEnv() {
        sut = new AvoInspector("apiKey", mockContext, AvoInspectorEnv.Prod);

        assertEquals("prod", sut.env);
    }

    @Test
    public void initsWithDevEnv() {
        sut = new AvoInspector("apiKey", mockContext, AvoInspectorEnv.Dev);

        assertEquals("dev", sut.env);
    }

    @Test
    public void initsWithStagingEnv() {
        sut = new AvoInspector("apiKey", mockContext, AvoInspectorEnv.Staging);

        assertEquals("staging", sut.env);
    }
}
