<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import draggable from 'vuedraggable'
import { useMessage } from 'naive-ui'
import type { Account } from '@/types/models'
import * as accountApi from '@/api/account'
import { DIM_ACCOUNT_OWNER, DIM_ACCOUNT_TYPE, useSettingsStore } from '@/stores/settings'
import PageHeader from '@/components/PageHeader.vue'
import { t } from '@/i18n'
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
    message.error(t('common.status.loadFailed'))
  } finally {
    loading.value = false
  }
})

async function onDragEnd() {
  try {
    await accountApi.reorderAccounts(list.value.map((a) => a.id))
    message.success(t('accounts.sortSaved'))
  } catch {
    message.error(t('accounts.sortFailed'))
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
      message.success(t('common.status.updated'))
    } else {
      await accountApi.createAccount(form.value)
      message.success(t('common.status.created'))
    }
    showModal.value = false
    await load()
  } catch {
    message.error(t('common.status.saveFailed'))
  }
}

async function deactivate(row: Account) {
  try {
    await accountApi.deactivateAccount(row.id)
    message.success(t('accounts.deactivated'))
    await load()
  } catch {
    message.error(t('common.status.operationFailed'))
  }
}
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('accounts.title')" :description="t('accounts.description')">
      <n-button type="primary" @click="openCreate">{{ t('accounts.create') }}</n-button>
    </PageHeader>

    <n-spin :show="loading">
      <n-card class="surface-panel surface-panel--flush">
        <template #header>
          {{ t('accounts.allAccounts') }} <span class="section-note">· {{ t('accounts.count', { n: list.length }) }}</span>
        </template>
        <draggable v-model="list" item-key="id" handle=".drag-handle" class="draggable-list" @end="onDragEnd">
          <template #item="{ element }">
            <div class="account-row">
              <div class="account-row__main">
                <span class="drag-handle" :title="t('accounts.dragToSort')">
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
                <n-button size="small" quaternary @click="openEdit(element)">{{ t('common.actions.edit') }}</n-button>
                <n-button size="small" quaternary type="error" @click="deactivate(element)">{{ t('common.actions.deactivate') }}</n-button>
              </div>
            </div>
          </template>
        </draggable>
        <n-empty v-if="!loading && !list.length" class="panel-empty" :description="t('accounts.empty')" />
      </n-card>
    </n-spin>
    <n-modal v-model:show="showModal" preset="card" :title="editing ? t('accounts.edit') : t('accounts.create')" style="width: 480px">
      <n-form>
        <n-form-item :label="t('accounts.fields.name')">
          <n-input v-model:value="form.name" />
        </n-form-item>
        <n-form-item :label="t('accounts.fields.type')">
          <n-select v-model:value="form.type" :options="typeOptions" />
        </n-form-item>
        <n-form-item :label="t('accounts.fields.owner')">
          <n-select v-model:value="form.owner" :options="ownerOptions" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="modal-footer">
          <n-button @click="showModal = false">{{ t('common.actions.cancel') }}</n-button>
          <n-button type="primary" @click="saveAccount">{{ t('common.actions.save') }}</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>
