package model;

/** A device id with the API key just issued for it. The key exists only here: the database keeps its SHA-256. */
public class DeviceKey {

    private final int deviceId;
    private final String apiKey;

    public DeviceKey(int deviceId, String apiKey) {
        this.deviceId = deviceId;
        this.apiKey = apiKey;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public String getApiKey() {
        return apiKey;
    }
}
