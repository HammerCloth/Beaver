package com.zero.mapper.typehandler;

import com.zero.domain.FxRateSource;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(FxRateSource.class)
public class FxRateSourceTypeHandler extends CodedEnumTypeHandler<FxRateSource> {
  public FxRateSourceTypeHandler() {
    super(FxRateSource.class);
  }
}
