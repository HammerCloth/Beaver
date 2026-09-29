<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import type { OptionItem } from '@/api/settings'
import { DIM_ACCOUNT_TYPE, DIM_ACCOUNT_OWNER, DIM_EVENT_CATEGORY, useSettingsStore } from '@/stores/settings'
import { chartPalette } from '@/lib/chartTheme'
import PageHeader from '@/components/PageHeader.vue'
import { t } from '@/i18n'

const message = useMessage()
const dialog = useDialog()
const settings = useSettingsStore()

const editing = ref<Record<string, OptionItem[]>>({})
const activeDim = ref(DIM_ACCOUNT_TYPE)

const dims = [
  {
    key: DIM_ACCOUNT_TYPE,
    label: t('settings.dims.accountType.label'),
    save: t('settings.dims.accountType.save'),
    hint: t('settings.dims.accountType.hint'),
  },
  {
    key: DIM_ACCOUNT_OWNER,
    label: t('settings.dims.accountOwner.label'),
    save: t('settings.dims.accountOwner.save'),
    hint: t('settings.dims.accountOwner.hint'),
  },
  {
    key: DIM_EVENT_CATEGORY,
    label: t('settings.dims.eventCategory.label'),
    save: t('settings.dims.eventCategory.save'),
    hint: t('settings.dims.eventCategory.hint'),
  },
]

/** 与 useCategoryColor 一致：按排序顺序取色，便于预览图表里的颜色 */
function rowColor(dim: string, index: number) {
  const rows = editing.value[dim] ?? []
  const order = [...rows].sort((a, b) => a.sortOrder - b.sortOrder)
  return chartPalette[Math.max(0, order.indexOf(rows[index])) % chartPalette.length]
}

function cloneRows(dim: string): OptionItem[] {
  const src = settings.options?.[dim as keyof typeof settings.options] ?? []
  return src.map((r) => ({ ...r }))
}

async function refresh() {
  await settings.load()
  editing.value = {
    [DIM_ACCOUNT_TYPE]: cloneRows(DIM_ACCOUNT_TYPE),
    [DIM_ACCOUNT_OWNER]: cloneRows(DIM_ACCOUNT_OWNER),
    [DIM_EVENT_CATEGORY]: cloneRows(DIM_EVENT_CATEGORY),
  }
}

onMounted(() => {
  refresh().catch(() => message.error(t('common.status.loadFailed')))
})

async function saveDim(dim: string) {
  try {
    await settings.saveDimension(dim, editing.value[dim] ?? [])
    message.success(t('common.status.saved'))
  } catch (e: unknown) {
    const data = (e as { response?: { data?: { error?: string } } })?.response?.data
    const msg = data?.error
    message.error(typeof msg === 'string' ? msg : t('settings.saveFailed'))
  }
}

function addRow(dim: string) {
  const list = editing.value[dim] ?? []
  list.push({
    key: '',
    label: '',
    sortOrder: list.length,
    enabled: true,
  })
  editing.value[dim] = list
}

function removeRow(dim: string, index: number) {
  editing.value[dim]?.splice(index, 1)
}

function onReset() {
  dialog.warning({
    title: t('settings.resetDialog.title'),
    content: t('settings.resetDialog.content'),
    positiveText: t('settings.resetDialog.confirm'),
    negativeText: t('common.actions.cancel'),
    onPositiveClick: async () => {
      try {
        await settings.reset()
        await refresh()
        message.success(t('settings.resetDone'))
      } catch {
        message.error(t('common.status.operationFailed'))
      }
    },
  })
}
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('settings.title')" :description="t('settings.description')">
      <n-button @click="refresh">{{ t('settings.reload') }}</n-button>
      <n-button secondary type="error" @click="onReset">{{ t('settings.resetDefaults') }}</n-button>
    </PageHeader>

    <n-spin :show="settings.loading">
      <n-card class="surface-panel">
        <n-tabs v-model:value="activeDim" type="line" animated>
          <n-tab-pane v-for="dim in dims" :key="dim.key" :name="dim.key" :tab="dim.label">
            <p class="section-note settings-hint">{{ dim.hint }}</p>
            <div v-if="editing[dim.key]" class="option-table">
              <div class="option-table__head">
                <span />
                <span>{{ t('settings.columns.key') }}</span>
                <span>{{ t('settings.columns.label') }}</span>
                <span>{{ t('settings.columns.sortOrder') }}</span>
                <span>{{ t('settings.columns.enabled') }}</span>
                <span />
              </div>
              <div v-for="(row, i) in editing[dim.key]" :key="i" class="option-table__row" :class="{ 'is-disabled': !row.enabled }">
                <span class="swatch" :style="{ background: rowColor(dim.key, i) }" />
                <n-input v-model:value="row.key" size="small" :placeholder="t('settings.keyPlaceholder')" />
                <n-input v-model:value="row.label" size="small" :placeholder="t('settings.labelPlaceholder')" />
                <n-input-number v-model:value="row.sortOrder" :input-props="{ inputmode: 'numeric' }" size="small" :show-button="false" />
                <n-switch v-model:value="row.enabled" size="small" />
                <n-button size="tiny" quaternary type="error" @click="removeRow(dim.key, i)">{{ t('settings.remove') }}</n-button>
              </div>
            </div>
            <div class="option-table__footer">
              <n-button size="small" dashed @click="addRow(dim.key)">{{ t('settings.addOption') }}</n-button>
              <n-button size="small" type="primary" @click="saveDim(dim.key)">{{ dim.save }}</n-button>
            </div>
          </n-tab-pane>
        </n-tabs>
      </n-card>
    </n-spin>
  </div>
</template>

<style scoped>
.settings-hint {
  margin: 4px 0 16px;
}

.option-table {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
}

.option-table__head,
.option-table__row {
  display: grid;
  grid-template-columns: 16px minmax(120px, 1fr) minmax(140px, 1.4fr) 88px 56px 56px;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
}

.option-table__head {
  background: var(--surface-0);
  color: var(--text-3);
  font-size: 12px;
  font-weight: 500;
}

.option-table__row {
  border-top: 1px solid var(--line-soft);
}

.option-table__row.is-disabled > :not(:last-child) {
  opacity: 0.5;
}

.option-table__footer {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
}

@media (max-width: 720px) {
  .option-table__head {
    display: none;
  }

  .option-table__row {
    grid-template-columns: 12px 1fr 1fr;
  }
}
</style>
