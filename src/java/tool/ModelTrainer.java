package tool;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import util.DbContext;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.SerializationHelper;

/**
 * Compares the 4 algorithms on the public sessions (view v_PublicSession_Features) and saves the best
 * one for RiskScorer. Run File in NetBeans after tool.CompensationFitter; then redeploy.
 *
 * Two evaluations are printed:
 * - random 10-fold cross-validation, as in the workshop;
 * - grouped 10-fold cross-validation, where all windows of one recording stay in the same fold.
 *   Windows of the same recording look alike, so the random split lets the model see "the answer"
 *   during training and gives optimistic figures. The grouped figures are the honest ones for the
 *   report, and the best model is chosen on them.
 * An ablation shows what the compensated score and the climate inputs add to the best algorithm.
 */
public class ModelTrainer {

    static final String QUERY = "SELECT induction_id AS group_id, mq2_raw, mq135_raw, temp_c, rh_pct, "
            + "compensated_score, label FROM v_PublicSession_Features ORDER BY public_id"; // fixed order: same folds every run
    private static final int FOLDS = 10;
    private static final long SEED = 42;
    private static final String MODEL_FOLDER = "web/WEB-INF/model";

    public static void main(String[] args) throws Exception {
        List<FeatureDataset.Row> rows;
        try (Connection conn = DbContext.getConnection()) {
            rows = FeatureDataset.load(conn, QUERY);
        }
        if (rows.size() < FOLDS * 2) {
            System.out.println("Only " + rows.size() + " labelled sessions: run sql/04_public_data.sql first.");
            return;
        }
        Instances data = FeatureDataset.toInstances("gas", rows);
        int[] groups = groupsOf(rows);
        System.out.println("Sessions: " + data.numInstances() + " from " + new TreeSet<Integer>(asList(groups)).size()
                + " recordings; per label: " + countPerLabel(data));

        Map<String, Classifier> models = candidates();
        List<String[]> csv = new ArrayList<String[]>();
        csv.add(new String[]{"model", "evaluation", "accuracy", "precision", "recall", "f1"});

        System.out.println();
        System.out.println("Random 10-fold cross-validation (workshop method, optimistic):");
        printHeader();
        for (Map.Entry<String, Classifier> entry : models.entrySet()) {
            Evaluation eval = new Evaluation(data);
            eval.crossValidateModel(AbstractClassifier.makeCopy(entry.getValue()), data, FOLDS, new Random(SEED));
            printRow(entry.getKey(), eval, csv, "random 10-fold");
        }

        System.out.println();
        System.out.println("Grouped 10-fold cross-validation (whole recordings held out, used to choose):");
        printHeader();
        String bestName = null;
        double bestF1 = -1;
        Evaluation bestEval = null;
        for (Map.Entry<String, Classifier> entry : models.entrySet()) {
            Evaluation eval = groupedCrossValidation(entry.getValue(), data, groups, FOLDS, SEED);
            printRow(entry.getKey(), eval, csv, "grouped 10-fold");
            double f1 = safe(eval.weightedFMeasure());
            if (f1 > bestF1) {
                bestF1 = f1;
                bestName = entry.getKey();
                bestEval = eval;
            }
        }
        System.out.println("Huerta et al. 2016 (baseline): copy the figures reported in the paper into the report table.");

        System.out.println();
        System.out.println("Ablation for " + bestName + " (grouped 10-fold):");
        printHeader();
        Instances noCompensation = new Instances(data);
        noCompensation.deleteAttributeAt(4); // compensated_score
        printRow("no compensated_score", groupedCrossValidation(models.get(bestName), noCompensation, groups,
                FOLDS, SEED), csv, "ablation");
        Instances gasOnly = new Instances(noCompensation);
        gasOnly.deleteAttributeAt(3); // rh_pct
        gasOnly.deleteAttributeAt(2); // temp_c
        printRow("gas sensors only", groupedCrossValidation(models.get(bestName), gasOnly, groups, FOLDS, SEED),
                csv, "ablation");

        System.out.println();
        System.out.println("Confusion matrix of " + bestName + " (grouped):");
        System.out.println(bestEval.toMatrixString(""));

        Classifier best = AbstractClassifier.makeCopy(models.get(bestName));
        best.buildClassifier(data); // train the winner on every session
        File folder = new File(MODEL_FOLDER);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            folder = new File(".");
        }
        SerializationHelper.write(new File(folder, "gas_model.bin").getPath(), best);
        try (PrintWriter out = new PrintWriter(new File(folder, "gas_header.arff"), "UTF-8")) {
            out.print(FeatureDataset.emptyHeader("gas").toString()); // the inputs RiskScorer must send
        }
        writeCsv(new File("model_results.csv"), csv);
        System.out.println("Saved " + bestName + " to " + folder.getAbsolutePath()
                + " and the tables to model_results.csv. Redeploy the web application to load it.");
    }

    /** The 4 algorithms of the workshop. */
    static Map<String, Classifier> candidates() {
        Map<String, Classifier> models = new LinkedHashMap<String, Classifier>();
        RandomForest forest = new RandomForest();
        forest.setSeed((int) SEED);
        models.put("RandomForest", forest);
        models.put("J48", new J48());
        models.put("NaiveBayes", new NaiveBayes());
        models.put("IBk", new IBk(5));
        return models;
    }

    /** Cross-validation where every group (recording) is entirely in one fold. */
    static Evaluation groupedCrossValidation(Classifier template, Instances data, int[] groups, int folds, long seed)
            throws Exception {
        List<Integer> distinct = new ArrayList<Integer>(new TreeSet<Integer>(asList(groups)));
        Collections.shuffle(distinct, new Random(seed));
        Map<Integer, Integer> foldOf = new HashMap<Integer, Integer>();
        for (int i = 0; i < distinct.size(); i++) {
            foldOf.put(distinct.get(i), i % folds);
        }
        Evaluation eval = new Evaluation(data);
        for (int fold = 0; fold < folds; fold++) {
            Instances train = new Instances(data, 0);
            Instances test = new Instances(data, 0);
            for (int i = 0; i < data.numInstances(); i++) {
                (foldOf.get(groups[i]) == fold ? test : train).add(data.instance(i));
            }
            if (train.numInstances() == 0 || test.numInstances() == 0) {
                continue;
            }
            Classifier model = AbstractClassifier.makeCopy(template);
            model.buildClassifier(train);
            eval.evaluateModel(model, test);
        }
        return eval;
    }

    private static void printHeader() {
        System.out.printf(Locale.ROOT, "%-22s %9s %10s %8s %8s%n", "model", "accuracy", "precision", "recall", "f1");
    }

    private static void printRow(String name, Evaluation eval, List<String[]> csv, String evaluation) {
        double accuracy = eval.pctCorrect() / 100;
        double precision = safe(eval.weightedPrecision());
        double recall = safe(eval.weightedRecall());
        double f1 = safe(eval.weightedFMeasure());
        System.out.printf(Locale.ROOT, "%-22s %9.4f %10.4f %8.4f %8.4f%n", name, accuracy, precision, recall, f1);
        csv.add(new String[]{name, evaluation, fmt(accuracy), fmt(precision), fmt(recall), fmt(f1)});
    }

    /** Weka gives NaN when a class is never predicted; count it as 0 so the comparison stays defined. */
    static double safe(double value) {
        return Double.isNaN(value) ? 0 : value;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static int[] groupsOf(List<FeatureDataset.Row> rows) {
        int[] groups = new int[rows.size()];
        for (int i = 0; i < groups.length; i++) {
            groups[i] = rows.get(i).getGroup();
        }
        return groups;
    }

    private static List<Integer> asList(int[] values) {
        List<Integer> list = new ArrayList<Integer>();
        for (int v : values) {
            list.add(v);
        }
        return list;
    }

    private static Map<String, Integer> countPerLabel(Instances data) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < data.numInstances(); i++) {
            String label = data.classAttribute().value((int) data.instance(i).classValue());
            Integer c = counts.get(label);
            counts.put(label, c == null ? 1 : c + 1);
        }
        return counts;
    }

    private static void writeCsv(File file, List<String[]> rows) throws IOException {
        try (PrintWriter out = new PrintWriter(file, StandardCharsets.UTF_8.name())) {
            for (String[] row : rows) {
                out.print(String.join(",", row) + "\n");
            }
        }
    }
}
