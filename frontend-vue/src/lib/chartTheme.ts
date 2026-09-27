import { registerTheme } from 'echarts/core'

/**
 * 全站图表配色：以 X 蓝为首的 8 色分类色板，刻意避开红、绿两个色相（留给涨跌）。
 * 已用 dataviz 校验脚本验证（浅色背景）：明度 L 0.43–0.77、饱和度 ≥ 0.1、
 * 相邻色色盲 ΔE ≥ 13.7、正常视觉 ΔE ≥ 19.9。颜色按固定顺序分配给类别，不循环生成新色。
 */
export const chartPalette = [
  '#1a8cd8', // 蓝
  '#eb6834', // 橙
  '#00a3b8', // 青
  '#5b45c7', // 靛紫
  '#d99a00', // 琥珀
  '#c2489a', // 洋红
  '#8b8ff5', // 薰衣草
  '#a4672c', // 赭石
]

/** 涨跌色：国内习惯红涨绿跌，与 style.css 中的 --up / --down 一致 */
export const chartPositive = '#d9272e'
export const chartNegative = '#00873c'
/** 涨跌的浅色填充：大面积色块（柱子）用浅色，避免刺眼；数值标签仍用上面的深色 */
export const chartPositiveSoft = '#f4a5a8'
export const chartNegativeSoft = '#8fcca6'

export const CHART_THEME = 'beaver'

const axisLabel = { color: '#6b7885', fontSize: 11 }
const axisLine = { show: true, lineStyle: { color: '#eff3f4' } }
const splitLine = { show: true, lineStyle: { color: '#eff3f4' } }

registerTheme(CHART_THEME, {
  color: chartPalette,
  backgroundColor: 'transparent',
  textStyle: {
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif',
    color: '#536471',
  },
  categoryAxis: {
    axisLine,
    axisTick: { show: false },
    axisLabel,
    splitLine: { show: false },
  },
  valueAxis: {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel,
    splitLine,
  },
  line: {
    symbol: 'circle',
    symbolSize: 5,
    showSymbol: false,
    lineStyle: { width: 2 },
  },
  bar: {
    itemStyle: { borderRadius: [4, 4, 0, 0] },
  },
  pie: {
    itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
  },
  legend: {
    icon: 'circle',
    itemWidth: 8,
    itemHeight: 8,
    itemGap: 14,
    textStyle: { color: '#536471', fontSize: 12 },
  },
  tooltip: {
    backgroundColor: '#ffffff',
    borderColor: '#eff3f4',
    borderWidth: 1,
    padding: [8, 12],
    textStyle: { color: '#0f1419', fontSize: 12 },
    extraCssText: 'border-radius: 8px; box-shadow: 0 8px 24px rgba(16, 24, 40, 0.08);',
  },
})
