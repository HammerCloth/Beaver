<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { User } from '@/types/models'
import * as userApi from '@/api/user'
import { mobileCardColumns } from '@/lib/mobileCard'
import { useIsMobile } from '@/composables/useIsMobile'
import PageHeader from '@/components/PageHeader.vue'
import { t } from '@/i18n'

const message = useMessage()
const { isMobile } = useIsMobile()
const rows = ref<User[]>([])
const loading = ref(true)
const showCreate = ref(false)
const showPwd = ref(false)
const pwdTarget = ref<User | null>(null)
const newUser = ref({ username: '', password: '', isAdmin: false })
const newPwd = ref('')

async function load() {
  rows.value = await userApi.listUsers()
}

onMounted(async () => {
  try {
    await load()
  } catch {
    message.error(t('common.status.loadFailed'))
  } finally {
    loading.value = false
  }
})

async function create() {
  try {
    await userApi.createUser(newUser.value)
    message.success(t('common.status.created'))
    showCreate.value = false
    newUser.value = { username: '', password: '', isAdmin: false }
    await load()
  } catch {
    message.error(t('users.createFailed'))
  }
}

function openPwd(u: User) {
  pwdTarget.value = u
  newPwd.value = ''
  showPwd.value = true
}

async function savePwd() {
  if (!pwdTarget.value) {
    return
  }
  try {
    await userApi.resetPassword(pwdTarget.value.id, newPwd.value)
    message.success(t('users.passwordReset'))
    showPwd.value = false
  } catch {
    message.error(t('users.failed'))
  }
}

const columns: DataTableColumns<User> = [
  {
    title: t('users.columns.user'),
    key: 'username',
    render: (row) =>
      h('span', { class: 'cell-name' }, [
        h('span', { class: 'app-user__avatar' }, row.username.slice(0, 1).toUpperCase()),
        h('span', { class: 'cell-main' }, row.username),
      ]),
  },
  {
    title: t('users.columns.role'),
    key: 'is_admin',
    render: (row) => h('span', { class: ['badge', row.is_admin ? 'badge--accent' : ''] }, row.is_admin ? t('common.role.admin') : t('common.role.member')),
  },
  {
    title: t('users.columns.passwordStatus'),
    key: 'must_change_password',
    render: (row) =>
      row.must_change_password
        ? h('span', { class: 'badge badge--warning' }, t('users.passwordStatus.mustChange'))
        : h('span', { class: 'badge badge--positive' }, t('users.passwordStatus.normal')),
  },
  {
    title: '',
    key: 'a',
    align: 'right',
    render: (row) => h('button', { class: 'text-action', onClick: () => openPwd(row) }, t('users.resetPassword')),
  },
]

const cardColumns = mobileCardColumns<User>((row) => ({
  title: h('span', { class: 'cell-name' }, [
    h('span', { class: 'app-user__avatar' }, row.username.slice(0, 1).toUpperCase()),
    h('span', { class: 'cell-main' }, row.username),
  ]),
  value: h('button', { class: 'text-action', onClick: () => openPwd(row) }, t('users.resetPassword')),
  meta: [
    h('span', { class: ['badge', row.is_admin ? 'badge--accent' : ''] }, row.is_admin ? t('common.role.admin') : t('common.role.member')),
    row.must_change_password
      ? h('span', { class: 'badge badge--warning' }, t('users.passwordStatus.mustChange'))
      : h('span', { class: 'badge badge--positive' }, t('users.passwordStatus.normal')),
  ],
}))
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('users.title')" :description="t('users.description')">
      <n-button type="primary" @click="showCreate = true">{{ t('users.create') }}</n-button>
    </PageHeader>
    <n-spin :show="loading">
      <n-card class="surface-panel surface-panel--flush">
        <template #header>
          {{ t('users.allUsers') }} <span class="section-note">· {{ t('users.count', { n: rows.length }) }}</span>
        </template>
        <n-data-table
          :class="{ 'data-table--cards': isMobile }"
          :columns="isMobile ? cardColumns : columns"
          :data="rows"
          :row-key="(r: User) => r.id"
        />
      </n-card>
    </n-spin>
    <n-modal v-model:show="showCreate" preset="card" :title="t('users.create')" style="width: 440px">
      <n-form>
        <n-form-item :label="t('users.fields.username')">
          <n-input v-model:value="newUser.username" />
        </n-form-item>
        <n-form-item :label="t('users.fields.password')">
          <n-input v-model:value="newUser.password" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item :label="t('users.fields.admin')">
          <n-switch v-model:value="newUser.isAdmin" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="modal-footer">
          <n-button @click="showCreate = false">{{ t('common.actions.cancel') }}</n-button>
          <n-button type="primary" @click="create">{{ t('users.submit') }}</n-button>
        </div>
      </template>
    </n-modal>
    <n-modal v-model:show="showPwd" preset="card" :title="t('users.resetPassword')" style="width: 400px">
      <n-input v-model:value="newPwd" type="password" show-password-on="click" :placeholder="t('users.newPasswordPlaceholder')" />
      <template #footer>
        <div class="modal-footer">
          <n-button @click="showPwd = false">{{ t('common.actions.cancel') }}</n-button>
          <n-button type="primary" @click="savePwd">{{ t('common.actions.save') }}</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>
