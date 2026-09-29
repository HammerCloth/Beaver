-- 币种按 ISO 4217 数字码存储（对应 com.zero.domain.Currency）：156=CNY，840=USD，344=HKD
-- 快照明细记录原币种，balance 为原币金额；存量数据均为人民币
ALTER TABLE snapshot_items ADD COLUMN currency INTEGER NOT NULL DEFAULT 156 CHECK (currency IN (156, 840, 344));

-- 每日汇率：1 单位 currency = rate 人民币；折算时按快照日期取当天或之前最近一天的汇率
-- source 对应 com.zero.domain.FxRateSource：1=API 自动获取，2=手动填写，3=接口不可用时沿用的更早汇率
-- provider 对应 com.zero.domain.FxProvider：1=欧洲央行 Frankfurter，2=currency-api jsDelivr，3=currency-api Cloudflare，4=ExchangeRate-API；手动填写为 NULL
CREATE TABLE IF NOT EXISTS fx_rates (
  currency INTEGER NOT NULL CHECK (currency IN (840, 344)),
  rate_date TEXT NOT NULL,
  rate REAL NOT NULL CHECK (rate > 0),
  source INTEGER NOT NULL CHECK (source IN (1, 2, 3)),
  provider INTEGER CHECK (provider IS NULL OR provider IN (1, 2, 3, 4)),
  fetched_at TEXT NOT NULL,
  PRIMARY KEY (currency, rate_date)
);
