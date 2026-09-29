package com.zero.mapper;

import com.zero.domain.Currency;
import com.zero.domain.FxRate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FxRateMapper {

  @Select(
      "SELECT currency, rate_date as rateDate, rate, source, provider, fetched_at as fetchedAt FROM fx_rates "
          + "WHERE currency = #{currency} AND rate_date = #{rateDate}")
  FxRate find(@Param("currency") Currency currency, @Param("rateDate") String rateDate);

  @Select(
      "SELECT currency, rate_date as rateDate, rate, source, provider, fetched_at as fetchedAt FROM fx_rates "
          + "WHERE currency = #{currency} AND rate_date <= #{rateDate} ORDER BY rate_date DESC LIMIT 1")
  FxRate findLatestOnOrBefore(@Param("currency") Currency currency, @Param("rateDate") String rateDate);

  @Select("SELECT currency, rate_date as rateDate, rate, source, provider, fetched_at as fetchedAt FROM fx_rates")
  List<FxRate> listAll();

  @Insert(
      "INSERT INTO fx_rates(currency, rate_date, rate, source, provider, fetched_at) "
          + "VALUES(#{currency}, #{rateDate}, #{rate}, #{source}, #{provider,jdbcType=INTEGER}, #{fetchedAt}) "
          + "ON CONFLICT(currency, rate_date) DO UPDATE SET rate = excluded.rate, "
          + "source = excluded.source, provider = excluded.provider, fetched_at = excluded.fetched_at")
  int upsert(FxRate rate);
}
