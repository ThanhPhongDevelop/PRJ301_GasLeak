package service;

/** Output of the Weka model for one session. */
public class Prediction {

    private final String label;
    private final double confidence;
    private final double leakProbability;
    private final String modelName;

    public Prediction(String label, double confidence, double leakProbability, String modelName) {
        this.label = label;
        this.confidence = confidence;
        this.leakProbability = leakProbability;
        this.modelName = modelName;
    }

    public String getLabel() {
        return label;
    }

    /** Probability of the predicted label. */
    public double getConfidence() {
        return confidence;
    }

    /** Probability of MINOR_LEAK plus MAJOR_LEAK; stored as Alert.risk_score. */
    public double getLeakProbability() {
        return leakProbability;
    }

    public String getModelName() {
        return modelName;
    }
}
