package service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import model.EvidenceItem;
import model.Labels;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RuleEngineTest {

    private final RuleEngine engine = new RuleEngine();
    // MQ-2 warning 1500 / alarm 2500, MQ-135 warning 1200 / alarm 2200 (seed defaults)
    private final RuleEngine.Config config = new RuleEngine.Config().thresholdSource("zone Stove area");
    private final List<Double> noHistory = Collections.emptyList();

    private static RuleEngine.Reading reading(Double mq2, Double mq135, Double temp, Double rh, Boolean flame) {
        // identity compensation: compensated equals raw
        return new RuleEngine.Reading(mq2, mq135, temp, rh, mq2, mq135, flame);
    }

    private static boolean hasItem(RuleEngine.Outcome outcome, String item) {
        for (EvidenceItem evidence : outcome.getEvidence()) {
            if (evidence.getItem().equals(item)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void normalReadingsAreSafe() {
        RuleEngine.Outcome outcome = engine.evaluate(reading(900.0, 800.0, 27.0, 60.0, false), config, noHistory);
        Assert.assertEquals(outcome.getLabel(), Labels.SAFE);
    }

    @Test
    public void warningLevelGivesMinorLeakWithEvidence() {
        RuleEngine.Outcome outcome = engine.evaluate(reading(1600.0, 900.0, 27.0, 60.0, null), config, noHistory);
        Assert.assertEquals(outcome.getLabel(), Labels.MINOR_LEAK);
        Assert.assertTrue(hasItem(outcome, "mq2_compensated"));
        Assert.assertTrue(outcome.getEvidence().get(0).getValue().contains("zone Stove area"));
    }

    @Test
    public void alarmLevelGivesMajorLeak() {
        RuleEngine.Outcome outcome = engine.evaluate(reading(2600.0, 900.0, 27.0, 60.0, null), config, noHistory);
        Assert.assertEquals(outcome.getLabel(), Labels.MAJOR_LEAK);
    }

    @Test
    public void flameSensorWinsOverEveryOtherRule() {
        RuleEngine.Outcome outcome = engine.evaluate(reading(null, 900.0, 27.0, 60.0, true), config, noHistory);
        Assert.assertEquals(outcome.getLabel(), Labels.FLAME_DETECTED);
        Assert.assertTrue(hasItem(outcome, "flame_sensor"));
    }

    @Test
    public void heatLimitGivesFlameDetected() {
        RuleEngine.Outcome outcome = engine.evaluate(reading(1000.0, 900.0, 70.0, 30.0, null), config, noHistory);
        Assert.assertEquals(outcome.getLabel(), Labels.FLAME_DETECTED);
        Assert.assertTrue(hasItem(outcome, "temp_c"));
    }

    @Test
    public void missingGasReadingIsSensorFault() {
        RuleEngine.Outcome outcome = engine.evaluate(reading(null, 900.0, 27.0, 60.0, null), config, noHistory);
        Assert.assertEquals(outcome.getLabel(), Labels.SENSOR_FAULT);
    }

    @Test
    public void unchangedReadingIsStuckSensor() {
        List<Double> previous = Arrays.asList(1010.0, 1010.0, 1010.0, 1010.0);
        RuleEngine.Outcome outcome = engine.evaluate(reading(1010.0, 900.0, 27.0, 60.0, null), config, previous);
        Assert.assertEquals(outcome.getLabel(), Labels.SENSOR_FAULT);
        Assert.assertTrue(outcome.getEvidence().get(0).getValue().contains("same value"));
    }

    @Test
    public void saturatedGasSensorIsTreatedAsLeakNotFault() {
        List<Double> previous = Arrays.asList(4095.0, 4095.0, 4095.0, 4095.0);
        RuleEngine.Outcome outcome = engine.evaluate(reading(4095.0, 900.0, 27.0, 60.0, null), config, previous);
        Assert.assertEquals(outcome.getLabel(), Labels.MAJOR_LEAK);
        Assert.assertTrue(hasItem(outcome, "mq2_range"));
    }

    @Test
    public void brokenHumiditySensorDoesNotHideALeak() {
        RuleEngine.Reading reading = new RuleEngine.Reading(1700.0, 900.0, 27.0, null, null, null, null);
        RuleEngine.Outcome outcome = engine.evaluate(reading, config, noHistory);
        Assert.assertEquals(outcome.getLabel(), Labels.MINOR_LEAK);
        Assert.assertTrue(hasItem(outcome, "compensation"));
        Assert.assertTrue(hasItem(outcome, "fault_rh_pct"));
    }

    @Test
    public void brokenHumiditySensorWithoutGasIsSensorFault() {
        RuleEngine.Reading reading = new RuleEngine.Reading(900.0, 900.0, 27.0, 140.0, null, null, null);
        RuleEngine.Outcome outcome = engine.evaluate(reading, config, noHistory);
        Assert.assertEquals(outcome.getLabel(), Labels.SENSOR_FAULT);
    }

    @Test
    public void airQualityRiseAloneIsCookingFumes() {
        RuleEngine.Outcome outcome = engine.evaluate(reading(1000.0, 1500.0, 30.0, 70.0, null), config, noHistory);
        Assert.assertEquals(outcome.getLabel(), Labels.COOKING_FUMES);
    }

    @Test
    public void compensationRemovesAFalseAlarmCausedByHumidity() {
        // raw MQ-2 1550 crosses the warning level only because of a humid kitchen; compensated it is 1200
        RuleEngine.Reading reading = new RuleEngine.Reading(1550.0, 900.0, 28.0, 85.0, 1200.0, 850.0, null);
        Assert.assertEquals(engine.evaluate(reading, config, noHistory).getLabel(), Labels.SAFE);
        RuleEngine.Reading uncompensated = new RuleEngine.Reading(1550.0, 900.0, 28.0, 85.0, 1550.0, 900.0, null);
        Assert.assertEquals(engine.evaluate(uncompensated, config, noHistory).getLabel(), Labels.MINOR_LEAK);
    }
}
