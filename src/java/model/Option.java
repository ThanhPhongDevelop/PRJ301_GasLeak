package model;

/** Id and label for a select box. */
public class Option {

    private final int id;
    private final String label;

    public Option(int id, String label) {
        this.id = id;
        this.label = label;
    }

    public int getId() {
        return id;
    }

    /** The id as text, so JSP pages compare it with raw form values without number parsing. */
    public String getValue() {
        return String.valueOf(id);
    }

    public String getLabel() {
        return label;
    }
}
