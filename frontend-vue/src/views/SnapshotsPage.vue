<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { SnapshotListItem } from '@/types/models'
import * as snapshotApi from '@/api/snapshot'
import { t } from '@/i18n'
import { amountTone, formatMoney, formatSignedMoney } from '@/lib/format'
import { mobileCardColumns } from '@/lib/mobileCard'
import { useIsMobile } from '@/composables/useIsMobile'
import PageHeader from '@/components/PageHeader.vue'
import StatStrip from '@/components/StatStrip.vue'
import SnapshotViewSwitch from '@/components/SnapshotViewSwitch.vue'

type Row = SnapshotListItem & { change: number | null }

const router = useRouter()
const message = useMessage()
const { isMobile } = useIsMobile()
const rows = ref<SnapshotListItem[]>([])
const loading = ref(true)

/** 按日期倒序，并计算与上一次（更早）快照相比的变化 */
const tableRows = computed<Row[]>(() => {
  const sorted = [...rows.value].sort((a, b) => b.date.localeCompare(a.date))
  return sorted.map((row, i) => {
    const prev = sorted[i + 1]
    return { ...row, change: prev ? row.netWorth - prev.netWorth : null }
  })
})

const stats = computed(() => {
  const list = tableRows.value
  const latest = list[0]
  const earliest = list[list.length - 1]
  return [
    { label: t('snapshots.list.stats.count'), value: list.length, unit: t('common.unit.times') },
    { label: t('snapshots.list.stats.latest'), value: latest?.date ?? '—', hint: latest ? formatMoney(latest.netWorth) : undefined },
    {
      label: t('snapshots.list.stats.totalChange'),
      value: latest && earliest ? formatSignedMoney(latest.netWorth - earliest.netWorth) : '—',
      hint: earliest ? t('snapshots.list.stats.since', { date: earliest.date }) : undefined,
      tone: latest && earliest ? amountTone(latest.netWorth - earliest.netWorth) : '',
    },
  ]
})

function changeCell(row: Row) {
  if (row.change === null) {
    return h('span', { class: 'amount amount--muted' }, t('snapshots.list.firstRecord'))
  }
  const tone = amountTone(row.change)
  return h('span', { class: ['amount', tone ? `amount--${tone}` : 'amount--muted'] }, formatSignedMoney(row.change))
}

const columns: DataTableColumns<Row> = [
  {
    title: t('snapshots.list.columns.date'),
    key: 'date',
    render: (row) => h('span', { class: 'cell-main' }, row.date),
  },
  {
    title: t('snapshots.list.columns.netWorth'),
    key: 'netWorth',
    align: 'right',
    render: (row) => h('span', { class: 'amount' }, formatMoney(row.netWorth)),
  },
  {
    title: t('snapshots.list.columns.change'),
    key: 'change',
    align: 'right',
    render: changeCell,
  },
  {
    title: t('snapshots.list.columns.createdAt'),
    key: 'createdAt',
    align: 'right',
    render: (row) => h('span', { class: 'cell-muted' }, row.createdAt),
  },
]

const cardColumns = mobileCardColumns<Row>((row) => ({
  title: [h('span', { class: 'cell-main' }, row.date), h('span', { class: 'cell-muted' }, t('snapshots.list.recordedAt', { time: row.createdAt }))],
  value: [h('div', { class: 'amount' }, formatMoney(row.netWorth)), h('div', { class: 'cell-muted' }, changeCell(row))],
}))

onMounted(async () => {
  try {
    rows.value = await snapshotApi.listSnapshots()
  } catch {
    message.error(t('common.status.loadFailed'))
  } finally {
    loading.value = false
  }
})

function rowProps(row: Row) {
  return {
    class: 'n-data-table-tr--clickable',
    onClick: () => router.push(`/snapshots/${row.id}`),
  }
}
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('snapshots.title')" :description="t('snapshots.list.description')">
      <SnapshotViewSwitch current="list" />
      <n-button type="primary" @click="router.push('/snapshots/new')">{{ t('snapshots.newSnapshot') }}</n-button>
    </PageHeader>

    <StatStrip v-if="tableRows.length" :items="stats" />

    <n-card class="surface-panel surface-panel--flush" :title="t('snapshots.list.allSnapshots')">
      <n-data-table
        :loading="loading"
        :class="{ 'data-table--cards': isMobile }"
        :columns="isMobile ? cardColumns : columns"
        :data="tableRows"
        :row-props="rowProps"
        :row-key="(row: Row) => row.id"
      />
    </n-card>
  </div>
</template>
