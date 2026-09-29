/** 与后端 com.zero.domain.Currency 一致；本币为人民币 */
export type CurrencyCode = 'CNY' | 'USD' | 'HKD'
export type ForeignCurrency = Exclude<CurrencyCode, 'CNY'>

export const BASE_CURRENCY: CurrencyCode = 'CNY'

export const CURRENCIES: { code: CurrencyCode; symbol: string; label: string }[] = [
  { code: 'CNY', symbol: '¥', label: '人民币' },
  { code: 'USD', symbol: '$', label: '美元' },
  { code: 'HKD', symbol: 'HK$', label: '港币' },
]

export const FOREIGN_CURRENCIES: ForeignCurrency[] = ['USD', 'HKD']

export function currencySymbol(code: CurrencyCode) {
  return CURRENCIES.find((c) => c.code === code)?.symbol ?? ''
}

export function currencyLabel(code: CurrencyCode) {
  return CURRENCIES.find((c) => c.code === code)?.label ?? code
}
