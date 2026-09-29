package com.zero.service;

import com.zero.domain.Account;
import com.zero.domain.SnapshotItem;
import java.util.List;
import java.util.Map;

public final class BalanceLogic {

  private BalanceLogic() {}

  /** 存储前：负债账户正数余额转为负数 */
  public static double normalizeStoredBalance(String accountType, double input) {
    if ("credit".equals(accountType) && input > 0) {
      return -input;
    }
    return input;
  }

  /** 计算净资产（本币）：负债按负值计入，外币按快照日期汇率折算 */
  public static double netWorth(List<SnapshotItem> items, Map<String, Account> accountsById, FxTable fx, String date) {
    double sum = 0;
    for (SnapshotItem it : items) {
      Account a = accountsById.get(it.getAccountId());
      if (a == null) {
        continue;
      }
      sum += baseBalance(a.getType(), it, fx, date);
    }
    return sum;
  }

  public static double effectiveBalance(String accountType, double stored) {
    return "credit".equals(accountType) ? -Math.abs(stored) : stored;
  }

  /** 折合本币后的有效余额 */
  public static double baseBalance(String accountType, SnapshotItem it, FxTable fx, String date) {
    return effectiveBalance(accountType, it.getBalance()) * fx.rate(it.getCurrency(), date);
  }
}
