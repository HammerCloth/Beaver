package com.zero.mapper.typehandler;

import com.zero.domain.Currency;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(Currency.class)
public class CurrencyTypeHandler extends CodedEnumTypeHandler<Currency> {
  public CurrencyTypeHandler() {
    super(Currency.class);
  }
}
