package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/** Small helpers shared by the DAO classes. */
public abstract class BaseDao {

    /** Binds the values in order starting at index 1 and returns the next free index. */
    protected static int bind(PreparedStatement ps, List<Object> params) throws SQLException {
        int index = 1;
        for (Object value : params) {
            ps.setObject(index++, value);
        }
        return index;
    }

    protected static int count(Connection conn, String sql, List<Object> params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    protected static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    protected static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    protected static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    protected static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null || value.isNaN() || value.isInfinite()) {
            ps.setNull(index, Types.FLOAT);
        } else {
            ps.setDouble(index, value);
        }
    }

    protected static void setNullableBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIT);
        } else {
            ps.setBoolean(index, value);
        }
    }

    protected static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NVARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    protected static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    protected static Boolean getNullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * The id parameter of a *_Save procedure is both input and output: NULL creates a row, an id
     * changes that row, and after the call it holds the id of the row written.
     */
    protected static void bindSaveId(java.sql.CallableStatement cs, int index, int id) throws SQLException {
        if (id <= 0) {
            cs.setNull(index, Types.INTEGER);
        } else {
            cs.setInt(index, id);
        }
        cs.registerOutParameter(index, Types.INTEGER);
    }

    /** SQL Server error 547: a foreign key or check constraint blocked the statement. */
    public static boolean isConstraintViolation(SQLException e) {
        return e.getErrorCode() == 547;
    }

    /** Errors 50000 and above come from THROW in our stored procedures and are safe to show. */
    public static boolean isBusinessRule(SQLException e) {
        return e.getErrorCode() >= 50000;
    }
}
