package com.zero.mapper.typehandler;

import com.zero.domain.CodedEnum;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** 枚举与数据库整数编码互转 */
public abstract class CodedEnumTypeHandler<E extends Enum<E> & CodedEnum> extends BaseTypeHandler<E> {

  private final Class<E> type;

  protected CodedEnumTypeHandler(Class<E> type) {
    this.type = type;
  }

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setInt(i, parameter.code());
  }

  @Override
  public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
    int code = rs.getInt(columnName);
    return rs.wasNull() ? null : fromCode(code);
  }

  @Override
  public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    int code = rs.getInt(columnIndex);
    return rs.wasNull() ? null : fromCode(code);
  }

  @Override
  public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    int code = cs.getInt(columnIndex);
    return cs.wasNull() ? null : fromCode(code);
  }

  private E fromCode(int code) {
    for (E e : type.getEnumConstants()) {
      if (e.code() == code) {
        return e;
      }
    }
    throw new IllegalArgumentException("未知的 " + type.getSimpleName() + " 编码: " + code);
  }
}
