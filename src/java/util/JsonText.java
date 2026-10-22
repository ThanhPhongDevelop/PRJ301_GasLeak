package util;

/** Tiny JSON builder for audit entries, so diffs stay valid JSON without another library. */
public final class JsonText {

    private JsonText() {
    }

    /** object("a", 1, "b", "x") gives {"a":1,"b":"x"}; wrap nested JSON with raw(...). */
    public static String object(Object... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quote(String.valueOf(keyValues[i]))).append(':').append(value(keyValues[i + 1]));
        }
        return sb.append('}').toString();
    }

    public static Raw raw(String json) {
        return new Raw(json);
    }

    public static String quote(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : text.toCharArray()) {
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.append('"').toString();
    }

    private static String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Raw) {
            return ((Raw) value).json;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return quote(String.valueOf(value));
    }

    /** Marks a string that already is JSON. */
    public static final class Raw {

        private final String json;

        private Raw(String json) {
            this.json = json;
        }
    }
}
