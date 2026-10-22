package service;

import org.testng.Assert;
import org.testng.annotations.Test;

public class SignalMathTest {

    @Test
    public void aucMeasuresSeparation() {
        Assert.assertEquals(SignalMath.auc(new double[]{5, 6, 7}, new double[]{1, 2, 3}), 1.0);
        Assert.assertEquals(SignalMath.auc(new double[]{1, 2}, new double[]{1, 2}), 0.5);
        Assert.assertEquals(SignalMath.auc(new double[]{1}, new double[]{2}), 0.0);
    }

    @Test
    public void percentileInterpolates() {
        double[] xs = {10, 20, 30, 40};
        Assert.assertEquals(SignalMath.percentile(xs, 0), 10.0);
        Assert.assertEquals(SignalMath.percentile(xs, 1), 40.0);
        Assert.assertEquals(SignalMath.percentile(xs, 0.5), 25.0, 1e-9);
        Assert.assertEquals(SignalMath.fractionAtOrAbove(xs, 30), 0.5);
    }
}
