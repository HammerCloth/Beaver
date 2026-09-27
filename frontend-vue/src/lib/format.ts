export function formatMoney(n: number) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    maximumFractionDigits: 0,
  }).format(n)
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
