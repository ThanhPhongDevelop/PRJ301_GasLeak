package model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Actuator commands the control page offers (same values as the DeviceCommand check constraint). */
public class CommandType {

    public static final List<CommandType> ALL = Collections.unmodifiableList(Arrays.asList(
            new CommandType("CLOSE_VALVE", "Close gas valve", "Close the gas valve? The stove will go out.", false),
            new CommandType("OPEN_VALVE", "Open gas valve",
                    "Open the gas valve? Only do this after checking the kitchen is safe.", true),
            new CommandType("FAN_ON", "Turn exhaust fan on", "Turn the exhaust fan on?", false),
            new CommandType("FAN_OFF", "Turn exhaust fan off", "Turn the exhaust fan off?", false),
            new CommandType("SILENCE_BUZZER", "Silence buzzer",
                    "Silence the buzzer? The alert stays open until someone resolves it.", false)));

    private final String code;
    private final String name;
    private final String confirmText;
    private final boolean risky;

    public CommandType(String code, String name, String confirmText, boolean risky) {
        this.code = code;
        this.name = name;
        this.confirmText = confirmText;
        this.risky = risky;
    }

    public static boolean isValid(String code) {
        for (CommandType type : ALL) {
            if (type.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getConfirmText() {
        return confirmText;
    }

    /** Shown in red: it can make the kitchen less safe. */
    public boolean isRisky() {
        return risky;
    }
}
