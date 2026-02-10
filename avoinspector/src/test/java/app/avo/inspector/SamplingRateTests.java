package app.avo.inspector;

import android.os.Handler;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SamplingRateTests {

    private AvoStorage prevAvoStorage;

    @Before
    public void setUp() {
        prevAvoStorage = AvoInspector.avoStorage;
        AvoAnonymousId.clearCache();
        AvoStorage mockStorage = mock(AvoStorage.class);
        when(mockStorage.isInitialized()).thenReturn(true);
        when(mockStorage.getItem(any())).thenReturn("testAnonymousId");
        AvoInspector.avoStorage = mockStorage;
    }

    @After
    public void tearDown() {
        AvoInspector.avoStorage = prevAvoStorage;
        AvoAnonymousId.clearCache();
    }

    @Test
    public void doesNotSendDataWithSamplingRate0() throws InterruptedException {

        AvoNetworkCallsHandler sut = new AvoNetworkCallsHandler(
                "testApiKey", "testEnvName", "testAppName",
                "testAppVersion", "testLibVersion"
        );
        sut.samplingRate = 0;
        sut.callbackHandler = mock(Handler.class);

        for (int i = 0; i < 1000; i++) {
            final Map<String, Object> body = sut.bodyForEventSchemaCall("testEvent", new HashMap<String, AvoEventSchemaType>(), null, null, null);
            sut.reportInspectorWithBatchBody(new ArrayList<Map<String, Object>>() {{
                                                 add(body);
                                             }},
                    new AvoNetworkCallsHandler.Callback() {
                        @Override
                        public void call(boolean retry) {
                        }
                    });
        }

        Thread.sleep(1000);
        verify(sut.callbackHandler, never()).post(any(Runnable.class));
    }

    @Test
    public void alwaysSendsDataWithSamplingRate1() throws InterruptedException {

        AvoNetworkCallsHandler sut = new AvoNetworkCallsHandler(
                "testApiKey", "testEnvName", "testAppName",
                "testAppVersion", "testLibVersion"
        );
        sut.samplingRate = 1;
        sut.callbackHandler = mock(Handler.class);

        for (int i = 0; i < 10; i++) {
            final Map<String, Object> body = sut.bodyForEventSchemaCall("testEvent", new HashMap<String, AvoEventSchemaType>(), null, null, null);
            sut.reportInspectorWithBatchBody(new ArrayList<Map<String, Object>>() {{
                                                 add(body);
                                             }},
                    new AvoNetworkCallsHandler.Callback() {
                        @Override
                        public void call(boolean retry) {
                            Assert.fail();
                        }
                    });
        }

        Thread.sleep(5000);
        verify(sut.callbackHandler, times(10)).post(any(Runnable.class));
    }
}
