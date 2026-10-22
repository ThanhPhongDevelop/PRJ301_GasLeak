package model;

/** Warning and alarm levels of one sensor type in one zone (v_ZoneThresholdDetail). */
public class ZoneThreshold {

    private int zoneThresholdId;
    private int kitchenZoneId;
    private String zoneName;
    private String homeName;
    private int sensorTypeId;
    private String sensorTypeName;
    private String unit;
    private double warnLevel;
    private double alarmLevel;
    private String updatedByName;
    private java.sql.Timestamp updatedAt;

    public int getZoneThresholdId() {
        return zoneThresholdId;
    }

    public void setZoneThresholdId(int zoneThresholdId) {
        this.zoneThresholdId = zoneThresholdId;
    }

    public int getKitchenZoneId() {
        return kitchenZoneId;
    }

    public void setKitchenZoneId(int kitchenZoneId) {
        this.kitchenZoneId = kitchenZoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getHomeName() {
        return homeName;
    }

    public void setHomeName(String homeName) {
        this.homeName = homeName;
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

    public double getWarnLevel() {
        return warnLevel;
    }

    public void setWarnLevel(double warnLevel) {
        this.warnLevel = warnLevel;
    }

    public double getAlarmLevel() {
        return alarmLevel;
    }

    public void setAlarmLevel(double alarmLevel) {
        this.alarmLevel = alarmLevel;
    }

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
}
