package tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import model.Labels;
import org.testng.Assert;
import org.testng.annotations.Test;
import service.CompensationModel;

public class CompensationAnalysisTest {

    /**
     * 40 recordings with different weather. Clean air drifts strongly with humidity, leaks add 120
     * counts, so on raw values humid clean air looks like a leak and compensation should fix that.
     */
    private static List<CompensationAnalysis.Sample> recordings() {
        Random random = new Random(11);
        List<CompensationAnalysis.Sample> samples = new ArrayList<CompensationAnalysis.Sample>();
        long id = 0;
        for (int rec = 0; rec < 40; rec++) {
            double baseT = 22 + 8 * random.nextDouble();
            double baseRh = 40 + 40 * random.nextDouble();
            String stimulus = rec % 2 == 0 ? Labels.MINOR_LEAK : Labels.COOKING_FUMES;
            for (int w = 0; w < 12; w++) {
                double t = baseT + random.nextGaussian();
                double rh = baseRh + 2 * random.nextGaussian();
                boolean during = w >= 6;
                String label = during ? stimulus : Labels.SAFE;
                double drift = 4 * (rh - 60) + 6 * (t - 26);
                double gas = Labels.MINOR_LEAK.equals(label) ? 120 : 0;
                double fumes = Labels.COOKING_FUMES.equals(label) ? 150 : 0;
                samples.add(new CompensationAnalysis.Sample(++id, rec, 2000 + drift + gas + random.nextGaussian() * 8,
                        1700 + drift / 2 + fumes + random.nextGaussian() * 8, t, rh, label));
            }
        }
        return samples;
    }

    @Test
    public void compensationCutsFalseAlarmsAtTheSameDetectionRate() {
        CompensationAnalysis.Report report = CompensationAnalysis.evaluate(recordings(), 5, 0.9, 42);
        CompensationAnalysis.Outcome raw = report.getRaw();
        CompensationAnalysis.Outcome comp = report.getCompensated();
        Assert.assertTrue(comp.detectionRate() > 0.8, "detection " + comp.detectionRate());
        Assert.assertTrue(raw.falseAlarmRateSafe() > 0.2, "raw false alarms " + raw.falseAlarmRateSafe());
        Assert.assertTrue(comp.falseAlarmRateSafe() < raw.falseAlarmRateSafe() / 4,
                "compensated false alarms " + comp.falseAlarmRateSafe());
        Assert.assertTrue(comp.auc() > raw.auc());
    }

    @Test
    public void suggestedLevelsSitBetweenCleanAirAndLeaks() {
        List<CompensationAnalysis.Sample> samples = recordings();
        CompensationModel model = CompensationAnalysis.fitSafe(samples, "test");
        double[][] levels = CompensationAnalysis.suggestLevels(samples, model, 0.9);
        double baseline = model.getMq2().getBaseline();
        Assert.assertTrue(levels[0][0] > baseline && levels[0][0] < baseline + 120, "warn " + levels[0][0]);
        Assert.assertTrue(levels[0][1] > levels[0][0]);
        Assert.assertTrue(levels[1][1] > levels[1][0]);
    }
}
