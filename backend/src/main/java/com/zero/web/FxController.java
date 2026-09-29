package com.zero.web;

import com.zero.domain.Currency;
import com.zero.service.FxRateService;
import com.zero.service.FxRateService.Quote;
import com.zero.support.CurrentUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fx")
public class FxController {

  private final FxRateService fxRateService;

  public FxController(FxRateService fxRateService) {
    this.fxRateService = fxRateService;
  }

  /** 某一天所有外币兑本币的汇率，rate 为 null 表示获取失败需手动填写 */
  @GetMapping("/rates")
  public Map<String, Object> rates(@RequestParam String date) {
    CurrentUser.require();
    Map<String, Object> rates = new LinkedHashMap<>();
    for (Currency c : Currency.foreign()) {
      Quote q = fxRateService.quote(c, date);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("rate", q.rate());
      row.put("source", q.source());
      row.put("provider", q.provider());
      row.put("providerLabel", q.provider() == null ? null : q.provider().label());
      row.put("rateDate", q.rateDate());
      row.put("fallback", q.fallback());
      rates.put(c.name(), row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("date", date);
    out.put("base", Currency.BASE);
    out.put("rates", rates);
    return out;
  }
}
