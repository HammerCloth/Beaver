<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import draggable from 'vuedraggable'
import { useMessage } from 'naive-ui'
import type { Account } from '@/types/models'
import * as accountApi from '@/api/account'
import { DIM_ACCOUNT_OWNER, DIM_ACCOUNT_TYPE, useSettingsStore } from '@/stores/settings'
import PageHeader from '@/components/PageHeader.vue'
import { useCategoryColor } from '@/composables/useCategoryColor'

const message = useMessage()
const settings = useSettingsStore()
const { categoryColor } = useCategoryColor()
const list = ref<Account[]>([])
const loading = ref(true)
const showModal = ref(false)
const editing = ref<Account | null>(null)
const form = ref({ name: '', type: 'cash', owner: 'A' })

const typeOptions = computed(() => settings.selectOptions(DIM_ACCOUNT_TYPE))
const ownerOptions = computed(() => settings.selectOptions(DIM_ACCOUNT_OWNER))

async function load() {
  await settings.load()
  list.value = await accountApi.listAccounts()
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

async function onDragEnd() {
  try {
    await accountApi.reorderAccounts(list.value.map((a) => a.id))
    message.success('排序已保存')
  } catch {
    message.error('排序失败')
    await load()
  }
}

function openCreate() {
  editing.value = null
  form.value = { name: '', type: 'cash', owner: 'A' }
  showModal.value = true
}

function openEdit(row: Account) {
  editing.value = row
  form.value = { name: row.name, type: row.type, owner: row.owner }
  showModal.value = true
}

async function saveAccount() {
  try {
    if (editing.value) {
      await accountApi.updateAccount(editing.value.id, form.value)
      message.success('已更新')
    } else {
      await accountApi.createAccount(form.value)
      message.success('已创建')
    }
    showModal.value = false
    await load()
  } catch {
    message.error('保存失败')
  }
}

async function deactivate(row: Account) {
  try {
    await accountApi.deactivateAccount(row.id)
    message.success('已停用')
    await load()
  } catch {
    message.error('操作失败')
  }
}
</script>

<template>
  <div class="page-stack">
    <PageHeader title="账户" description="拖动左侧手柄调整顺序，顺序会同步到快照录入和各类图表。">
      <n-button type="primary" @click="openCreate">新建账户</n-button>
    </PageHeader>

    <n-spin :show="loading">
      <n-card class="surface-panel surface-panel--flush">
        <template #header>
          全部账户 <span class="section-note">· {{ list.length }} 个</span>
        </template>
        <draggable v-model="list" item-key="id" handle=".drag-handle" class="draggable-list" @end="onDragEnd">
          <template #item="{ element }">
            <div class="account-row">
              <div class="account-row__main">
                <span class="drag-handle" title="拖动排序">
                  <svg viewBox="0 0 24 24" fill="currentColor"><circle cx="9" cy="6" r="1.5" /><circle cx="15" cy="6" r="1.5" /><circle cx="9" cy="12" r="1.5" /><circle cx="15" cy="12" r="1.5" /><circle cx="9" cy="18" r="1.5" /><circle cx="15" cy="18" r="1.5" /></svg>
                </span>
                <div class="account-row__info">
                  <span class="account-row__name">
                    <span class="swatch" :style="{ background: categoryColor(DIM_ACCOUNT_TYPE, element.type) }" />
                    <strong>{{ element.name }}</strong>
                  </span>
                  <span class="account-row__tags">
                    <span class="badge badge--plain">{{ settings.label(DIM_ACCOUNT_TYPE, element.type) }}</span>
                    <span class="badge badge--plain badge--accent">{{ settings.label(DIM_ACCOUNT_OWNER, element.owner) }}</span>
                  </span>
                </div>
              </div>
              <div class="account-row__meta">
                <span>#{{ element.sort_order }}</span>
              </div>
              <div class="account-row__actions">
                <n-button size="small" quaternary @click="openEdit(element)">编辑</n-button>
                <n-button size="small" quaternary type="error" @click="deactivate(element)">停用</n-button>
              </div>
            </div>
          </template>
        </draggable>
        <n-empty v-if="!loading && !list.length" class="panel-empty" description="还没有账户，先新建一个" />
      </n-card>
    </n-spin>
    <n-modal v-model:show="showModal" preset="card" :title="editing ? '编辑账户' : '新建账户'" style="width: 480px">
      <n-form>
        <n-form-item label="名称">
          <n-input v-model:value="form.name" />
        </n-form-item>
        <n-form-item label="类型">
          <n-select v-model:value="form.type" :options="typeOptions" />
        </n-form-item>
        <n-form-item label="归属">
          <n-select v-model:value="form.owner" :options="ownerOptions" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="modal-footer">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" @click="saveAccount">保存</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>
