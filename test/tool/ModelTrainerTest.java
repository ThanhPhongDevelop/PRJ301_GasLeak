package tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import model.Labels;
import org.testng.Assert;
import org.testng.annotations.Test;
import weka.classifiers.Evaluation;
import weka.classifiers.lazy.IBk;
import weka.core.Instances;

public class ModelTrainerTest {

    @Test
    public void everyDatasetUsesTheSameLabelIndexes() {
        // the public data has no FLAME_DETECTED and the device data starts with MINOR_LEAK:
        // the workshop loader would number the labels differently in each dataset
        List<FeatureDataset.Row> publicRows = new ArrayList<FeatureDataset.Row>();
        publicRows.add(new FeatureDataset.Row(new double[]{1, 2, 3, 4, 5}, Labels.SAFE, 1));
        List<FeatureDataset.Row> deviceRows = new ArrayList<FeatureDataset.Row>();
        deviceRows.add(new FeatureDataset.Row(new double[]{1, 2, 3, 4, 5}, Labels.MINOR_LEAK, 1));
        Instances a = FeatureDataset.toInstances("public", publicRows);
        Instances b = FeatureDataset.toInstances("device", deviceRows);
        Assert.assertTrue(a.equalHeaders(b));
        Assert.assertEquals(b.classAttribute().value((int) b.instance(0).classValue()), Labels.MINOR_LEAK);
    }

    @Test
    public void groupedCrossValidationHoldsOutWholeRecordings() throws Exception {
        // every recording has its own offset: a model that memorises recordings scores well on a random
        // split but cannot do so on unseen recordings
        Random random = new Random(3);
        List<FeatureDataset.Row> rows = new ArrayList<FeatureDataset.Row>();
        for (int rec = 0; rec < 30; rec++) {
            String label = rec % 3 == 0 ? Labels.MINOR_LEAK : rec % 3 == 1 ? Labels.COOKING_FUMES : Labels.SAFE;
            double offset = 1500 + rec * 20; // neighbouring recordings have different labels
            for (int w = 0; w < 10; w++) {
                double v = offset + random.nextGaussian() * 3;
                rows.add(new FeatureDataset.Row(new double[]{v, v - 300, 25, 50, v}, label, rec));
            }
        }
        Instances data = FeatureDataset.toInstances("gas", rows);
        int[] groups = new int[rows.size()];
        for (int i = 0; i < groups.length; i++) {
            groups[i] = rows.get(i).getGroup();
        }
        Evaluation random10 = new Evaluation(data);
        random10.crossValidateModel(new IBk(1), data, 10, new Random(42));
        Evaluation grouped = ModelTrainer.groupedCrossValidation(new IBk(1), data, groups, 10, 42);
        Assert.assertEquals((int) grouped.numInstances(), rows.size());
        Assert.assertTrue(random10.pctCorrect() > 90, "random split memorises recordings: " + random10.pctCorrect());
        Assert.assertTrue(grouped.pctCorrect() < random10.pctCorrect() - 30, "grouped split reveals it: " + grouped.pctCorrect());
    }
}
