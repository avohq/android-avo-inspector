package app.avo.inspector;

import org.junit.Assert;
import org.junit.Test;

public class StaticParametersTests {

    @Test
    public void setsIdLogging() {
        AvoInspector.enableLogging(true);

        Assert.assertTrue(AvoInspector.isLogging());

        AvoInspector.enableLogging(false);

        Assert.assertFalse(AvoInspector.isLogging());
    }

    @Test
    public void setsBatchSize() {
        AvoInspector.setBatchSize(10);

        Assert.assertEquals(10, AvoInspector.getBatchSize());

        AvoInspector.setBatchSize(100);

        Assert.assertEquals(100, AvoInspector.getBatchSize());
    }

    @Test
    public void setsBatchFlushSeconds() {
        AvoInspector.setBatchFlushSeconds(10);

        Assert.assertEquals(10, AvoInspector.getBatchFlushSeconds());

        AvoInspector.setBatchFlushSeconds(100);

        Assert.assertEquals(100, AvoInspector.getBatchFlushSeconds());
    }
}