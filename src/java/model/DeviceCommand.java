package model;

/** Actuator command queued for a device. */
public class DeviceCommand {

    private long commandId;
    private int deviceId;
    private String deviceCode;
    private String command;
    private String issuedByName;
    private java.sql.Timestamp issuedAt;
    private String status;
    private java.sql.Timestamp deliveredAt;

    /** Readable command name, for example CLOSE_VALVE becomes Close valve. */
    public String getCommandDisplay() {
        return Labels.displayNameOf(command);
    }

    public long getCommandId() {
        return commandId;
    }

    public void setCommandId(long commandId) {
        this.commandId = commandId;
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

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getIssuedByName() {
        return issuedByName;
    }

    public void setIssuedByName(String issuedByName) {
        this.issuedByName = issuedByName;
    }

    public java.sql.Timestamp getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(java.sql.Timestamp issuedAt) {
        this.issuedAt = issuedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public java.sql.Timestamp getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(java.sql.Timestamp deliveredAt) {
        this.deliveredAt = deliveredAt;
    }
}
