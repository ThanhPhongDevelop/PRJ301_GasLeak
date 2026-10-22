package model;

/** A sensor type, its valid range and how many records use it (v_SensorTypeDetail). */
public class SensorType {

    private int sensorTypeId;
    private String name;
    private String unit;
    private Double minValid;
    private Double maxValid;
    private boolean builtIn;
    private int sensorCount;
    private int thresholdCount;
    private int calibrationCount;

    public int getSensorTypeId() {
        return sensorTypeId;
    }

    public void setSensorTypeId(int sensorTypeId) {
        this.sensorTypeId = sensorTypeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    /** Lowest valid reading; null when the type has no range check. */
    public Double getMinValid() {
        return minValid;
    }

    public void setMinValid(Double minValid) {
        this.minValid = minValid;
    }

    public Double getMaxValid() {
        return maxValid;
    }

    public void setMaxValid(Double maxValid) {
        this.maxValid = maxValid;
    }

    /** The rules find this type by its name, so it cannot be renamed or deleted. */
    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    public int getSensorCount() {
        return sensorCount;
    }

    public void setSensorCount(int sensorCount) {
        this.sensorCount = sensorCount;
    }

    public int getThresholdCount() {
        return thresholdCount;
    }

    public void setThresholdCount(int thresholdCount) {
        this.thresholdCount = thresholdCount;
    }

    public int getCalibrationCount() {
        return calibrationCount;
    }

    public void setCalibrationCount(int calibrationCount) {
        this.calibrationCount = calibrationCount;
    }

    /** Hide the delete button when true: the type is built in or still used. */
    public boolean isLocked() {
        return builtIn || sensorCount + thresholdCount + calibrationCount > 0;
    }
}
