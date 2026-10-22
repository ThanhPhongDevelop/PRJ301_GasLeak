package model;

public class AuditEntry {

    private long auditId;
    private String ts; // ISO text produced by CONVERT(..., 126)
    private Integer actorUserId;
    private String actorUsername;
    private String action;
    private String objectType;
    private Long objectId;
    private String diffJson;
    private String rowHash;

    public String getTimeDisplay() {
        if (ts == null) {
            return "";
        }
        String text = ts.replace('T', ' ');
        return text.length() > 19 ? text.substring(0, 19) : text;
    }

    public String getDiffPreview() {
        if (diffJson == null) {
            return "";
        }
        return diffJson.length() > 90 ? diffJson.substring(0, 90) + "\u2026" : diffJson;
    }

    public String getShortHash() {
        return rowHash == null ? "-" : rowHash.substring(0, Math.min(12, rowHash.length()));
    }

    public long getAuditId() {
        return auditId;
    }

    public void setAuditId(long auditId) {
        this.auditId = auditId;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    public Integer getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Integer actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    public String getDiffJson() {
        return diffJson;
    }

    public void setDiffJson(String diffJson) {
        this.diffJson = diffJson;
    }

    public String getRowHash() {
        return rowHash;
    }

    public void setRowHash(String rowHash) {
        this.rowHash = rowHash;
    }
}
