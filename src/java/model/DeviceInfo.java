package model;

/** Device with its control lock owner, for the control page and the dashboard. */
public class DeviceInfo {

    private int deviceId;
    private String deviceCode;
    private String location;
    private String firmware;
    private String status;
    private Integer controlOwner;
    private String controlOwnerName;
    private java.sql.Timestamp lastSeen;
    private boolean online;
    private String zoneNames;

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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getFirmware() {
        return firmware;
    }

    public void setFirmware(String firmware) {
        this.firmware = firmware;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getControlOwner() {
        return controlOwner;
    }

    public void setControlOwner(Integer controlOwner) {
        this.controlOwner = controlOwner;
    }

    public String getControlOwnerName() {
        return controlOwnerName;
    }

    public void setControlOwnerName(String controlOwnerName) {
        this.controlOwnerName = controlOwnerName;
    }

    public java.sql.Timestamp getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(java.sql.Timestamp lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getZoneNames() {
        return zoneNames;
    }

    public void setZoneNames(String zoneNames) {
        this.zoneNames = zoneNames;
    }
}
