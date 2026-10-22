package model;

import service.CompensationModel;

/**
 * One saved version of a device's compensation profile (v_OperatingProfileDetail). The newest version
 * of a device is the one in use; older ones are history.
 */
public class OperatingProfile {

    private int profileId;
    private int deviceId;
    private String deviceCode;
    private String paramsJson;
    private Integer updatedBy;
    private String updatedByName;
    private java.sql.Timestamp updatedAt;
    private boolean current;
    private CompensationModel model; // parsed from paramsJson on first use

    public int getProfileId() {
        return profileId;
    }

    public void setProfileId(int profileId) {
        this.profileId = profileId;
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

    public String getParamsJson() {
        return paramsJson;
    }

    public void setParamsJson(String paramsJson) {
        this.paramsJson = paramsJson;
        this.model = null;
    }

    public Integer getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Integer updatedBy) {
        this.updatedBy = updatedBy;
    }

    /** Null when the profile was saved by a tool (for example CompensationFitter). */
    public String getUpdatedByName() {
        return updatedByName;
    }

    public void setUpdatedByName(String updatedByName) {
        this.updatedByName = updatedByName;
    }

    public java.sql.Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.sql.Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** True for the newest version of the device: the one the compensation uses. */
    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

    /** The coefficients read from params_json (no correction when the JSON is unreadable). */
    public CompensationModel getModel() {
        if (model == null) {
            model = CompensationModel.fromJson(paramsJson);
        }
        return model;
    }

    /** One line for the list page, for example "MQ-2 -12.3 per °C, ...". */
    public String getSummary() {
        return getModel().describe();
    }

    /** "fitted from data", "manual" or "not fitted". */
    public String getSource() {
        CompensationModel m = getModel();
        if (!m.isFitted()) {
            return "not fitted";
        }
        return CompensationModel.MANUAL.equals(m.getSource()) ? "manual" : "fitted from data";
    }
}
