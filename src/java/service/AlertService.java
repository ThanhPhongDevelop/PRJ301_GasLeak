package service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import model.EvidenceItem;
import model.Labels;

/**
 * Decides which alerts a session raises from the two tiers.
 * Rules always win for the dangerous labels: the model can add an alert, never remove one.
 */
public final class AlertService {

    /** The model alone raises a leak alert only when it is at least this sure. */
    public static final double ML_ALERT_CONFIDENCE = 0.80;
    /** No new alert while the same device has an unresolved alert with the same label this recent. */
    public static final int DEDUPE_MINUTES = 30;

    private AlertService() {
    }

    public static List<Draft> decide(RuleEngine.Outcome rule, Prediction ml) {
        List<Draft> drafts = new ArrayList<Draft>();
        String ruleLabel = rule.getLabel();
        if (!Labels.SAFE.equals(ruleLabel)) {
            List<EvidenceItem> evidence = new ArrayList<EvidenceItem>();
            evidence.add(new EvidenceItem("rule", rule.getReason()));
            evidence.addAll(rule.getEvidence());
            if (ml != null) {
                evidence.add(new EvidenceItem("model", describe(ml)));
            }
            drafts.add(new Draft("RULE", ruleLabel, ml == null ? null : ml.getLeakProbability(), evidence));
        }
        boolean ruleSawDanger = Labels.isLeak(ruleLabel) || Labels.FLAME_DETECTED.equals(ruleLabel)
                || Labels.SENSOR_FAULT.equals(ruleLabel);
        if (ml != null && !ruleSawDanger && Labels.isLeak(ml.getLabel()) && ml.getConfidence() >= ML_ALERT_CONFIDENCE) {
            List<EvidenceItem> evidence = new ArrayList<EvidenceItem>();
            evidence.add(new EvidenceItem("model", describe(ml)));
            evidence.add(new EvidenceItem("rule", "rules gave " + ruleLabel + ": " + rule.getReason()));
            evidence.addAll(rule.getEvidence());
            drafts.add(new Draft("ML", ml.getLabel(), ml.getLeakProbability(), evidence));
        }
        return drafts;
    }

    private static String describe(Prediction ml) {
        return String.format(Locale.ROOT, "%s predicts %s (p=%.2f, leak probability %.2f)",
                ml.getModelName(), ml.getLabel(), ml.getConfidence(), ml.getLeakProbability());
    }

    /** An alert to raise: source RULE or ML, label, risk score and evidence. */
    public static final class Draft {

        private final String source;
        private final String label;
        private final Double riskScore;
        private final List<EvidenceItem> evidence;

        Draft(String source, String label, Double riskScore, List<EvidenceItem> evidence) {
            this.source = source;
            this.label = label;
            this.riskScore = riskScore;
            this.evidence = evidence;
        }

        public String getSource() {
            return source;
        }

        public String getLabel() {
            return label;
        }

        public Double getRiskScore() {
            return riskScore;
        }

        public List<EvidenceItem> getEvidence() {
            return evidence;
        }
    }
}
