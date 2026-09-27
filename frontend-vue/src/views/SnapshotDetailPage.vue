<script setup lang="ts">
import { computed, h, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDialog, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { SnapshotDetail } from '@/types/models'
import * as snapshotApi from '@/api/snapshot'
import { amountTone, formatMoney, formatSignedMoney } from '@/lib/format'
import PageHeader from '@/components/PageHeader.vue'
import StatStrip from '@/components/StatStrip.vue'
import DonutBreakdown, { type DonutItem } from '@/components/DonutBreakdown.vue'
import { useCategoryColor } from '@/composables/useCategoryColor'
import { CHART_THEME } from '@/lib/chartTheme'
import { THEME_KEY } from 'vue-echarts'
import { DIM_EVENT_CATEGORY, DIM_ACCOUNT_OWNER, DIM_ACCOUNT_TYPE, useSettingsStore } from '@/stores/settings'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const settings = useSettingsStore()
const { categoryColor } = useCategoryColor()
provide(THEME_KEY, CHART_THEME)
const snap = ref<SnapshotDetail | null>(null)
const loading = ref(true)

const id = computed(() => String(route.params.id ?? ''))
const eventRows = computed(() => snap.value?.events ?? [])

type Item = SnapshotDetail['items'][number]
type EventRow = SnapshotDetail['events'][number]

/** 与后端 BalanceLogic 一致：负债账户按负值计入 */
function effectiveBalance(item: Item) {
  return item.type === 'credit' ? -Math.abs(item.balance) : item.balance
}

const items = computed(() =>
  [...(snap.value?.items ?? [])].sort((a, b) => Math.abs(effectiveBalance(b)) - Math.abs(effectiveBalance(a))),
)

const netWorth = computed(() => items.value.reduce((sum, it) => sum + effectiveBalance(it), 0))
const eventNet = computed(() => eventRows.value.reduce((sum, ev) => sum + ev.amount, 0))

const stats = computed(() => [
  { label: '净资产', value: formatMoney(netWorth.value) },
  { label: '账户', value: items.value.length, unit: '个' },
  {
    label: '大事记净额',
    value: eventRows.value.length ? formatSignedMoney(eventNet.value) : '—',
    hint: eventRows.value.length ? `${eventRows.value.length} 笔` : '无',
    tone: amountTone(eventNet.value),
  },
])

const typeBreakdown = computed<DonutItem[]>(() => {
  const byType = new Map<string, number>()
  for (const it of items.value) {
    const v = effectiveBalance(it)
    if (v > 0) {
      byType.set(it.type ?? '', (byType.get(it.type ?? '') ?? 0) + v)
    }
  }
  return [...byType.entries()].map(([type, value]) => ({
    id: type,
    name: settings.label(DIM_ACCOUNT_TYPE, type),
    value,
    color: categoryColor(DIM_ACCOUNT_TYPE, type),
  }))
})

const itemColumns: DataTableColumns<Item> = [
  {
    title: '账户',
    key: 'accountName',
    render: (row) =>
      h('span', { class: 'cell-name' }, [
        h('span', { class: 'swatch', style: { background: categoryColor(DIM_ACCOUNT_TYPE, row.type ?? '') } }),
        h('span', { class: 'cell-main' }, row.accountName ?? row.accountId),
      ]),
  },
  {
    title: '类型',
    key: 'type',
    render: (row) => h('span', { class: 'cell-muted' }, settings.label(DIM_ACCOUNT_TYPE, row.type ?? '')),
  },
  {
    title: '归属',
    key: 'owner',
    render: (row) => h('span', { class: 'cell-muted' }, settings.label(DIM_ACCOUNT_OWNER, row.owner ?? '')),
  },
  {
    title: '余额',
    key: 'balance',
    align: 'right',
    render(row) {
      const v = effectiveBalance(row)
      return h('span', { class: ['amount', v === 0 ? 'amount--muted' : ''] }, formatMoney(v))
    },
  },
]

const eventColumns: DataTableColumns<EventRow> = [
  {
    title: '分类',
    key: 'category',
    render: (row) =>
      h('span', { class: 'cell-name' }, [
        h('span', { class: 'swatch', style: { background: categoryColor(DIM_EVENT_CATEGORY, row.category) } }),
        settings.label(DIM_EVENT_CATEGORY, row.category),
      ]),
  },
  { title: '说明', key: 'description', render: (row) => row.description || '—' },
  {
    title: '收支',
    key: 'flow',
    render: (row) =>
      h('span', { class: ['badge', row.amount < 0 ? 'badge--down' : 'badge--up'] }, row.amount < 0 ? '支出' : '收入'),
  },
  {
    title: '金额',
    key: 'amount',
    align: 'right',
    render: (row) =>
      h('span', { class: ['amount', row.amount < 0 ? 'amount--negative' : 'amount--positive'] }, formatSignedMoney(row.amount)),
  },
]

watch(
  id,
  async (next) => {
    if (!next) {
      return
    }
    loading.value = true
    try {
      await settings.load()
      snap.value = await snapshotApi.getSnapshot(next)
    } catch {
      message.error('加载失败')
      snap.value = null
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

function onEdit() {
  router.push(`/snapshots/${id.value}/edit`)
}

function onDelete() {
  dialog.warning({
    title: '删除快照',
    content: `删除 ${snap.value?.date ?? ''} 的快照后无法恢复，确定继续？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await snapshotApi.deleteSnapshot(id.value)
      message.success('已删除')
      await router.replace('/snapshots')
    },
  })
}
</script>

<template>
  <n-spin :show="loading">
    <div v-if="snap" class="page-stack">
      <PageHeader :title="`${snap.date} 快照`">
        <template #eyebrow>
          <button type="button" class="page-back" @click="router.push('/snapshots')">← 快照日历</button>
        </template>
        <template #description>
          <span class="meta-line">
            <span>记录于 {{ snap.createdAt }}</span>
            <span v-if="snap.note">备注：{{ snap.note }}</span>
          </span>
        </template>
        <n-button @click="onEdit">编辑</n-button>
        <n-button secondary type="error" @click="onDelete">删除</n-button>
      </PageHeader>

      <StatStrip :items="stats" />

      <section class="bento">
        <n-card class="bento__span-8 surface-panel surface-panel--flush" title="账户余额">
          <n-data-table :columns="itemColumns" :data="items" :row-key="(row: Item) => row.accountId" />
        </n-card>
        <n-card class="bento__span-4 surface-panel" title="资产构成">
          <DonutBreakdown v-if="typeBreakdown.length" :items="typeBreakdown" center-label="总资产" />
          <n-empty v-else class="panel-empty" description="没有正资产" />
        </n-card>
        <n-card class="bento__span-12 surface-panel surface-panel--flush" title="大事记">
          <n-data-table
            v-if="eventRows.length"
            :columns="eventColumns"
            :data="eventRows"
            :row-key="(row: EventRow) => row.id"
          />
          <n-empty v-else class="panel-empty" description="这次快照没有记录大事记" />
        </n-card>
      </section>
    </div>
  </n-spin>
</template>
