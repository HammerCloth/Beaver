<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { useRouter } from 'vue-router'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart, SankeyChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components'
import VChart, { THEME_KEY } from 'vue-echarts'
import { CHART_THEME, chartNegative, chartNegativeSoft, chartPalette, chartPositive, chartPositiveSoft } from '@/lib/chartTheme'
import * as accountApi from '@/api/account'
import * as dashboardApi from '@/api/dashboard'
import type { DashboardComposition, DashboardTypeChange } from '@/api/dashboard'
import { formatMoney } from '@/lib/format'
import { currentLocale, t } from '@/i18n'
import DonutBreakdown, { type DonutItem } from '@/components/DonutBreakdown.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useCategoryColor } from '@/composables/useCategoryColor'
import { DIM_ACCOUNT_OWNER, DIM_ACCOUNT_TYPE, useSettingsStore } from '@/stores/settings'

use([
  CanvasRenderer,
  LineChart,
  PieChart,
  BarChart,
  SankeyChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
])

provide(THEME_KEY, CHART_THEME)

const message = useMessage()
const router = useRouter()
const settings = useSettingsStore()
const loading = ref(true)
const isMobileChart = ref(false)
const summary = ref({
  netWorth: 0,
  monthlyChange: 0,
  annualChange: 0,
  annualizedReturn: 0,
})
const range = ref<'3m' | '6m' | '1y' | 'all'>('1y')
const trendPoints = ref<{ date: string; netWorth: number }[]>([])
const stackedPoints = ref<{ date: string; byType: Record<string, number> }[]>([])

const composition = ref<DashboardComposition>({
  byType: {},
  byOwner: {},
})
const typeChange = ref<DashboardTypeChange>({
  latestDate: null,
  previousDate: null,
  items: [],
})
/** 账户 id → 展示名（用于分账户占比图） */
const accountNameById = ref<Record<string, string>>({})

const { categoryColor } = useCategoryColor()
const typeColor = (key: string) => categoryColor(DIM_ACCOUNT_TYPE, key)

const trendOption = computed(() => ({
  grid: { left: 8, right: 12, top: 12, bottom: 4, containLabel: true },
  tooltip: {
    trigger: 'axis',
    valueFormatter: (v: number) => formatMoney(v),
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trendPoints.value.map((p) => p.date),
    axisLine: { show: false },
    axisLabel: { hideOverlap: true, margin: 12 },
  },
  yAxis: {
    type: 'value',
    scale: true,
    position: 'right',
    splitNumber: 3,
    axisLabel: { formatter: (v: number) => formatAssetAmount(v).replace('.00', '') },
  },
  series: [
    {
      type: 'line',
      smooth: 0.35,
      symbol: 'circle',
      symbolSize: 6,
      showSymbol: false,
      lineStyle: { width: 2.5, color: '#1d9bf0' },
      itemStyle: { color: '#1d9bf0' },
      data: trendPoints.value.map((p) => p.netWorth),
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(29, 155, 240, 0.2)' },
            { offset: 1, color: 'rgba(29, 155, 240, 0)' },
          ],
        },
      },
    },
  ],
}))

const compositionMode = ref<'type' | 'owner'>('type')

const compositionItems = computed<DonutItem[]>(() => {
  if (compositionMode.value === 'owner') {
    // byOwner 是各归属的净额（已扣负债）；只展示净额为正的归属，中心显示净资产
    return Object.entries(composition.value.byOwner)
      .filter(([, value]) => value > 0)
      .map(([key, value]) => ({
        id: key,
        name: settings.label(DIM_ACCOUNT_OWNER, key),
        value,
        color: categoryColor(DIM_ACCOUNT_OWNER, key),
      }))
  }
  // 与「总资产」口径一致：只统计正资产，负债不计入
  return Object.entries(composition.value.byType)
    .filter(([, value]) => value > 0)
    .map(([key, value]) => ({
      id: key,
      name: settings.label(DIM_ACCOUNT_TYPE, key),
      value,
      color: typeColor(key),
    }))
})

