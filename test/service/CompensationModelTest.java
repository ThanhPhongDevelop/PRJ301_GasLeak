package service;

import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CompensationModelTest {

    /** Clean air whose MQ-2 reading drifts by +15 per degree and -4 per %RH, plus a little noise. */
    private static double[][] cleanAir(int n, long seed) {
        Random random = new Random(seed);
        double[][] data = new double[4][n];
        for (int i = 0; i < n; i++) {
            double t = 22 + 10 * random.nextDouble();
            double rh = 40 + 40 * random.nextDouble();
            data[0][i] = 2000 + 15 * (t - 25) - 4 * (rh - 50) + random.nextGaussian() * 5;
            data[1][i] = 1600 + 6 * (t - 25) + 2 * (rh - 50) + random.nextGaussian() * 5;
            data[2][i] = t;
            data[3][i] = rh;
        }
        return data;
    }

    @Test
    public void fitRecoversTheTemperatureAndHumidityDrift() {
        double[][] d = cleanAir(400, 1);
        CompensationModel model = CompensationModel.fit(d[0], d[1], d[2], d[3], "test");
        Assert.assertEquals(model.getMq2().getBT(), 15.0, 0.3);
        Assert.assertEquals(model.getMq2().getBRh(), -4.0, 0.1);
        Assert.assertEquals(model.getMq135().getBT(), 6.0, 0.3);
        Assert.assertTrue(model.getMq2().getR2() > 0.95);
    }

    @Test
    public void compensatedCleanAirIsFlatAcrossWeather() {
        double[][] d = cleanAir(400, 2);
        CompensationModel model = CompensationModel.fit(d[0], d[1], d[2], d[3], "test");
        Assert.assertTrue(SignalMath.sd(d[0]) > 40, "raw clean air should drift");
        Assert.assertTrue(model.getMq2().getNoiseSd() < 7, "compensated clean air should only keep the noise");
        // hot and humid versus cool and dry: same compensated value within the noise
        double hot = model.compensateMq2(2000 + 15 * 7 - 4 * 25.0, 32.0, 75.0);
        double cool = model.compensateMq2(2000 + 15 * -3 - 4 * -10.0, 22.0, 40.0);
        Assert.assertEquals(hot, cool, 3.0);
    }

    @Test
    public void jsonRoundTripKeepsTheCoefficients() {
        double[][] d = cleanAir(200, 3);
        CompensationModel model = CompensationModel.fit(d[0], d[1], d[2], d[3], "test");
        CompensationModel copy = CompensationModel.fromJson(model.toJson());
        Assert.assertTrue(copy.isFitted());
        Assert.assertEquals(copy.compensateMq2(2100.0, 30.0, 70.0), model.compensateMq2(2100.0, 30.0, 70.0), 1e-9);
    }

    @Test
    public void manualProfileUsesTheTypedCoefficientsAfterSaving() {
        CompensationModel typed = CompensationModel.manual(25, 50, 15, -4, 6, 2);
        CompensationModel saved = CompensationModel.fromJson(typed.toJson());
        Assert.assertTrue(saved.isFitted());
        Assert.assertEquals(saved.getSource(), CompensationModel.MANUAL);
        // 30 °C and 70 %RH: 15 * 5 - 4 * 20 = -5 to remove from MQ-2, 6 * 5 + 2 * 20 = 70 from MQ-135
        Assert.assertEquals(saved.compensateMq2(2000.0, 30.0, 70.0), 2005.0, 1e-9);
        Assert.assertEquals(saved.compensateMq135(1600.0, 30.0, 70.0), 1530.0, 1e-9);
        Assert.assertTrue(saved.describe().startsWith("Set by hand"), saved.describe());
    }

    @Test
    public void unreadableProfileMeansNoCorrection() {
        CompensationModel model = CompensationModel.fromJson("{not json");
        Assert.assertFalse(model.isFitted());
        Assert.assertEquals(model.compensateMq2(1234.0, null, null), 1234.0);
    }

    @Test
    public void fittedModelNeedsTemperatureAndHumidity() {
        double[][] d = cleanAir(200, 4);
        CompensationModel model = CompensationModel.fit(d[0], d[1], d[2], d[3], "test");
        Assert.assertNull(model.compensateMq2(2000.0, null, 50.0));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void constantHumidityCannotBeFitted() {
        double[][] d = cleanAir(200, 5);
        java.util.Arrays.fill(d[3], 55.0);
        CompensationModel.fit(d[0], d[1], d[2], d[3], "test");
    }
}
