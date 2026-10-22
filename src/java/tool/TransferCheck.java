package tool;

import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import util.DbContext;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;

/**
 * Extension with the real ESP32: trains on the public sessions and measures how much the F1 score
 * drops on labelled sessions of the real device. Both datasets are built by FeatureDataset with the
 * same class attribute, so class indexes match (the workshop version numbered labels per query).
 */
public class TransferCheck {

    private static final int DEVICE_ID = 2; // the real ESP32 (device 1 is the replay device)

    public static void main(String[] args) throws Exception {
        List<FeatureDataset.Row> publicRows;
        List<FeatureDataset.Row> deviceRows;
        try (Connection conn = DbContext.getConnection()) {
            publicRows = FeatureDataset.load(conn, ModelTrainer.QUERY);
            deviceRows = FeatureDataset.load(conn, "SELECT session_id AS group_id, mq2_raw, mq135_raw, temp_c, rh_pct, "
                    + "compensated_score, label FROM v_GasEvent_Features WHERE device_id = " + DEVICE_ID);
        }
        if (deviceRows.isEmpty()) {
            System.out.println("Device " + DEVICE_ID + " has no labelled sessions yet (label them on the session page).");
            return;
        }
        Instances publicData = FeatureDataset.toInstances("public", publicRows);
        Instances deviceData = FeatureDataset.toInstances("device", deviceRows);
        int[] groups = new int[publicRows.size()];
        for (int i = 0; i < groups.length; i++) {
            groups[i] = publicRows.get(i).getGroup();
        }
        Classifier template = new RandomForest();
        Evaluation onPublic = ModelTrainer.groupedCrossValidation(template, publicData, groups, 10, 42);
        Classifier model = AbstractClassifier.makeCopy(template);
        model.buildClassifier(publicData);
        Evaluation onDevice = new Evaluation(publicData);
        onDevice.evaluateModel(model, deviceData);
        System.out.printf(Locale.ROOT, "F1 on public data (grouped 10-fold): %.3f%n",
                ModelTrainer.safe(onPublic.weightedFMeasure()));
        System.out.printf(Locale.ROOT, "F1 on %d real device sessions:       %.3f%n", deviceData.numInstances(),
                ModelTrainer.safe(onDevice.weightedFMeasure()));
        System.out.println(onDevice.toMatrixString("Confusion matrix on the real device"));
    }
}