const typeChangeOption = computed(() => {
  const items = typeChange.value.items.filter((item) => Number.isFinite(item.change))
  const sorted = [...items].sort((a, b) => Math.abs(b.change) - Math.abs(a.change))
  const values = sorted.map((item) => item.change)
  const maxValue = Math.max(0, ...values)
  const minValue = Math.min(0, ...values)
  const span = Math.max(maxValue - minValue, Math.max(Math.abs(maxValue), Math.abs(minValue)), 1)
  const padding = span * 0.18
  const positiveColor = chartPositiveSoft
  const negativeColor = chartNegativeSoft
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: Array<{ dataIndex: number }>) => {
        const first = params[0]
        const item = sorted[first?.dataIndex ?? 0]
        if (!item) {
          return ''
        }
        return [
          settings.label(DIM_ACCOUNT_TYPE, item.type),
          t('dashboard.typeChange.latest', { amount: formatMoney(item.latest) }),
          t('dashboard.typeChange.previous', { amount: formatMoney(item.previous) }),
          t('dashboard.typeChange.change', { amount: formatMoney(item.change) }),
        ].join('<br/>')
      },
    },
    grid: {
      left: 12,
      right: 12,
      top: 48,
      bottom: isMobileChart.value ? 60 : 36,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: sorted.map((item) => settings.label(DIM_ACCOUNT_TYPE, item.type)),
      axisLabel: {
        interval: 0,
        rotate: isMobileChart.value ? 34 : 0,
        width: isMobileChart.value ? 56 : 96,
        overflow: 'truncate',
      },
    },
    yAxis: {
      type: 'value',
      max: maxValue + padding,
      min: minValue - padding,
      axisLabel: { formatter: (v: number) => formatAssetAmount(v) },
    },
    series: [
      {
        name: t('dashboard.typeChange.inflow'),
        type: 'bar',
        clip: false,
        barMaxWidth: 26,
        data: sorted.map((item) => ({
          value: item.change > 0 ? item.change : null,
          itemStyle: {
            color: positiveColor,
            borderRadius: [4, 4, 0, 0],
          },
        })),
        label: {
          show: true,
          position: 'top',
          verticalAlign: 'bottom',
          distance: 10,
          color: chartPositive,
          fontWeight: 700,
          fontSize: isMobileChart.value ? 10 : 11,
          formatter: (p: { value?: number }) => {
            const value = Number(p.value ?? 0)
            return value > 0 ? formatAssetAmount(value) : ''
          },
        },
      },
      {
        name: t('dashboard.typeChange.outflow'),
        type: 'bar',
        clip: false,
        barGap: '-100%',
        barMaxWidth: 26,
        data: sorted.map((item) => ({
          value: item.change < 0 ? item.change : null,
          itemStyle: {
            color: negativeColor,
            borderRadius: [0, 0, 4, 4],
          },
        })),
        label: {
          show: true,
          position: 'bottom',
          verticalAlign: 'top',
          distance: 10,
          color: chartNegative,
          fontWeight: 700,
          fontSize: isMobileChart.value ? 10 : 11,
          formatter: (p: { value?: number }) => {
            const value = Number(p.value ?? 0)
            return value < 0 ? formatAssetAmount(value) : ''
          },
        },
      },
    ],
  }
})

function signedMoney(value: number) {
  return `${value > 0 ? '+' : ''}${formatMoney(value)}`
}

function toneOf(value: number) {
  return value > 0 ? 'positive' : value < 0 ? 'negative' : ''
}

const kpis = computed(() => [
  { label: t('dashboard.netWorth'), value: formatMoney(summary.value.netWorth), hint: t('dashboard.kpi.latestSnapshot'), tone: '' },
  {
    label: t('dashboard.kpi.monthlyChange'),
    value: signedMoney(summary.value.monthlyChange),
    hint: t('dashboard.kpi.monthlyHint'),
    tone: toneOf(summary.value.monthlyChange),
  },
  {
    label: t('dashboard.kpi.annualChange'),
    value: signedMoney(summary.value.annualChange),
    hint: t('dashboard.kpi.annualHint'),
    tone: toneOf(summary.value.annualChange),
  },
])

const typeChangeHasData = computed(() => Boolean(typeChange.value.previousDate && typeChange.value.items.length))
const typeChangeAllZero = computed(() => typeChange.value.items.every((item) => !item.change))

