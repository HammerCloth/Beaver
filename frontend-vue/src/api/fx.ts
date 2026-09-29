import type { ForeignCurrency } from '@/lib/currency'
import { t, te } from '@/i18n'
import http from './http'

export interface FxQuote {
  /** 1 单位外币折合人民币；null 表示获取失败，需要手动填写 */
  rate: number | null
  source: 'API' | 'MANUAL' | 'FALLBACK' | null
  /** 自动获取时的数据源，手动填写为 null */
  provider: string | null
  providerLabel: string | null
  /** 汇率实际所属日期，fallback 时早于查询日期 */
  rateDate: string | null
  fallback: boolean
}

/** 汇率来源的简短说明，例如「欧洲央行」「手动填写」 */
export function fxSourceText(q: FxQuote | undefined) {
  if (!q || q.rate == null) return t('fx.source.none')
  if (q.source === 'MANUAL') return t('fx.source.manual')
  // 后端 providerLabel 是中文，按数据源枚举取当前语言的名称，未知数据源再退回后端文案
  const key = q.provider ? `fx.provider.${q.provider}` : ''
  const label = key && te(key) ? t(key) : (q.providerLabel ?? t('fx.source.auto'))
  return q.fallback ? t('fx.source.fallback', { label, date: q.rateDate }) : label
}

export async function getFxRates(date: string) {
  const { data } = await http.get<{ date: string; base: string; rates: Record<ForeignCurrency, FxQuote> }>(
    '/api/v1/fx/rates',
    { params: { date } },
  )
  return data.rates
}
