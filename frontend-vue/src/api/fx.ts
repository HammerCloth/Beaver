import type { ForeignCurrency } from '@/lib/currency'
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
  if (!q || q.rate == null) return '暂无汇率'
  if (q.source === 'MANUAL') return '手动填写'
  const label = q.providerLabel ?? '自动获取'
  return q.fallback ? `${label} · 沿用 ${q.rateDate}` : label
}

export async function getFxRates(date: string) {
  const { data } = await http.get<{ date: string; base: string; rates: Record<ForeignCurrency, FxQuote> }>(
    '/api/v1/fx/rates',
    { params: { date } },
  )
  return data.rates
}