const typeChangeSummary = computed(() => {
  const total = typeChange.value.items.reduce((sum, item) => sum + (Number.isFinite(item.change) ? item.change : 0), 0)
  if (total > 0) {
    return { label: t('dashboard.typeChange.netInflow'), value: total, tone: 'positive' }
  }
  if (total < 0) {
    return { label: t('dashboard.typeChange.netOutflow'), value: Math.abs(total), tone: 'negative' }
  }
  return { label: t('dashboard.typeChange.netChange'), value: 0, tone: 'neutral' }
})

const palette = chartPalette
type SankeyNode = { name: string; labelName: string; raw?: number; itemStyle?: { color: string } }
type SankeyLink = { source: string; target: string; value: number; raw?: number; labelName: string }

function formatAssetAmount(value: number) {
  const abs = Math.abs(value)
  // 负号放在货币符号前面：-¥1.20w，而不是 ¥-1.20w
  const sign = value < 0 ? '-' : ''
  if (currentLocale() !== 'zh-CN') {
    // 英文界面用 K/M 缩写
    if (abs >= 1_000_000) {
      return `${sign}¥${(abs / 1_000_000).toFixed(2)}M`
    }
    if (abs > 1000) {
      return `${sign}¥${(abs / 1000).toFixed(2)}K`
    }
    return `${sign}¥${abs.toFixed(2)}`
  }
  if (abs > 10000) {
    return `${sign}¥${(abs / 10000).toFixed(2)}w`
  }
  if (abs > 1000) {
    return `${sign}¥${(abs / 1000).toFixed(2)}k`
  }
  return `${sign}¥${abs.toFixed(2)}`
}

function compactLabelName(name: string, maxLength: number) {
  return name.length > maxLength ? `${name.slice(0, maxLength)}...` : name
}

const assetSankeyData = computed(() => {
  const nodes: SankeyNode[] = []
  const links: SankeyLink[] = []
  const nodeNames = new Set<string>()
  const positiveTypes: [string, number][] = []

  function addNode(name: string, labelName: string, color: string, raw?: number) {
    if (nodeNames.has(name)) {
      return
    }
    nodeNames.add(name)
    nodes.push({ name, labelName, raw, itemStyle: { color } })
  }

  for (const [type, value] of Object.entries(composition.value.byType)) {
    if (!Number.isFinite(value) || value <= 0) {
      continue
    }
    positiveTypes.push([type, value])
  }

  const totalAssets = positiveTypes.reduce((sum, [, value]) => sum + value, 0)

  addNode('summary:totalAssets', t('dashboard.totalAssets'), palette[0], totalAssets)

  positiveTypes
    .sort((a, b) => b[1] - a[1])
    .forEach(([type, value]) => {
      const typeName = settings.label(DIM_ACCOUNT_TYPE, type)
      const typeNodeName = `type:${type}`
      const nodeColor = typeColor(type)
      addNode(typeNodeName, typeName, nodeColor, value)
      links.push({
        source: 'summary:totalAssets',
        target: typeNodeName,
        value,
        raw: value,
        labelName: `${t('dashboard.totalAssets')} → ${typeName}`,
      })

      const accounts = composition.value.byTypeAccounts?.[type] ?? {}
      Object.entries(accounts)
        .filter(([, accountValue]) => Number.isFinite(accountValue) && accountValue > 0)
        .sort((a, b) => b[1] - a[1])
        .forEach(([accountId, accountValue]) => {
          const accountName = accountNameById.value[accountId] ?? accountId
          const accountNodeName = `account:${accountId}`
          addNode(accountNodeName, accountName, nodeColor, accountValue)
          links.push({
            source: typeNodeName,
            target: accountNodeName,
            value: accountValue,
            raw: accountValue,
            labelName: `${typeName} → ${accountName}`,
          })
        })
    })

  return { nodes, links, totalAssets }
})

