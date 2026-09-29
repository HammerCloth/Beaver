<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import * as oauthApi from '@/api/oauth'
import type { OAuthClientAuthorization } from '@/api/oauth'
import { mobileCardColumns } from '@/lib/mobileCard'
import { useIsMobile } from '@/composables/useIsMobile'
import PageHeader from '@/components/PageHeader.vue'

type Row = OAuthClientAuthorization

const message = useMessage()
const dialog = useDialog()
const { isMobile } = useIsMobile()
const loading = ref(false)
const rows = ref<Row[]>([])
const showInactive = ref(false)

const activeRows = computed(() => rows.value.filter((r) => r.active))
const inactiveCount = computed(() => rows.value.length - activeRows.value.length)
const visibleRows = computed(() => (showInactive.value ? rows.value : activeRows.value))

/** 后端时间为 UTC：SQLite 的 "YYYY-MM-DD HH:MM:SS" 或 ISO 字符串，统一转成本地时间展示 */
function formatTime(value: string | null) {
  if (!value) {
    return null
  }
  const d = new Date(value.includes('T') ? value : `${value.replace(' ', 'T')}Z`)
  if (Number.isNaN(d.getTime())) {
    return value
  }
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function statusBadge(row: Row) {
  return h('span', { class: ['badge', row.active ? 'badge--positive' : ''] }, row.active ? '有效' : '已失效')
}

function revokeAction(row: Row) {
  return row.active
    ? h('button', { class: 'text-action text-action--danger', onClick: () => revoke(row) }, '撤销')
    : null
}

const muted = (text: string | null, fallback = '—') => h('span', { class: 'cell-muted' }, text ?? fallback)

const columns: DataTableColumns<Row> = [
  {
    title: '客户端',
    key: 'clientName',
    render: (row) => h('span', { class: 'cell-main' }, row.clientName || 'AI 客户端'),
  },
  {
    title: '权限',
    key: 'scope',
    render: (row) => h('code', { class: 'scope-code' }, row.scope),
  },
  { title: '首次授权', key: 'authorizedAt', render: (row) => muted(formatTime(row.authorizedAt)) },
  { title: '最后使用', key: 'lastUsedAt', render: (row) => muted(formatTime(row.lastUsedAt), '尚未使用') },
  { title: '过期时间', key: 'expiresAt', render: (row) => muted(formatTime(row.expiresAt)) },
  { title: '状态', key: 'active', render: statusBadge },
  { title: '', key: 'actions', width: 80, align: 'right', render: revokeAction },
]

const cardColumns = mobileCardColumns<Row>((row) => {
  const action = revokeAction(row)
  return {
    title: h('span', { class: 'cell-main' }, row.clientName || 'AI 客户端'),
    value: statusBadge(row),
    meta: [
      h('code', { class: 'scope-code' }, row.scope),
      `最后使用 ${formatTime(row.lastUsedAt) ?? '尚未使用'}`,
      `首次授权 ${formatTime(row.authorizedAt) ?? '—'}`,
      row.active && row.expiresAt ? `${formatTime(row.expiresAt)} 过期` : null,
    ],
    actions: action ? [action] : [],
  }
})

async function refresh() {
  loading.value = true
  try {
    rows.value = await oauthApi.listClientAuthorizations()
  } finally {
    loading.value = false
  }
}

function revoke(row: Row) {
  dialog.warning({
    title: '撤销 AI 客户端',
    content: `撤销后 ${row.clientName || '该客户端'} 需要重新授权才能访问 MCP。`,
    positiveText: '撤销',
    negativeText: '取消',
    onPositiveClick: async () => {
      await oauthApi.revokeClientAuthorization(row.clientId)
      message.success('已撤销')
      await refresh()
    },
  })
}

function revokeAll() {
  dialog.warning({
    title: '撤销全部 AI 客户端',
    content: '所有 AI 客户端都需要重新授权后才能访问 MCP。',
    positiveText: '全部撤销',
    negativeText: '取消',
    onPositiveClick: async () => {
      await oauthApi.revokeAllAuthorizations()
      message.success('已全部撤销')
      await refresh()
    },
  })
}

onMounted(() => {
  refresh().catch(() => message.error('加载失败'))
})
</script>

<template>
  <div class="page-stack">
    <PageHeader title="AI 客户端" description="通过 MCP 授权访问你数据的 AI Agent。不再使用的客户端请及时撤销。">
      <n-button @click="refresh">刷新</n-button>
      <n-button secondary type="error" :disabled="activeRows.length === 0" @click="revokeAll">全部撤销</n-button>
    </PageHeader>

    <n-card class="surface-panel surface-panel--flush">
      <template #header>
        已授权客户端 <span class="section-note">· {{ activeRows.length }} 个</span>
      </template>
      <template v-if="inactiveCount" #header-extra>
        <label class="inactive-toggle">
          <n-switch v-model:value="showInactive" size="small" />
          显示已失效（{{ inactiveCount }}）
        </label>
      </template>
      <n-data-table
        :class="{ 'data-table--cards': isMobile }"
        :columns="isMobile ? cardColumns : columns"
        :data="visibleRows"
        :loading="loading"
        :pagination="visibleRows.length > 10 ? { pageSize: 10 } : false"
        :row-key="(row: Row) => row.clientId"
      />
    </n-card>
  </div>
</template>

<style scoped>
:deep(.scope-code) {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--surface-hover);
  color: var(--text-2);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

.inactive-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-2);
  font-size: 13px;
  cursor: pointer;
}
</style>
