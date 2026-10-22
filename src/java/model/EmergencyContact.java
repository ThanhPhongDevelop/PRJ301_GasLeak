package model;

/** Person to call for a home, and the least serious alert they should hear about. */
public class EmergencyContact {

    private int contactId;
    private int homeId;
    private String homeName;
    private String fullName;
    private String phone;
    private String email;
    private String relation;
    private int priority;
    private String notifyMinLabel;
    private java.sql.Timestamp createdAt;

    public String getNotifyMinLabelDisplay() {
        return Labels.displayNameOf(notifyMinLabel);
    }

    public int getContactId() {
        return contactId;
    }

    public void setContactId(int contactId) {
        this.contactId = contactId;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getNotifyMinLabel() {
        return notifyMinLabel;
    }

    public void setNotifyMinLabel(String notifyMinLabel) {
        this.notifyMinLabel = notifyMinLabel;
    }

    public java.sql.Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.sql.Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
