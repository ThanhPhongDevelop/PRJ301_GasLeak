package tool;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import model.Labels;
import service.RiskScorer;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

/**
 * Builds Weka datasets for the tools. Unlike the workshop sample, the class attribute always lists
 * the six labels in the fixed order of model.Labels, so the public data, the device data and the
 * running application agree on what each class index means (the sample numbered labels in the order
 * they first appeared in each query, which breaks TransferCheck).
 */
public final class FeatureDataset {

    private FeatureDataset() {
    }

    /**
     * Reads labelled rows. The query must return the columns of RiskScorer.FEATURES and "label",
     * and may return "group_id" (the recording a row comes from, used for grouped cross-validation).
     */
    public static List<Row> load(Connection conn, String sql) throws SQLException {
        List<Row> rows = new ArrayList<Row>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            boolean hasGroup = hasColumn(rs.getMetaData(), "group_id");
            while (rs.next()) {
                String label = rs.getString("label");
                if (!Labels.isValid(label)) {
                    continue;
                }
                double[] values = new double[RiskScorer.FEATURES.length];
                boolean complete = true;
                for (int i = 0; i < values.length; i++) {
                    values[i] = rs.getDouble(RiskScorer.FEATURES[i]);
                    complete &= !rs.wasNull();
                }
                if (!complete) {
                    continue; // the model is trained on complete rows only
                }
                int group = hasGroup ? rs.getInt("group_id") : rows.size();
                rows.add(new Row(values, label, group));
            }
        }
        return rows;
    }

    /** Empty dataset with the feature attributes and the fixed label attribute. */
    public static Instances emptyHeader(String name) {
        ArrayList<Attribute> attributes = new ArrayList<Attribute>();
        for (String feature : RiskScorer.FEATURES) {
            attributes.add(new Attribute(feature));
        }
        attributes.add(new Attribute("label", new ArrayList<String>(Labels.ALL)));
        Instances data = new Instances(name, attributes, 0);
        data.setClassIndex(data.numAttributes() - 1);
        return data;
    }

    public static Instances toInstances(String name, List<Row> rows) {
        Instances data = emptyHeader(name);
        for (Row row : rows) {
            double[] values = new double[data.numAttributes()];
            System.arraycopy(row.features, 0, values, 0, row.features.length);
            values[values.length - 1] = Labels.ALL.indexOf(row.label);
            data.add(new DenseInstance(1.0, values));
        }
        return data;
    }

    private static boolean hasColumn(ResultSetMetaData meta, String name) throws SQLException {
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (name.equalsIgnoreCase(meta.getColumnLabel(i))) {
                return true;
            }
        }
        return false;
    }

    /** One labelled session: features in RiskScorer.FEATURES order, label, and recording id. */
    public static final class Row {

        private final double[] features;
        private final String label;
        private final int group;

        public Row(double[] features, String label, int group) {
            this.features = features.clone();
            this.label = label;
            this.group = group;
        }

        public double[] getFeatures() {
            return features.clone();
        }

        public double feature(int index) {
            return features[index];
        }

        public String getLabel() {
            return label;
        }

        public int getGroup() {
            return group;
        }
    }
}
