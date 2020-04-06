package app.avo.inspector;

import android.os.Handler;

import androidx.annotation.Nullable;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class SamplingRateTests {

    @Test
    public void doesNotSendDataWithSamplingRate0() throws InterruptedException {

        AvoNetworkCallsHandler sut = new AvoNetworkCallsHandler(
                "testApiKey", "testEnvName", "testAppName",
                "testAppVersion", "testLibVersion",
                "testInstallationId"
        );
        sut.samplingRate = 0;
        sut.callbackHandler = mock(Handler.class);

        for (int i = 0; i < 1000; i++) {
            final Map<String, String> body = sut.bodyForSessionStartedCall();
            sut.reportInspectorWithBatchBody(new ArrayList<Map<String, String>>() {{
                                                 add(body);
                                             }},
                    new AvoNetworkCallsHandler.Callback() {
                        @Override
                        public void call(@Nullable String error) {
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
                "testAppVersion", "testLibVersion",
                "testInstallationId"
        );
        sut.samplingRate = 1;
        sut.callbackHandler = mock(Handler.class);

        for (int i = 0; i < 10; i++) {
            final Map<String, String> body = sut.bodyForSessionStartedCall();
            sut.reportInspectorWithBatchBody(new ArrayList<Map<String, String>>() {{
                                                 add(body);
                                             }},
                    new AvoNetworkCallsHandler.Callback() {
                        @Override
                        public void call(@Nullable String error) {
                            Assert.fail();
                        }
                    });
        }

        Thread.sleep(1000);
        verify(sut.callbackHandler, times(10)).post(any(Runnable.class));
    }
}
