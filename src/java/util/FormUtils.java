package util;

import java.math.BigDecimal;

/** Safe parsing of request parameters: bad input gives null instead of an exception. */
public final class FormUtils {

    private FormUtils() {
    }

    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static Integer parseInt(String value) {
        try {
            return value == null || value.trim().isEmpty() ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Finite numbers only: NaN and Infinity are rejected. */
    public static Double parseDouble(String value) {
        try {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            double number = Double.parseDouble(value.trim());
            return Double.isNaN(number) || Double.isInfinite(number) ? null : number;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int parsePage(String value) {
        Integer page = parseInt(value);
        return page == null || page < 1 ? 1 : page;
    }

    /** 300.0 -> "300", 12.5 -> "12.5" for form fields. */
    public static String formatNumber(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
