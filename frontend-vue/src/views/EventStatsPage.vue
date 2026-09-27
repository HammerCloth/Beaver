<script setup lang="ts">
import { computed, h, onMounted, provide, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { DataTableColumns } from 'naive-ui'
import { useMessage } from 'naive-ui'
import { THEME_KEY } from 'vue-echarts'
import { CHART_THEME } from '@/lib/chartTheme'
import * as eventApi from '@/api/event'
import { formatMoney, formatSignedMoney } from '@/lib/format'
import { DIM_EVENT_CATEGORY, useSettingsStore } from '@/stores/settings'
import { useCategoryColor } from '@/composables/useCategoryColor'
import PageHeader from '@/components/PageHeader.vue'
import StatStrip from '@/components/StatStrip.vue'
import DonutBreakdown, { type DonutItem } from '@/components/DonutBreakdown.vue'

provide(THEME_KEY, CHART_THEME)

const message = useMessage()
const router = useRouter()
const settings = useSettingsStore()
const { categoryColor } = useCategoryColor()
const year = ref(new Date().getFullYear())
const byCategory = ref<Record<string, number>>({})
const countByCategory = ref<Record<string, number>>({})
const grandTotal = ref(0)
const loading = ref(true)
const events = ref<eventApi.EventItem[]>([])
const categoryFilter = ref<string | null>(null)
const flowFilter = ref<'all' | 'out' | 'in'>('all')

const flowOptions = [
  { label: '全部收支', value: 'all' },
  { label: '仅支出', value: 'out' },
  { label: '仅收入', value: 'in' },
]

const categoryOptions = computed(() =>
  [...new Set(events.value.map((e) => e.category))].map((key) => ({
    label: settings.label(DIM_EVENT_CATEGORY, key),
    value: key,
  })),
)

const filteredEvents = computed(() =>
  events.value.filter(
    (e) =>
      (!categoryFilter.value || e.category === categoryFilter.value) &&
      (flowFilter.value === 'all' || (flowFilter.value === 'out' ? e.amount < 0 : e.amount > 0)),
  ),
)

const filteredNet = computed(() => filteredEvents.value.reduce((sum, e) => sum + e.amount, 0))
const incomeTotal = computed(() => events.value.filter((e) => e.amount > 0).reduce((sum, e) => sum + e.amount, 0))

const columns: DataTableColumns<eventApi.EventItem> = [
  {
    title: '日期',
    key: 'date',
    width: 120,
    render: (row) =>
      h('button', { class: 'text-action', onClick: () => router.push(`/snapshots/${row.snapshotId}`) }, row.date),
  },
  {
    title: '分类',
    key: 'category',
    width: 140,
    render: (row) =>
      h('span', { class: 'cell-name' }, [
        h('span', { class: 'swatch', style: { background: categoryColor(DIM_EVENT_CATEGORY, row.category) } }),
        settings.label(DIM_EVENT_CATEGORY, row.category),
      ]),
  },
  { title: '说明', key: 'description', ellipsis: { tooltip: true }, render: (row) => row.description || '—' },
  {
    title: '收支',
    key: 'flow',
    width: 90,
    render: (row) =>
      h('span', { class: ['badge', row.amount < 0 ? 'badge--down' : 'badge--up'] }, row.amount < 0 ? '支出' : '收入'),
  },
  {
    title: '金额',
    key: 'amount',
    width: 130,
    align: 'right',
    render: (row) =>
      h('span', { class: ['amount', row.amount < 0 ? 'amount--negative' : 'amount--positive'] }, formatSignedMoney(row.amount)),
  },
]

const items = computed<DonutItem[]>(() =>
  Object.entries(byCategory.value).map(([category, amount]) => ({
    id: category,
    name: settings.label(DIM_EVENT_CATEGORY, category),
    value: amount,
    color: categoryColor(DIM_EVENT_CATEGORY, category),
  })),
)

/** 展示名 → 笔数，供明细行显示 */
const countByLabel = computed(() =>
  Object.fromEntries(
    Object.entries(countByCategory.value).map(([category, n]) => [settings.label(DIM_EVENT_CATEGORY, category), n]),
  ),
)

const stats = computed(() => {
  const count = Object.values(countByCategory.value).reduce((sum, n) => sum + n, 0)
  const top = [...items.value].sort((a, b) => b.value - a.value)[0]
  return [
    { label: `${year.value} 年支出合计`, value: formatMoney(grandTotal.value) },
    { label: '收入合计', value: formatMoney(incomeTotal.value), tone: incomeTotal.value > 0 ? ('positive' as const) : ('' as const) },
    { label: '支出事件', value: count, unit: '笔' },
    { label: '最大分类', value: top?.name ?? '—', hint: top ? formatMoney(top.value) : undefined },
  ]
})

function shiftYear(delta: number) {
  const base = year.value ?? new Date().getFullYear()
  year.value = Math.min(2100, Math.max(2000, base + delta))
}

async function load() {
  loading.value = true
  try {
    await settings.load()
    const [data, list] = await Promise.all([eventApi.eventStats(year.value), eventApi.listEvents(year.value)])
    events.value = list
    byCategory.value = data.byCategory
    grandTotal.value = data.grandTotal ?? 0
    countByCategory.value = data.countByCategory ?? {}
  } catch {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

watch(year, (y) => {
  if (y) {
    categoryFilter.value = null
    load()
  }
})

onMounted(load)
</script>

<template>
  <div class="page-stack">
    <PageHeader title="大事记" description="汇总快照中记录的年度支出事件，看钱主要花在了哪里。">
      <n-button quaternary @click="shiftYear(-1)">‹</n-button>
      <n-input-number v-model:value="year" style="width: 84px; text-align: center" :min="2000" :max="2100" :show-button="false" />
      <n-button quaternary @click="shiftYear(1)">›</n-button>
    </PageHeader>

    <n-spin :show="loading">
      <StatStrip :items="stats" />

      <n-card class="surface-panel" title="按分类分布">
        <DonutBreakdown
          v-if="items.length"
          layout="split"
          bars
          :items="items"
          center-label="支出合计"
          :meta="(item) => (countByLabel[item.name] ? `${countByLabel[item.name]} 笔` : undefined)"
        />
        <n-empty v-else-if="!loading" class="panel-empty" :description="`${year} 年还没有支出类大事记`" />
      </n-card>

      <n-card class="surface-panel surface-panel--flush">
        <template #header>
          明细 <span class="section-note">· {{ filteredEvents.length }} 笔，合计 {{ formatSignedMoney(filteredNet) }}</span>
        </template>
        <template #header-extra>
          <div class="filter-bar">
            <n-select v-model:value="categoryFilter" size="small" clearable placeholder="全部分类" :options="categoryOptions" style="width: 132px" />
            <n-select v-model:value="flowFilter" size="small" :options="flowOptions" style="width: 112px" />
          </div>
        </template>
        <n-data-table
          :columns="columns"
          :data="filteredEvents"
          :row-key="(row: eventApi.EventItem) => row.id"
          :pagination="filteredEvents.length > 15 ? { pageSize: 15 } : false"
          :scroll-x="720"
        />
        <n-empty v-if="!loading && !filteredEvents.length" class="panel-empty" description="没有符合条件的大事记" />
      </n-card>
    </n-spin>
  </div>
</template>
