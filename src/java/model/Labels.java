package model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The six session labels of the topic, in a fixed order shared by training and run time. */
public final class Labels {

    public static final String SAFE = "SAFE";
    public static final String COOKING_FUMES = "COOKING_FUMES";
    public static final String MINOR_LEAK = "MINOR_LEAK";
    public static final String MAJOR_LEAK = "MAJOR_LEAK";
    public static final String FLAME_DETECTED = "FLAME_DETECTED";
    public static final String SENSOR_FAULT = "SENSOR_FAULT";

    /** Fixed order: every Weka dataset is built with this class attribute, so indexes always match. */
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            SAFE, COOKING_FUMES, MINOR_LEAK, MAJOR_LEAK, FLAME_DETECTED, SENSOR_FAULT));

    private Labels() {
    }

    public static boolean isValid(String label) {
        return label != null && ALL.contains(label);
    }

    public static boolean isLeak(String label) {
        return MINOR_LEAK.equals(label) || MAJOR_LEAK.equals(label);
    }

    /** critical: act now; warning: check soon; info: no danger expected. */
    public static String severityOf(String label) {
        if (FLAME_DETECTED.equals(label) || MAJOR_LEAK.equals(label)) {
            return "critical";
        }
        if (MINOR_LEAK.equals(label) || SENSOR_FAULT.equals(label)) {
            return "warning";
        }
        return "info";
    }

    /** Readable name for the interface, for example MINOR_LEAK becomes Minor leak. */
    public static String displayNameOf(String label) {
        if (label == null) {
            return "";
        }
        String text = label.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