const assetSankeyOption = computed(() => ({
  tooltip: {
    trigger: 'item',
    formatter: (p: {
      dataType?: string
      name?: string
      data?: { raw?: number; value?: number; labelName?: string }
    }) => {
      const raw = Number(p.data?.raw ?? p.data?.value ?? 0)
      if (p.dataType === 'edge') {
        return `${p.data?.labelName ?? p.name ?? ''}<br/>${formatAssetAmount(raw)}`
      }
      if (p.data?.labelName && Number.isFinite(raw)) {
        return `${p.data.labelName}<br/>${formatAssetAmount(raw)}`
      }
      return p.data?.labelName ?? p.name ?? ''
    },
  },
  series: [
    {
      type: 'sankey',
      left: isMobileChart.value ? 2 : 12,
      right: isMobileChart.value ? 96 : 176,
      top: isMobileChart.value ? 8 : 18,
      bottom: isMobileChart.value ? 8 : 18,
      nodeWidth: isMobileChart.value ? 6 : 10,
      nodeGap: isMobileChart.value ? 8 : 14,
      draggable: false,
      emphasis: { focus: 'adjacency' },
      lineStyle: {
        color: 'gradient',
        curveness: 0.56,
        opacity: 0.3,
      },
      label: {
        color: '#374151',
        fontSize: isMobileChart.value ? 9 : 12,
        lineHeight: isMobileChart.value ? 12 : 16,
        width: isMobileChart.value ? 74 : undefined,
        overflow: isMobileChart.value ? 'truncate' : undefined,
        formatter: (p: { name: string; data?: { labelName?: string; raw?: number } }) => {
          if (p.name === 'summary:totalAssets') {
            return t('dashboard.flow.totalLabel', { amount: formatAssetAmount(assetSankeyData.value.totalAssets) })
          }
          if (p.data?.labelName && Number.isFinite(p.data.raw)) {
            if (isMobileChart.value) {
              const raw = Number(p.data.raw)
              const isLeafAccount = p.name.startsWith('account:')
              const isSmallLeaf = isLeafAccount && raw / Math.max(assetSankeyData.value.totalAssets, 1) < 0.08
              if (isSmallLeaf) {
                return p.data.labelName
              }
              return `${p.data.labelName}\n${formatAssetAmount(Number(p.data.raw))}`
            }
            const labelName = p.name.startsWith('account:') ? compactLabelName(p.data.labelName, 8) : p.data.labelName
            return `${labelName}  ${formatAssetAmount(Number(p.data.raw))}`
          }
          return p.data?.labelName ?? p.name
        },
      },
      data: assetSankeyData.value.nodes,
      links: assetSankeyData.value.links,
    },
  ],
}))

/** 有分账户数据的类型，供下拉选择 */
const accountShareTypeOptions = computed(() => {
  const bta = composition.value.byTypeAccounts
  if (!bta) {
    return [] as { label: string; value: string }[]
  }
  return Object.keys(bta)
    .filter((t) => {
      const row = bta[t] ?? {}
      return Object.values(row).some((v) => v !== 0 && !Number.isNaN(v))
    })
    .sort((a, b) => a.localeCompare(b))
    .map((t) => ({ label: settings.label(DIM_ACCOUNT_TYPE, t), value: t }))
})

const accountShareTypeKey = ref<string | null>(null)

watch(
  accountShareTypeOptions,
  (opts) => {
    if (!opts.length) {
      accountShareTypeKey.value = null
      return
    }
    if (!accountShareTypeKey.value || !opts.some((o) => o.value === accountShareTypeKey.value)) {
      accountShareTypeKey.value = opts[0].value
    }
  },
  { immediate: true },
)

/** 当前选中类型下，各账户占比（扇区用绝对值，便于负债等类型展示；列表显示带符号金额） */
const typeAccountShareItems = computed<DonutItem[]>(() => {
  const bta = composition.value.byTypeAccounts
  const t = accountShareTypeKey.value
  if (!bta || !t) {
    return []
  }
  return Object.entries(bta[t] ?? {})
    .filter(([, v]) => v !== 0 && !Number.isNaN(v))
    .sort((a, b) => Math.abs(b[1]) - Math.abs(a[1]))
    .map(([id, raw], i) => ({
      id,
      name: accountNameById.value[id] ?? id,
      value: raw,
      color: palette[i % palette.length],
    }))
})

const typeAccountSharePieHasData = computed(() => {
  const bta = composition.value.byTypeAccounts
  const t = accountShareTypeKey.value
  if (!bta || !t) {
    return false
  }
  const row = bta[t] ?? {}
  return Object.values(row).some((v) => v !== 0 && !Number.isNaN(v))
})

