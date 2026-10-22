package model;

/** Maintenance ticket with readable names (v_MaintenanceTicketDetail). */
public class MaintenanceTicket {

    private int ticketId;
    private Integer alertId;
    private String alertLabel;
    private int deviceId;
    private String deviceCode;
    private Integer assignedTo;
    private String assigneeName;
    private String note;
    private String status;
    private java.sql.Timestamp openedAt;
    private java.sql.Timestamp closedAt;

    public int getTicketId() {
        return ticketId;
    }

    public void setTicketId(int ticketId) {
        this.ticketId = ticketId;
    }

    public Integer getAlertId() {
        return alertId;
    }

    public void setAlertId(Integer alertId) {
        this.alertId = alertId;
    }

    public String getAlertLabel() {
        return alertLabel;
    }

    public void setAlertLabel(String alertLabel) {
        this.alertLabel = alertLabel;
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

    public Integer getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(Integer assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getAssigneeName() {
        return assigneeName;
    }

    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public java.sql.Timestamp getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(java.sql.Timestamp openedAt) {
        this.openedAt = openedAt;
    }

    public java.sql.Timestamp getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(java.sql.Timestamp closedAt) {
        this.closedAt = closedAt;
    }
}
