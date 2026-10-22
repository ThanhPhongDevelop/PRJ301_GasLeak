package util;

/** Builds CSV rows for the export buttons. */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static String row(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cell(values[i]));
        }
        return sb.append("\r\n").toString();
    }

    public static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        // text starting with = + - @ would run as a formula in Excel, so neutralise it
        if (!(value instanceof Number) && !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