const stackedByTypeOption = computed(() => {
  const pts = stackedPoints.value
  if (!pts.length) {
    return { xAxis: { type: 'category' as const, data: [] }, yAxis: { type: 'value' as const }, series: [] }
  }
  const dates = pts.map((p) => p.date)
  const typeKeys = new Set<string>()
  for (const p of pts) {
    Object.keys(p.byType).forEach((k) => typeKeys.add(k))
  }
  const keys = [...typeKeys].sort()
  if (!keys.length) {
    return {
      xAxis: { type: 'category' as const, data: dates },
      yAxis: { type: 'value' as const },
      series: [],
    }
  }
  const series = keys.map((key) => ({
    name: settings.label(DIM_ACCOUNT_TYPE, key),
    type: 'line' as const,
    stack: 'nw',
    showSymbol: false,
    smooth: 0.3,
    lineStyle: { width: 1.5, color: typeColor(key) },
    itemStyle: { color: typeColor(key) },
    areaStyle: { opacity: 0.28, color: typeColor(key) },
    data: pts.map((p) => p.byType[key] ?? 0),
  }))
  return {
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: number) => formatMoney(v),
    },
    legend: { type: 'scroll', bottom: 0 },
    xAxis: { type: 'category', data: dates },
    yAxis: {
      type: 'value',
      axisLabel: { formatter: (v: number) => formatMoney(v) },
    },
    series,
  }
})

async function loadRangeCharts() {
  try {
    const tr = await dashboardApi.fetchTrend(range.value)
    trendPoints.value = tr.points
  } catch {
    trendPoints.value = []
  }
  try {
    const st = await dashboardApi.fetchStackedByType(range.value)
    stackedPoints.value = st.points
  } catch {
    stackedPoints.value = []
  }
}

async function load() {
  loading.value = true
  try {
    try {
      await settings.load()
    } catch {
      /* 标签映射降级为 key，不阻塞总览 */
    }
    summary.value = await dashboardApi.fetchSummary()
    await loadRangeCharts()
    try {
      const accs = await accountApi.listAccounts()
      accountNameById.value = Object.fromEntries(accs.map((a) => [a.id, a.name]))
    } catch {
      accountNameById.value = {}
    }
    composition.value = await dashboardApi.fetchComposition()
    try {
      typeChange.value = await dashboardApi.fetchTypeChange()
    } catch {
      typeChange.value = { latestDate: null, previousDate: null, items: [] }
    }
  } catch (e) {
    console.error('dashboard load', e)
    message.error(t('dashboard.loadFailed'))
  } finally {
    loading.value = false
  }
}

watch(range, async () => {
  try {
    await loadRangeCharts()
  } catch (e) {
    console.error('dashboard range charts', e)
    message.error(t('dashboard.chartsLoadFailed'))
  }
})

function updateMobileChart() {
  isMobileChart.value = window.matchMedia('(max-width: 640px)').matches
}

