<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { User } from '@/types/models'
import * as userApi from '@/api/user'
import { mobileCardColumns } from '@/lib/mobileCard'
import { useIsMobile } from '@/composables/useIsMobile'
import PageHeader from '@/components/PageHeader.vue'

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
    message.error('加载失败')
  } finally {
    loading.value = false
  }
})

async function create() {
  try {
    await userApi.createUser(newUser.value)
    message.success('已创建')
    showCreate.value = false
    newUser.value = { username: '', password: '', isAdmin: false }
    await load()
  } catch {
    message.error('创建失败')
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
    message.success('密码已重置')
    showPwd.value = false
  } catch {
    message.error('失败')
  }
}

const columns: DataTableColumns<User> = [
  {
    title: '用户',
    key: 'username',
    render: (row) =>
      h('span', { class: 'cell-name' }, [
        h('span', { class: 'app-user__avatar' }, row.username.slice(0, 1).toUpperCase()),
        h('span', { class: 'cell-main' }, row.username),
      ]),
  },
  {
    title: '角色',
    key: 'is_admin',
    render: (row) => h('span', { class: ['badge', row.is_admin ? 'badge--accent' : ''] }, row.is_admin ? '管理员' : '成员'),
  },
  {
    title: '密码状态',
    key: 'must_change_password',
    render: (row) =>
      row.must_change_password
        ? h('span', { class: 'badge badge--warning' }, '待首次改密')
        : h('span', { class: 'badge badge--positive' }, '正常'),
  },
  {
    title: '',
    key: 'a',
    align: 'right',
    render: (row) => h('button', { class: 'text-action', onClick: () => openPwd(row) }, '重置密码'),
  },
]

const cardColumns = mobileCardColumns<User>((row) => ({
  title: h('span', { class: 'cell-name' }, [
    h('span', { class: 'app-user__avatar' }, row.username.slice(0, 1).toUpperCase()),
    h('span', { class: 'cell-main' }, row.username),
  ]),
  value: h('button', { class: 'text-action', onClick: () => openPwd(row) }, '重置密码'),
  meta: [
    h('span', { class: ['badge', row.is_admin ? 'badge--accent' : ''] }, row.is_admin ? '管理员' : '成员'),
    row.must_change_password
      ? h('span', { class: 'badge badge--warning' }, '待首次改密')
      : h('span', { class: 'badge badge--positive' }, '正常'),
  ],
}))
</script>

<template>
  <div class="page-stack">
    <PageHeader title="用户管理" description="创建成员账号、重置密码。新用户首次登录时需要修改密码。">
      <n-button type="primary" @click="showCreate = true">新建用户</n-button>
    </PageHeader>
    <n-spin :show="loading">
      <n-card class="surface-panel surface-panel--flush">
        <template #header>
          全部用户 <span class="section-note">· {{ rows.length }} 人</span>
        </template>
        <n-data-table
          :class="{ 'data-table--cards': isMobile }"
          :columns="isMobile ? cardColumns : columns"
          :data="rows"
          :row-key="(r: User) => r.id"
        />
      </n-card>
    </n-spin>
    <n-modal v-model:show="showCreate" preset="card" title="新建用户" style="width: 440px">
      <n-form>
        <n-form-item label="用户名">
          <n-input v-model:value="newUser.username" />
        </n-form-item>
        <n-form-item label="密码">
          <n-input v-model:value="newUser.password" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item label="管理员">
          <n-switch v-model:value="newUser.isAdmin" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="modal-footer">
          <n-button @click="showCreate = false">取消</n-button>
          <n-button type="primary" @click="create">创建</n-button>
        </div>
      </template>
    </n-modal>
    <n-modal v-model:show="showPwd" preset="card" title="重置密码" style="width: 400px">
      <n-input v-model:value="newPwd" type="password" show-password-on="click" placeholder="新密码（至少 8 位）" />
      <template #footer>
        <div class="modal-footer">
          <n-button @click="showPwd = false">取消</n-button>
          <n-button type="primary" @click="savePwd">保存</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>
