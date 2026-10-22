package model;

import java.sql.Timestamp;
import util.JsonText;

public class KitchenZone {

    private int id;
    private String zoneName;
    private int homeId;
    private String homeName;
    private double thresholdPpm;
    private Integer valveDeviceId; // optional: not every zone has a valve
    private String valveDeviceCode;
    private Timestamp createdAt;

    /** Editable fields as JSON, written to the audit log on create, update and delete. */
    public String toJson() {
        return JsonText.object("zoneName", zoneName, "homeId", homeId,
                "thresholdPpm", thresholdPpm, "valveDeviceId", valveDeviceId);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public int getHomeId() {
        return homeId;
    }

    public void setHomeId(int homeId) {
        this.homeId = homeId;
    }

    public String getHomeName() {
        return homeName;
    }

    public void setHomeName(String homeName) {
        this.homeName = homeName;
    }

    public double getThresholdPpm() {
        return thresholdPpm;
    }

    public void setThresholdPpm(double thresholdPpm) {
        this.thresholdPpm = thresholdPpm;
    }

    public Integer getValveDeviceId() {
        return valveDeviceId;
    }

    public void setValveDeviceId(Integer valveDeviceId) {
        this.valveDeviceId = valveDeviceId;
    }

    public String getValveDeviceCode() {
        return valveDeviceCode;
    }

    public void setValveDeviceCode(String valveDeviceCode) {
        this.valveDeviceCode = valveDeviceCode;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
