<script setup lang="ts">
import { computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { formatMoney } from '@/lib/format'
import { t } from '@/i18n'

use([CanvasRenderer, PieChart, TooltipComponent])

export interface DonutItem {
  /** 稳定标识（账户 id、类别 key 等）；名称可能重复时必须提供 */
  id?: string
  name: string
  /** 带符号金额，用于展示 */
  value: number
  color: string
}

const props = withDefaults(
  defineProps<{
    items: DonutItem[]
    centerLabel?: string
    /** 中心展示的数值；默认取各项绝对值之和 */
    centerValue?: number
    formatValue?: (v: number) => string
    /** stack：环图在上、明细在下；split：环图在左、明细在右 */
    layout?: 'stack' | 'split'
    /** 明细行显示占比条 */
    bars?: boolean
    /** 明细行附加说明，例如「3 笔」 */
    meta?: (item: DonutItem) => string | undefined
  }>(),
  { centerLabel: () => t('components.donut.total'), centerValue: undefined, formatValue: formatMoney, layout: 'stack', bars: false, meta: undefined },
)

const rows = computed(() => {
  const list = props.items.filter((x) => Number.isFinite(x.value) && x.value !== 0)
  const total = list.reduce((sum, x) => sum + Math.abs(x.value), 0) || 1
  return [...list]
    .sort((a, b) => Math.abs(b.value) - Math.abs(a.value))
    .map((x) => ({ ...x, pct: (Math.abs(x.value) / total) * 100 }))
})

const center = computed(
  () => props.centerValue ?? rows.value.reduce((sum, x) => sum + Math.abs(x.value), 0),
)

const option = computed(() => ({
  tooltip: {
    trigger: 'item',
    formatter: (p: { name: string; data: { raw: number }; percent: number }) =>
      `${p.name}<br/>${props.formatValue(p.data.raw)} · ${p.percent.toFixed(1)}%`,
  },
  series: [
    {
      type: 'pie',
      radius: ['68%', '92%'],
      padAngle: 1.5,
      itemStyle: { borderRadius: 4, borderWidth: 0 },
      label: { show: false },
      labelLine: { show: false },
      emphasis: { scale: true, scaleSize: 4 },
      data: rows.value.map((x) => ({
        name: x.name,
        value: Math.abs(x.value),
        raw: x.value,
        itemStyle: { color: x.color },
      })),
    },
  ],
}))
</script>

<template>
  <div class="donut" :class="`donut--${layout}`">
    <div class="donut__chart">
      <v-chart :option="option" autoresize />
      <div class="donut__center">
        <span class="donut__center-label">{{ centerLabel }}</span>
        <span class="donut__center-value">{{ formatValue(center) }}</span>
      </div>
    </div>
    <ul class="donut__legend">
      <li v-for="row in rows" :key="row.id ?? row.name" class="donut__row">
        <span class="donut__dot" :style="{ background: row.color }" />
        <span class="donut__name">
          {{ row.name }}
          <small v-if="meta && meta(row)" class="donut__meta">{{ meta(row) }}</small>
        </span>
        <span class="donut__value">{{ formatValue(row.value) }}</span>
        <span class="donut__pct">{{ row.pct.toFixed(1) }}%</span>
        <span v-if="bars" class="donut__bar">
          <span class="donut__bar-fill" :style="{ width: `${row.pct}%`, background: row.color }" />
        </span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.donut {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.donut__chart {
  position: relative;
  width: 100%;
  max-width: 220px;
  height: 220px;
  margin: 0 auto;
}

.donut__chart > :first-child {
  width: 100%;
  height: 100%;
}

.donut__center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.donut__center-label {
  font-size: 12px;
  color: var(--text-3);
}

.donut__center-value {
  margin-top: 2px;
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.donut--split {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  align-items: center;
  gap: 32px;
}

.donut--split .donut__chart {
  margin: 0;
}

@media (max-width: 720px) {
  .donut--split {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .donut--split .donut__chart {
    margin: 0 auto;
  }
}

.donut__legend {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.donut__row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto 52px;
  align-items: center;
  gap: 6px 10px;
  padding: 8px 0;
  border-top: 1px solid var(--line-soft);
  font-size: 13px;
}

.donut__row:first-child {
  border-top: 0;
}

.donut__dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.donut__name {
  overflow: hidden;
  color: var(--text-2);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.donut__value {
  color: var(--text-1);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.donut__meta {
  margin-left: 6px;
  color: var(--text-3);
  font-size: 12px;
}

.donut__bar {
  grid-column: 2 / -1;
  height: 4px;
  margin-top: -2px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--surface-hover);
}

.donut__bar-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  opacity: 0.85;
}

.donut__pct {
  color: var(--text-3);
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
