package util;

import java.sql.Timestamp;
import java.util.Locale;
import model.Labels;

/** EL functions for the JSP pages (declared in WEB-INF/gasguard.tld, prefix gg). */
public final class ViewFunctions {

    private ViewFunctions() {
    }

    /** MINOR_LEAK -> Minor leak. */
    public static String label(String code) {
        return Labels.displayNameOf(code);
    }

    /** CSS suffix for a label: ok, info, warning or critical. */
    public static String severity(String label) {
        if (label == null || label.isEmpty()) {
            return "none";
        }
        return Labels.SAFE.equals(label) ? "ok" : Labels.severityOf(label);
    }

    /** One decimal with a dot whatever the browser language, n/a for a missing value. */
    public static String num(Double value) {
        return value == null || value.isNaN() ? "n/a" : String.format(Locale.ROOT, "%.1f", value);
    }

    /** Signed, for the compensation change column. */
    public static String signed(Double value) {
        return value == null || value.isNaN() ? "n/a" : String.format(Locale.ROOT, "%+.1f", value);
    }

    /** Plain number for levels, offsets and scales: 2500.0 -> "2500", 0.98 -> "0.98", null -> "". */
    public static String plain(Double value) {
        return value == null || value.isNaN() || value.isInfinite() ? "" : FormUtils.formatNumber(value);
    }

    public static String pct(Double value) {
        return value == null || value.isNaN() ? "n/a" : String.format(Locale.ROOT, "%.0f%%", value * 100);
    }

    /** UTC time to the second. */
    public static String time(Timestamp time) {
        if (time == null) {
            return "";
        }
        String text = time.toString();
        return text.length() > 19 ? text.substring(0, 19) : text;
    }
}
