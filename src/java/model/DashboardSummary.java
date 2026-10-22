package model;

public class DashboardSummary {

    private final int zoneCount;
    private final int activeDevices;
    private final int onlineDevices;
    private final int openAlerts;
    private final int urgentAlerts;
    private final int sessionsToday;

    public DashboardSummary(int zoneCount, int activeDevices, int onlineDevices, int openAlerts, int urgentAlerts,
            int sessionsToday) {
        this.zoneCount = zoneCount;
        this.activeDevices = activeDevices;
        this.onlineDevices = onlineDevices;
        this.openAlerts = openAlerts;
        this.urgentAlerts = urgentAlerts;
        this.sessionsToday = sessionsToday;
    }

    /** Unresolved leak, flame and sensor fault alerts (cooking fumes alerts are informational). */
    public int getUrgentAlerts() {
        return urgentAlerts;
    }

    public int getZoneCount() {
        return zoneCount;
    }

    public int getActiveDevices() {
        return activeDevices;
    }

    public int getOnlineDevices() {
        return onlineDevices;
    }

    public int getOpenAlerts() {
        return openAlerts;
    }

    public int getSessionsToday() {
        return sessionsToday;
    }
}
