package service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletContext;
import model.Labels;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.SerializationHelper;

/**
 * Second tier of the alerting: the Weka model trained by tool.ModelTrainer.
 * The model file and the empty dataset header that describes its inputs are read once from
 * web/WEB-INF/model/. Without them the application runs on the rules alone.
 */
public final class RiskScorer {

    /** Inputs of the model, in the order used for training (same names as v_GasEvent_Features). */
    public static final String[] FEATURES = {"mq2_raw", "mq135_raw", "temp_c", "rh_pct", "compensated_score"};
    public static final String MODEL_PATH = "/WEB-INF/model/gas_model.bin";
    public static final String HEADER_PATH = "/WEB-INF/model/gas_header.arff";

    private static final String CONTEXT_KEY = RiskScorer.class.getName();
    private static final String CONTEXT_PROBLEM_KEY = CONTEXT_KEY + ".problem";

    private final Classifier model;
    private final Instances header;
    private final String modelName;

    private RiskScorer(Classifier model, Instances header) {
        this.model = model;
        this.header = header;
        this.modelName = model.getClass().getSimpleName();
    }

    public static RiskScorer load(InputStream modelStream, Reader headerReader) throws Exception {
        Object loaded = SerializationHelper.read(modelStream);
        if (!(loaded instanceof Classifier)) {
            throw new IllegalArgumentException("The model file does not contain a Weka classifier");
        }
        Instances header = new Instances(new BufferedReader(headerReader));
        header.setClassIndex(header.numAttributes() - 1);
        checkHeader(header);
        return new RiskScorer((Classifier) loaded, header);
    }

    /** Model of this web application, loaded on first use; null when there is no usable model. */
    public static RiskScorer forContext(ServletContext context) {
        synchronized (RiskScorer.class) {
            Object cached = context.getAttribute(CONTEXT_KEY);
            if (cached instanceof RiskScorer) {
                return (RiskScorer) cached;
            }
            if (context.getAttribute(CONTEXT_PROBLEM_KEY) != null) {
                return null; // already tried; redeploy after training a model
            }
            try (InputStream modelStream = context.getResourceAsStream(MODEL_PATH);
                    InputStream headerStream = context.getResourceAsStream(HEADER_PATH)) {
                if (modelStream == null || headerStream == null) {
                    context.setAttribute(CONTEXT_PROBLEM_KEY, "No model yet: run tool.ModelTrainer, then redeploy.");
                    return null;
                }
                RiskScorer scorer = load(modelStream, new InputStreamReader(headerStream, StandardCharsets.UTF_8));
                context.setAttribute(CONTEXT_KEY, scorer);
                return scorer;
            } catch (Exception e) {
                context.log("Could not load the Weka model", e);
                context.setAttribute(CONTEXT_PROBLEM_KEY, "The model could not be loaded: " + e.getMessage());
                return null;
            }
        }
    }

    /** Why no model is in use, or null when one is loaded or loading has not been tried yet. */
    public static String problemOf(ServletContext context) {
        Object problem = context.getAttribute(CONTEXT_PROBLEM_KEY);
        return problem == null ? null : problem.toString();
    }

    /**
     * Predicts the label of one session. Returns null when an input is missing, because the model was
     * trained on complete rows only. Synchronized: some Weka classifiers are not thread safe.
     */
    public synchronized Prediction score(Double mq2Raw, Double mq135Raw, Double tempC, Double rhPct,
            Double compensatedScore) throws Exception {
        Double[] values = {mq2Raw, mq135Raw, tempC, rhPct, compensatedScore};
        DenseInstance instance = new DenseInstance(header.numAttributes());
        instance.setDataset(header);
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null || values[i].isNaN() || values[i].isInfinite()) {
                return null;
            }
            instance.setValue(i, values[i]);
        }
        instance.setClassMissing();
        double[] distribution = model.distributionForInstance(instance);
        Attribute classAttribute = header.classAttribute();
        int best = 0;
        double leak = 0;
        for (int i = 0; i < distribution.length; i++) {
            if (distribution[i] > distribution[best]) {
                best = i;
            }
            if (Labels.isLeak(classAttribute.value(i))) {
                leak += distribution[i];
            }
        }
        return new Prediction(classAttribute.value(best), distribution[best], leak, modelName);
    }

    public String getModelName() {
        return modelName;
    }

    /** Refuses a model trained on other inputs, which would silently give wrong predictions. */
    private static void checkHeader(Instances header) {
        if (header.numAttributes() != FEATURES.length + 1) {
            throw new IllegalArgumentException("The model expects " + (header.numAttributes() - 1)
                    + " inputs, the application sends " + FEATURES.length);
        }
        for (int i = 0; i < FEATURES.length; i++) {
            Attribute attribute = header.attribute(i);
            if (!attribute.isNumeric() || !FEATURES[i].equals(attribute.name())) {
                throw new IllegalArgumentException("Input " + (i + 1) + " of the model is " + attribute.name()
                        + ", expected " + FEATURES[i]);
            }
        }
        Attribute label = header.classAttribute();
        if (!label.isNominal()) {
            throw new IllegalArgumentException("The class of the model must be the nominal label");
        }
        for (int i = 0; i < label.numValues(); i++) {
            if (!Labels.isValid(label.value(i))) {
                throw new IllegalArgumentException("Unknown label in the model: " + label.value(i));
            }
        }
    }
}
