package com.anchor.common.persistence.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/** JSONB 与 Map 互转。P1 表还没有 JSONB 列，P3 多跳路径会用到。 */
public class JsonbMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int index, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        PGobject json = new PGobject();
        json.setType("jsonb");
        json.setValue(JsonbCodec.write(parameter));
        ps.setObject(index, json);
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return JsonbCodec.read(rs.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return JsonbCodec.read(rs.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return JsonbCodec.read(cs.getString(columnIndex));
    }

    public static final class JsonbCodec {
        private JsonbCodec() {
        }

        public static String write(Map<String, Object> value) {
            try {
                return MAPPER.writeValueAsString(value);
            } catch (JsonProcessingException ex) {
                throw new IllegalArgumentException("jsonb write failed", ex);
            }
        }

        public static Map<String, Object> read(String json) {
            if (json == null || json.isBlank()) {
                return null;
            }
            try {
                return MAPPER.readValue(json, TYPE);
            } catch (JsonProcessingException ex) {
                throw new IllegalArgumentException("jsonb read failed", ex);
            }
        }
    }
}
