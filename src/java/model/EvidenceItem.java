package model;

/** One line of evidence attached to an alert, for example "mq2_compensated" = "2710.4 >= alarm level 2500". */
public class EvidenceItem {

    private final String item;
    private final String value;

    public EvidenceItem(String item, String value) {
        this.item = item.length() > 60 ? item.substring(0, 60) : item;
        this.value = value == null ? "" : value.length() > 200 ? value.substring(0, 200) : value;
    }

    public String getItem() {
        return item;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return item + "=" + value;
    }
}
