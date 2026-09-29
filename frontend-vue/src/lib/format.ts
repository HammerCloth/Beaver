import type { CurrencyCode } from './currency'
import { currentLocale } from '@/i18n'

const formatters = new Map<string, Intl.NumberFormat>()

/** 按界面语言格式化金额；人民币在英文界面下显示为 ¥ 而不是 CN¥ */
export function formatMoney(n: number, currency: CurrencyCode = 'CNY') {
  const locale = currentLocale()
  const cacheKey = `${locale}:${currency}`
  let f = formatters.get(cacheKey)
  if (!f) {
    f = new Intl.NumberFormat(locale, {
      style: 'currency',
      currency,
      currencyDisplay: currency === 'CNY' ? 'narrowSymbol' : 'symbol',
      maximumFractionDigits: 0,
    })
    formatters.set(cacheKey, f)
  }
  return f.format(n)
}

/** 带正负号的金额，例如 +¥1,200 / -¥300；0 显示为 ¥0 */
export function formatSignedMoney(n: number) {
  if (!n) {
    return formatMoney(0)
  }
  return `${n > 0 ? '+' : '-'}${formatMoney(Math.abs(n))}`
}

/** 金额对应的语气：positive / negative / 空 */
export function amountTone(n: number): 'positive' | 'negative' | '' {
  return n > 0 ? 'positive' : n < 0 ? 'negative' : ''
}
