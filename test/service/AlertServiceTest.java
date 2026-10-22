package service;

import java.util.Collections;
import java.util.List;
import model.Labels;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AlertServiceTest {

    private final RuleEngine engine = new RuleEngine();
    private final RuleEngine.Config config = new RuleEngine.Config();

    private RuleEngine.Outcome rule(double mq2, double mq135) {
        return engine.evaluate(new RuleEngine.Reading(mq2, mq135, 27.0, 60.0, mq2, mq135, null), config,
                Collections.<Double>emptyList());
    }

    @Test
    public void safeSessionWithoutModelRaisesNothing() {
        Assert.assertTrue(AlertService.decide(rule(900, 800), null).isEmpty());
    }

    @Test
    public void ruleAlertCarriesTheModelOpinionAsEvidence() {
        List<AlertService.Draft> drafts = AlertService.decide(rule(1700, 800),
                new Prediction(Labels.MINOR_LEAK, 0.9, 0.93, "RandomForest"));
        Assert.assertEquals(drafts.size(), 1);
        Assert.assertEquals(drafts.get(0).getSource(), "RULE");
        Assert.assertEquals(drafts.get(0).getRiskScore(), 0.93, 1e-9);
        Assert.assertEquals(drafts.get(0).getEvidence().get(drafts.get(0).getEvidence().size() - 1).getItem(), "model");
    }

    @Test
    public void confidentModelCatchesALeakTheRulesMissed() {
        List<AlertService.Draft> drafts = AlertService.decide(rule(1300, 800),
                new Prediction(Labels.MINOR_LEAK, 0.86, 0.88, "RandomForest"));
        Assert.assertEquals(drafts.size(), 1);
        Assert.assertEquals(drafts.get(0).getSource(), "ML");
        Assert.assertEquals(drafts.get(0).getLabel(), Labels.MINOR_LEAK);
    }

    @Test
    public void unsureModelAloneRaisesNothing() {
        Assert.assertTrue(AlertService.decide(rule(1300, 800),
                new Prediction(Labels.MINOR_LEAK, 0.6, 0.6, "J48")).isEmpty());
    }

    @Test
    public void modelNeverRemovesARuleAlert() {
        List<AlertService.Draft> drafts = AlertService.decide(rule(2600, 800),
                new Prediction(Labels.SAFE, 0.99, 0.01, "J48"));
        Assert.assertEquals(drafts.size(), 1);
        Assert.assertEquals(drafts.get(0).getLabel(), Labels.MAJOR_LEAK);
    }
}
