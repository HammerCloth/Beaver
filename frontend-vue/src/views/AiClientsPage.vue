<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import * as oauthApi from '@/api/oauth'
import type { OAuthAuthorization } from '@/api/oauth'
import PageHeader from '@/components/PageHeader.vue'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const rows = ref<OAuthAuthorization[]>([])

const columns = [
  {
    title: '客户端',
    key: 'clientName',
    render: (row: OAuthAuthorization) =>
      h('div', { class: 'cell-stack' }, [
        h('span', { class: 'cell-main' }, row.clientName || 'AI 客户端'),
        h('span', { class: 'cell-muted' }, row.clientId),
      ]),
  },
  {
    title: '权限',
    key: 'scope',
    render: (row: OAuthAuthorization) => h('code', { class: 'scope-code' }, row.scope),
  },
  {
    title: '授权时间',
    key: 'createdAt',
    render: (row: OAuthAuthorization) => h('span', { class: 'cell-muted' }, row.createdAt),
  },
  {
    title: '最后使用',
    key: 'lastUsedAt',
    render: (row: OAuthAuthorization) => h('span', { class: 'cell-muted' }, row.lastUsedAt || '尚未使用'),
  },
  {
    title: '过期时间',
    key: 'expiresAt',
    render: (row: OAuthAuthorization) => h('span', { class: 'cell-muted' }, row.expiresAt),
  },
  {
    title: '状态',
    key: 'status',
    render: (row: OAuthAuthorization) =>
      h('span', { class: ['badge', row.revokedAt ? '' : 'badge--positive'] }, row.revokedAt ? '已撤销' : '有效'),
  },
  {
    title: '',
    key: 'actions',
    width: 80,
    align: 'right' as const,
    render: (row: OAuthAuthorization) =>
      row.revokedAt
        ? null
        : h('button', { class: 'text-action text-action--danger', onClick: () => revoke(row) }, '撤销'),
  },
]

async function refresh() {
  loading.value = true
  try {
    rows.value = await oauthApi.listAuthorizations()
  } finally {
    loading.value = false
  }
}

function revoke(row: OAuthAuthorization) {
  dialog.warning({
    title: '撤销 AI 客户端',
    content: `撤销后 ${row.clientName || '该客户端'} 需要重新授权才能访问 MCP。`,
    positiveText: '撤销',
    negativeText: '取消',
    onPositiveClick: async () => {
      await oauthApi.revokeAuthorization(row.id)
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
      <n-button secondary type="error" :disabled="rows.length === 0" @click="revokeAll">全部撤销</n-button>
    </PageHeader>

    <n-card class="surface-panel surface-panel--flush">
      <template #header>
        已授权客户端 <span class="section-note">· {{ rows.length }} 个</span>
      </template>
      <n-data-table
        :columns="columns"
        :data="rows"
        :loading="loading"
        :pagination="rows.length > 10 ? { pageSize: 10 } : false"
        :row-key="(row: OAuthAuthorization) => row.id"
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
</style>
