package com.zero.mapper.typehandler;

import com.zero.domain.FxProvider;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(FxProvider.class)
public class FxProviderTypeHandler extends CodedEnumTypeHandler<FxProvider> {
  public FxProviderTypeHandler() {
    super(FxProvider.class);
  }
}
