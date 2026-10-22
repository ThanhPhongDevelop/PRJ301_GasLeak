package model;

/** One physical sensor on a device, placed in a kitchen zone (v_GasSensorDetail). */
public class GasSensor {

    private int gasSensorId;
    private String sensorCode;
    private int deviceId;
    private String deviceCode;
    private int sensorTypeId;
    private String sensorTypeName;
    private String unit;
    private Integer kitchenZoneId;
    private String zoneName;
    private String pinLabel;
    private java.sql.Date installedAt;
    private String status;

    public int getGasSensorId() {
        return gasSensorId;
    }

    public void setGasSensorId(int gasSensorId) {
        this.gasSensorId = gasSensorId;
    }

    public String getSensorCode() {
        return sensorCode;
    }

    public void setSensorCode(String sensorCode) {
        this.sensorCode = sensorCode;
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

    public Integer getKitchenZoneId() {
        return kitchenZoneId;
    }

    public void setKitchenZoneId(Integer kitchenZoneId) {
        this.kitchenZoneId = kitchenZoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getPinLabel() {
        return pinLabel;
    }

    public void setPinLabel(String pinLabel) {
        this.pinLabel = pinLabel;
    }

    public java.sql.Date getInstalledAt() {
        return installedAt;
    }

    public void setInstalledAt(java.sql.Date installedAt) {
        this.installedAt = installedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
