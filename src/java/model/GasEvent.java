package model;

/** One session as shown on the session pages (v_GasEventDetail). */
public class GasEvent {

    private long sessionId;
    private int deviceId;
    private String deviceCode;
    private int deviceSeq;
    private java.sql.Timestamp measuredAt;
    private java.sql.Timestamp ingestedAt;
    private Double mq2Raw;
    private Double mq135Raw;
    private Double tempC;
    private Double rhPct;
    private Double compensatedScore;
    private Double mq135Compensated;
    private Boolean flameDetected;
    private String ruleLabel;
    private String mlLabel;
    private Double mlConfidence;
    private String humanLabel;
    private java.sql.Timestamp labeledAt;
    private String labeledByName;
    private int alertCount;

    /** Change made by the compensation, for the before and after table. */
    public Double getCompensationDelta() {
        return mq2Raw == null || compensatedScore == null ? null : compensatedScore - mq2Raw;
    }

    public String getRuleSeverity() {
        return Labels.severityOf(ruleLabel);
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }

    public int getDeviceSeq() {
        return deviceSeq;
    }

    public void setDeviceSeq(int deviceSeq) {
        this.deviceSeq = deviceSeq;
    }

    public java.sql.Timestamp getMeasuredAt() {
        return measuredAt;
    }

    public void setMeasuredAt(java.sql.Timestamp measuredAt) {
        this.measuredAt = measuredAt;
    }

    public java.sql.Timestamp getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(java.sql.Timestamp ingestedAt) {
        this.ingestedAt = ingestedAt;
    }

    public Double getMq2Raw() {
        return mq2Raw;
    }

    public void setMq2Raw(Double mq2Raw) {
        this.mq2Raw = mq2Raw;
    }

    public Double getMq135Raw() {
        return mq135Raw;
    }

    public void setMq135Raw(Double mq135Raw) {
        this.mq135Raw = mq135Raw;
    }

    public Double getTempC() {
        return tempC;
    }

    public void setTempC(Double tempC) {
        this.tempC = tempC;
    }

    public Double getRhPct() {
        return rhPct;
    }

    public void setRhPct(Double rhPct) {
        this.rhPct = rhPct;
    }

    public Double getCompensatedScore() {
        return compensatedScore;
    }

    public void setCompensatedScore(Double compensatedScore) {
        this.compensatedScore = compensatedScore;
    }

    public Double getMq135Compensated() {
        return mq135Compensated;
    }

    public void setMq135Compensated(Double mq135Compensated) {
        this.mq135Compensated = mq135Compensated;
    }

    public Boolean getFlameDetected() {
        return flameDetected;
    }

    public void setFlameDetected(Boolean flameDetected) {
        this.flameDetected = flameDetected;
    }

    public String getRuleLabel() {
        return ruleLabel;
    }

    public void setRuleLabel(String ruleLabel) {
        this.ruleLabel = ruleLabel;
    }

    public String getMlLabel() {
        return mlLabel;
    }

    public void setMlLabel(String mlLabel) {
        this.mlLabel = mlLabel;
    }

    public Double getMlConfidence() {
        return mlConfidence;
    }

    public void setMlConfidence(Double mlConfidence) {
        this.mlConfidence = mlConfidence;
    }

    public String getHumanLabel() {
        return humanLabel;
    }

    public void setHumanLabel(String humanLabel) {
        this.humanLabel = humanLabel;
    }

    public java.sql.Timestamp getLabeledAt() {
        return labeledAt;
    }

    public void setLabeledAt(java.sql.Timestamp labeledAt) {
        this.labeledAt = labeledAt;
    }

    public String getLabeledByName() {
        return labeledByName;
    }

    public void setLabeledByName(String labeledByName) {
        this.labeledByName = labeledByName;
    }

    public int getAlertCount() {
        return alertCount;
    }

    public void setAlertCount(int alertCount) {
        this.alertCount = alertCount;
    }
}
