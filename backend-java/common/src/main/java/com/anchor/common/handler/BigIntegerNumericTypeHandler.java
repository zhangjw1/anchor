package com.anchor.common.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 把链上大整数按数据库 NUMERIC 读写，避免超过 Long 后失真。
 */
public class BigIntegerNumericTypeHandler extends BaseTypeHandler<BigInteger> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int index, BigInteger parameter, JdbcType jdbcType) throws SQLException {
        ps.setBigDecimal(index, new BigDecimal(parameter));
    }

    @Override
    public BigInteger getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toBigInteger(rs.getBigDecimal(columnName));
    }

    @Override
    public BigInteger getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toBigInteger(rs.getBigDecimal(columnIndex));
    }

    @Override
    public BigInteger getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toBigInteger(cs.getBigDecimal(columnIndex));
    }

    private static BigInteger toBigInteger(BigDecimal value) {
        return value == null ? null : value.toBigInteger();
    }
}
