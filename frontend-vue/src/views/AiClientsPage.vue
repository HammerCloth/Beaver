<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import * as oauthApi from '@/api/oauth'
import type { OAuthClientAuthorization } from '@/api/oauth'
import { mobileCardColumns } from '@/lib/mobileCard'
import { useIsMobile } from '@/composables/useIsMobile'
import PageHeader from '@/components/PageHeader.vue'
import { t } from '@/i18n'

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
  return h('span', { class: ['badge', row.active ? 'badge--positive' : ''] }, row.active ? t('aiClients.status.active') : t('aiClients.status.inactive'))
}

function revokeAction(row: Row) {
  return row.active
    ? h('button', { class: 'text-action text-action--danger', onClick: () => revoke(row) }, t('common.actions.revoke'))
    : null
}

const muted = (text: string | null, fallback = '—') => h('span', { class: 'cell-muted' }, text ?? fallback)

const columns: DataTableColumns<Row> = [
  {
    title: t('aiClients.columns.client'),
    key: 'clientName',
    render: (row) => h('span', { class: 'cell-main' }, row.clientName || t('aiClients.defaultName')),
  },
  {
    title: t('aiClients.columns.scope'),
    key: 'scope',
    render: (row) => h('code', { class: 'scope-code' }, row.scope),
  },
  { title: t('aiClients.columns.authorizedAt'), key: 'authorizedAt', render: (row) => muted(formatTime(row.authorizedAt)) },
  { title: t('aiClients.columns.lastUsedAt'), key: 'lastUsedAt', render: (row) => muted(formatTime(row.lastUsedAt), t('aiClients.neverUsed')) },
  { title: t('aiClients.columns.expiresAt'), key: 'expiresAt', render: (row) => muted(formatTime(row.expiresAt)) },
  { title: t('aiClients.columns.status'), key: 'active', render: statusBadge },
  { title: '', key: 'actions', width: 80, align: 'right', render: revokeAction },
]

const cardColumns = mobileCardColumns<Row>((row) => {
  const action = revokeAction(row)
  return {
    title: h('span', { class: 'cell-main' }, row.clientName || t('aiClients.defaultName')),
    value: statusBadge(row),
    meta: [
      h('code', { class: 'scope-code' }, row.scope),
      t('aiClients.meta.lastUsed', { time: formatTime(row.lastUsedAt) ?? t('aiClients.neverUsed') }),
      t('aiClients.meta.authorized', { time: formatTime(row.authorizedAt) ?? '—' }),
      row.active && row.expiresAt ? t('aiClients.meta.expires', { time: formatTime(row.expiresAt) }) : null,
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
    title: t('aiClients.revokeDialog.title'),
    content: t('aiClients.revokeDialog.content', { name: row.clientName || t('aiClients.thisClient') }),
    positiveText: t('common.actions.revoke'),
    negativeText: t('common.actions.cancel'),
    onPositiveClick: async () => {
      await oauthApi.revokeClientAuthorization(row.clientId)
      message.success(t('aiClients.revoked'))
      await refresh()
    },
  })
}

function revokeAll() {
  dialog.warning({
    title: t('aiClients.revokeAllDialog.title'),
    content: t('aiClients.revokeAllDialog.content'),
    positiveText: t('aiClients.revokeAll'),
    negativeText: t('common.actions.cancel'),
    onPositiveClick: async () => {
      await oauthApi.revokeAllAuthorizations()
      message.success(t('aiClients.allRevoked'))
      await refresh()
    },
  })
}

onMounted(() => {
  refresh().catch(() => message.error(t('common.status.loadFailed')))
})
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('aiClients.title')" :description="t('aiClients.description')">
      <n-button @click="refresh">{{ t('common.actions.refresh') }}</n-button>
      <n-button secondary type="error" :disabled="activeRows.length === 0" @click="revokeAll">{{ t('aiClients.revokeAll') }}</n-button>
    </PageHeader>

    <n-card class="surface-panel surface-panel--flush">
      <template #header>
        {{ t('aiClients.authorizedClients') }} <span class="section-note">· {{ t('aiClients.count', { n: activeRows.length }) }}</span>
      </template>
      <template v-if="inactiveCount" #header-extra>
        <label class="inactive-toggle">
          <n-switch v-model:value="showInactive" size="small" />
          {{ t('aiClients.showInactive', { n: inactiveCount }) }}
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
