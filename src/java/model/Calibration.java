package model;

/**
 * Calibration of one sensor type on one device: corrected = raw * scale + offset (v_CalibrationDetail).
 * The version counts the calibrations of that device and sensor type.
 */
public class Calibration {

    private int calibId;
    private int deviceId;
    private String deviceCode;
    private int sensorTypeId;
    private String sensorTypeName;
    private String unit;
    private double offsetVal;
    private double scaleVal = 1;
    private int version;
    private java.sql.Date validUntil;
    private Integer createdBy;
    private String createdByName;
    private boolean expired;

    public int getCalibId() {
        return calibId;
    }

    public void setCalibId(int calibId) {
        this.calibId = calibId;
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

    public int getSensorTypeId() {
        return sensorTypeId;
    }

    public void setSensorTypeId(int sensorTypeId) {
        this.sensorTypeId = sensorTypeId;
    }

    public String getSensorTypeName() {
        return sensorTypeName;
    }

    public void setSensorTypeName(String sensorTypeName) {
        this.sensorTypeName = sensorTypeName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public double getOffsetVal() {
        return offsetVal;
    }

    public void setOffsetVal(double offsetVal) {
        this.offsetVal = offsetVal;
    }

    public double getScaleVal() {
        return scaleVal;
    }

    public void setScaleVal(double scaleVal) {
        this.scaleVal = scaleVal;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    /** Last day the calibration is valid; null when it does not expire. */
    public java.sql.Date getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(java.sql.Date validUntil) {
        this.validUntil = validUntil;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public boolean isExpired() {
        return expired;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }
}
