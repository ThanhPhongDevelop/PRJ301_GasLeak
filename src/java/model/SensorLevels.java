package model;

/** Warning and alarm levels of one sensor type in the zone where a device's sensor is installed. */
public class SensorLevels {

    private final String sensorName;
    private final int zoneId;
    private final String zoneName;
    private final double warnLevel;
    private final double alarmLevel;

    public SensorLevels(String sensorName, int zoneId, String zoneName, double warnLevel, double alarmLevel) {
        this.sensorName = sensorName;
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.warnLevel = warnLevel;
        this.alarmLevel = alarmLevel;
    }

    public String getSensorName() {
        return sensorName;
    }

    public int getZoneId() {
        return zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public double getWarnLevel() {
        return warnLevel;
    }

    public double getAlarmLevel() {
        return alarmLevel;
    }
}