onMounted(load)
onMounted(() => {
  updateMobileChart()
  window.addEventListener('resize', updateMobileChart)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', updateMobileChart)
})
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('dashboard.title')">
      <template #description>
        <template v-if="typeChange.latestDate">{{ t('dashboard.descAsOf', { date: typeChange.latestDate }) }}</template>
        <template v-else>{{ t('dashboard.descDefault') }}</template>
      </template>
      <n-button type="primary" @click="router.push('/snapshots/new')">{{ t('dashboard.recordSnapshot') }}</n-button>
    </PageHeader>

    <n-spin :show="loading">
      <section class="bento">
        <div class="bento__span-8 hero-card">
          <div class="hero-card__head">
            <div>
              <div class="hero-card__label">{{ t('dashboard.netWorth') }}</div>
              <div class="hero-card__value">{{ formatMoney(summary.netWorth) }}</div>
              <div class="hero-card__chips">
                <span v-for="kpi in kpis.slice(1)" :key="kpi.label" class="delta-chip" :class="kpi.tone && `delta-chip--${kpi.tone}`">
                  <span class="delta-chip__label">{{ kpi.label }}</span>
                  {{ kpi.value }}
                </span>
              </div>
            </div>
            <n-tabs v-model:value="range" type="segment" size="small" class="range-tabs">
              <n-tab name="3m">{{ t('dashboard.range.m3') }}</n-tab>
              <n-tab name="6m">{{ t('dashboard.range.m6') }}</n-tab>
              <n-tab name="1y">{{ t('dashboard.range.y1') }}</n-tab>
              <n-tab name="all">{{ t('dashboard.range.all') }}</n-tab>
            </n-tabs>
          </div>
          <v-chart v-if="trendPoints.length" class="hero-card__chart" :option="trendOption" autoresize />
          <n-empty v-else class="hero-card__chart" :description="t('dashboard.emptyTrend')" />
        </div>

        <n-card class="bento__span-4 surface-panel" :title="t('dashboard.composition.title')">
          <template #header-extra>
            <n-tabs v-model:value="compositionMode" type="segment" size="small" class="mini-tabs">
              <n-tab name="type">{{ t('dashboard.composition.byType') }}</n-tab>
              <n-tab name="owner">{{ t('dashboard.composition.byOwner') }}</n-tab>
            </n-tabs>
          </template>
          <DonutBreakdown
            v-if="compositionItems.length"
            :items="compositionItems"
            :center-label="compositionMode === 'type' ? t('dashboard.totalAssets') : t('dashboard.netWorth')"
            :center-value="compositionMode === 'type' ? undefined : summary.netWorth"
          />
          <n-empty v-else :description="t('dashboard.emptySnapshot')" />
        </n-card>

        <n-card class="bento__span-12 surface-panel" :title="t('dashboard.flow.title')">
          <template #header-extra>
            <span class="section-note">{{ t('dashboard.flow.note') }}</span>
          </template>
          <v-chart
            v-if="assetSankeyData.links.length"
            class="chart-frame chart-frame--sankey"
            :option="assetSankeyOption"
            autoresize
          />
          <n-empty v-else :description="t('dashboard.emptySnapshot')" />
        </n-card>

        <n-card class="bento__span-8 surface-panel" :title="t('dashboard.stacked.title')">
          <template #header-extra>
            <span class="section-note">{{ t('dashboard.stacked.note') }}</span>
          </template>
          <v-chart v-if="stackedPoints.length" class="chart-frame" :option="stackedByTypeOption" autoresize />
          <n-empty v-else :description="t('dashboard.stacked.empty')" />
        </n-card>

        <n-card class="bento__span-4 surface-panel" :title="t('dashboard.typeAccounts.title')">
          <template #header-extra>
            <n-select
              v-model:value="accountShareTypeKey"
              :options="accountShareTypeOptions"
              size="small"
              :placeholder="t('dashboard.typeAccounts.selectType')"
              style="width: 112px"
              :disabled="!accountShareTypeOptions.length"
            />
          </template>
          <DonutBreakdown
            v-if="accountShareTypeKey && typeAccountSharePieHasData"
            :items="typeAccountShareItems"
            :center-label="accountShareTypeOptions.find((o) => o.value === accountShareTypeKey)?.label"
            :center-value="typeAccountShareItems.reduce((sum, x) => sum + x.value, 0)"
          />
          <n-empty v-else-if="!accountShareTypeOptions.length" :description="t('dashboard.typeAccounts.emptyAccounts')" />
          <n-empty v-else :description="t('dashboard.typeAccounts.emptyBalance')" />
        </n-card>

        <n-card class="bento__span-12 surface-panel" :title="t('dashboard.typeChange.title')">
          <template #header-extra>
            <span v-if="typeChange.latestDate && typeChange.previousDate" class="section-note">
              {{ typeChange.previousDate }} → {{ typeChange.latestDate }}
            </span>
          </template>
          <div class="type-change-summary">
            <div
              class="type-change-summary__value"
              :class="`type-change-summary__value--${typeChangeSummary.tone}`"
            >
              {{ typeChangeSummary.label }} {{ formatAssetAmount(typeChangeSummary.value) }}
            </div>
          </div>
          <div v-if="typeChangeHasData && typeChangeAllZero" class="section-note">{{ t('dashboard.typeChange.noChange') }}</div>
          <v-chart
            v-else-if="typeChangeHasData"
            class="chart-frame--compact"
            :option="typeChangeOption"
            autoresize
          />
          <n-empty v-else :description="t('dashboard.typeChange.empty')" />
        </n-card>
      </section>
    </n-spin>
  </div>
</template>
