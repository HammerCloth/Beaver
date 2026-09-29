export default {
  currency: {
    CNY: '人民币',
    USD: '美元',
    HKD: '港币',
  },
  source: {
    none: '暂无汇率',
    manual: '手动填写',
    auto: '自动获取',
    fallback: '{label} · 沿用 {date}',
  },
  // 与后端 FxProvider.label 保持一致
  provider: {
    FRANKFURTER: '欧洲央行',
    CURRENCY_API_JSDELIVR: 'currency-api',
    CURRENCY_API_CLOUDFLARE: 'currency-api 镜像',
    EXCHANGE_RATE_API: 'ExchangeRate-API',
  },
}
