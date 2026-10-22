package service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import model.Labels;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tool.FeatureDataset;
import weka.classifiers.trees.J48;
import weka.core.Instances;
import weka.core.SerializationHelper;

/** Trains a small model the way tool.ModelTrainer saves it, then loads it the way the web application does. */
public class RiskScorerTest {

    private RiskScorer scorer;

    @BeforeClass
    public void trainAndLoad() throws Exception {
        Random random = new Random(7);
        List<FeatureDataset.Row> rows = new ArrayList<FeatureDataset.Row>();
        for (int i = 0; i < 60; i++) {
            rows.add(row(1900, 1700, Labels.SAFE, random));
            rows.add(row(1950, 2300, Labels.COOKING_FUMES, random));
            rows.add(row(2500, 1900, Labels.MINOR_LEAK, random));
        }
        Instances data = FeatureDataset.toInstances("gas", rows);
        J48 model = new J48();
        model.buildClassifier(data);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SerializationHelper.write(bytes, model);
        String header = FeatureDataset.emptyHeader("gas").toString();
        scorer = RiskScorer.load(new ByteArrayInputStream(bytes.toByteArray()), new StringReader(header));
    }

    private static FeatureDataset.Row row(double mq2, double mq135, String label, Random random) {
        double t = 22 + 8 * random.nextDouble();
        double rh = 40 + 30 * random.nextDouble();
        double noise2 = random.nextGaussian() * 20;
        return new FeatureDataset.Row(new double[]{mq2 + noise2, mq135 + random.nextGaussian() * 20, t, rh,
            mq2 + noise2}, label, 0);
    }

    @Test
    public void predictsTheTrainedClasses() throws Exception {
        Prediction leak = scorer.score(2510.0, 1890.0, 25.0, 55.0, 2510.0);
        Assert.assertEquals(leak.getLabel(), Labels.MINOR_LEAK);
        Assert.assertTrue(leak.getLeakProbability() > 0.5);
        Assert.assertEquals(scorer.score(1950.0, 2310.0, 25.0, 55.0, 1950.0).getLabel(), Labels.COOKING_FUMES);
        Assert.assertEquals(scorer.getModelName(), "J48");
    }

    @Test
    public void missingInputGivesNoPrediction() throws Exception {
        Assert.assertNull(scorer.score(2510.0, null, 25.0, 55.0, 2510.0));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void modelWithOtherInputsIsRefused() throws Exception {
        String header = "@relation x\n@attribute a numeric\n@attribute label {SAFE,MINOR_LEAK}\n@data\n";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SerializationHelper.write(bytes, new J48());
        RiskScorer.load(new ByteArrayInputStream(bytes.toByteArray()), new StringReader(header));
    }
}
