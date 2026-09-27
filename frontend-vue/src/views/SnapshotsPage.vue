<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { SnapshotListItem } from '@/types/models'
import * as snapshotApi from '@/api/snapshot'
import { amountTone, formatMoney, formatSignedMoney } from '@/lib/format'
import PageHeader from '@/components/PageHeader.vue'
import StatStrip from '@/components/StatStrip.vue'
import SnapshotViewSwitch from '@/components/SnapshotViewSwitch.vue'

type Row = SnapshotListItem & { change: number | null }

const router = useRouter()
const message = useMessage()
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
    { label: '快照次数', value: list.length, unit: '次' },
    { label: '最近一次', value: latest?.date ?? '—', hint: latest ? formatMoney(latest.netWorth) : undefined },
    {
      label: '累计变化',
      value: latest && earliest ? formatSignedMoney(latest.netWorth - earliest.netWorth) : '—',
      hint: earliest ? `自 ${earliest.date}` : undefined,
      tone: latest && earliest ? amountTone(latest.netWorth - earliest.netWorth) : '',
    },
  ]
})

const columns: DataTableColumns<Row> = [
  {
    title: '日期',
    key: 'date',
    render: (row) => h('span', { class: 'cell-main' }, row.date),
  },
  {
    title: '净资产',
    key: 'netWorth',
    align: 'right',
    render: (row) => h('span', { class: 'amount' }, formatMoney(row.netWorth)),
  },
  {
    title: '较上次',
    key: 'change',
    align: 'right',
    render(row) {
      if (row.change === null) {
        return h('span', { class: 'amount amount--muted' }, '首次记录')
      }
      const tone = amountTone(row.change)
      return h('span', { class: ['amount', tone ? `amount--${tone}` : 'amount--muted'] }, formatSignedMoney(row.change))
    },
  },
  {
    title: '记录时间',
    key: 'createdAt',
    align: 'right',
    render: (row) => h('span', { class: 'cell-muted' }, row.createdAt),
  },
]

onMounted(async () => {
  try {
    rows.value = await snapshotApi.listSnapshots()
  } catch {
    message.error('加载失败')
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
    <PageHeader title="快照" description="每一次快照都是某一天全部账户余额的记录，点击行查看明细。">
      <SnapshotViewSwitch current="list" />
      <n-button type="primary" @click="router.push('/snapshots/new')">记录快照</n-button>
    </PageHeader>

    <StatStrip v-if="tableRows.length" :items="stats" />

    <n-card class="surface-panel surface-panel--flush" title="全部快照">
      <n-data-table
        :loading="loading"
        :columns="columns"
        :data="tableRows"
        :row-props="rowProps"
        :row-key="(row: Row) => row.id"
      />
    </n-card>
  </div>
</template>
