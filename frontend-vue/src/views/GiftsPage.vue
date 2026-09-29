<script setup lang="ts">
import { computed, h, provide, ref, watch } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import { THEME_KEY } from 'vue-echarts'
import { CHART_THEME, chartPalette } from '@/lib/chartTheme'
import PageHeader from '@/components/PageHeader.vue'
import StatStrip from '@/components/StatStrip.vue'
import DonutBreakdown, { type DonutItem } from '@/components/DonutBreakdown.vue'
import type { DataTableColumns } from 'naive-ui'
import type { GiftRecipient, GiftRecord } from '@/types/models'
import * as giftApi from '@/api/gift'
import { formatMoney } from '@/lib/format'
import { localToday } from '@/lib/date'
import { t } from '@/i18n'
import { mobileCardColumns } from '@/lib/mobileCard'
import { useIsMobile } from '@/composables/useIsMobile'

provide(THEME_KEY, CHART_THEME)

const message = useMessage()
const { isMobile } = useIsMobile()
const dialog = useDialog()
const currentYear = new Date().getFullYear()
const year = ref(currentYear)
const keyword = ref('')
const recipientFilter = ref<string | null>(null)
const recipients = ref<GiftRecipient[]>([])
const records = ref<GiftRecord[]>([])
const stats = ref<giftApi.GiftStats>({
  year: currentYear,
  grandTotal: 0,
  count: 0,
  recipientCount: 0,
  byOccasion: {},
  countByOccasion: {},
})
const loading = ref(true)
const recordModalOpen = ref(false)
const recipientModalOpen = ref(false)
const recipientManagerOpen = ref(false)
const editingRecord = ref<GiftRecord | null>(null)
const editingRecipient = ref<GiftRecipient | null>(null)
const detailRecipient = ref<GiftRecipient | null>(null)
const detailRecords = ref<GiftRecord[]>([])
const recipientCreateForRecord = ref(false)

const recordForm = ref<giftApi.GiftBody>({
  recipientId: '',
  occasion: '',
  giftDate: localToday(),
  amount: null,
  paymentMethod: '',
  note: '',
})
const recipientForm = ref<giftApi.RecipientBody>({ name: '', relationship: '', note: '' })

const recipientOptions = computed(() =>
  recipients.value.filter((item) => item.is_active).map((item) => ({
    label: item.relationship ? `${item.name} · ${item.relationship}` : item.name,
    value: item.id,
  })),
)
const allRecipientOptions = computed(() =>
  recipients.value.map((item) => ({ label: item.name, value: item.id })),
)
const occasionRows = computed(() =>
  Object.entries(stats.value.byOccasion).map(([occasion, amount]) => ({ occasion, amount })),
)
const detailTotal = computed(() => detailRecords.value.reduce((sum, record) => sum + record.amount, 0))

/** 场合是自由文本，按金额排名分配颜色 */
const occasionItems = computed<DonutItem[]>(() =>
  [...occasionRows.value]
    .sort((x, y) => y.amount - x.amount)
    .map((row, i) => ({ id: row.occasion, name: row.occasion, value: row.amount, color: chartPalette[i % chartPalette.length] })),
)

const statItems = computed(() => [
  { label: t('gifts.stats.yearTotal', { year: stats.value.year }), value: formatMoney(stats.value.grandTotal) },
  { label: t('gifts.stats.records'), value: stats.value.count, unit: t('common.unit.items') },
  { label: t('gifts.stats.recipients'), value: stats.value.recipientCount, unit: t('common.unit.people') },
  {
    label: t('gifts.stats.average'),
    value: stats.value.count ? formatMoney(stats.value.grandTotal / stats.value.count) : t('common.empty'),
  },
])

const recordColumns: DataTableColumns<GiftRecord> = [
  { title: t('gifts.columns.date'), key: 'gift_date', width: 112, render: (row) => h('span', { class: 'cell-muted' }, row.gift_date) },
  {
    title: t('gifts.columns.recipient'), key: 'recipient_name', minWidth: 150,
    render(row) {
      return h('a', { class: 'text-action', onClick: () => openRecipientDetail(row.gift_recipient_id) }, row.recipient_name)
    },
  },
  { title: t('gifts.columns.relationship'), key: 'recipient_relationship', width: 100, render: (row) => row.recipient_relationship || '—' },
  { title: t('gifts.columns.occasion'), key: 'occasion', width: 110, render: (row) => h('span', { class: 'badge badge--plain' }, row.occasion) },
  { title: t('gifts.columns.method'), key: 'payment_method', width: 100, render: (row) => row.payment_method || '—' },
  { title: t('gifts.columns.note'), key: 'note', ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'cell-muted' }, row.note || '—') },
  { title: t('gifts.columns.amount'), key: 'amount', width: 120, align: 'right', render: (row) => h('span', { class: 'amount' }, formatMoney(row.amount)) },
  {
    title: '', key: 'actions', width: 130, align: 'right',
    render: (row) => h('div', { class: 'table-actions' }, recordActions(row)),
  },
]

function recordActions(row: GiftRecord) {
  return [
    h('button', { class: 'text-action', onClick: () => openEditRecord(row) }, t('common.actions.edit')),
    h('button', { class: 'text-action text-action--danger', onClick: () => confirmDeleteRecord(row) }, t('common.actions.delete')),
  ]
}

const recordCardColumns = mobileCardColumns<GiftRecord>((row) => ({
  title: [
    h('a', { class: 'text-action', onClick: () => openRecipientDetail(row.gift_recipient_id) }, row.recipient_name),
    row.note ? h('span', { class: 'cell-muted' }, row.note) : null,
  ],
  value: h('span', { class: 'amount' }, formatMoney(row.amount)),
  meta: [
    h('span', { class: 'badge badge--plain' }, row.occasion),
    row.gift_date,
    row.recipient_relationship,
    row.payment_method,
  ],
  actions: recordActions(row),
}))

async function load() {
  loading.value = true
  try {
    const [recipientRows, recordRows, statsData] = await Promise.all([
      giftApi.listRecipients(true),
      giftApi.listRecords({ year: year.value, recipientId: recipientFilter.value ?? undefined, keyword: keyword.value || undefined }),
      giftApi.giftStats(year.value),
    ])
    recipients.value = recipientRows
    records.value = recordRows
    stats.value = statsData
  } catch {
    message.error(t('gifts.toast.loadFailed'))
  } finally {
    loading.value = false
  }
}

watch([year, recipientFilter], load, { immediate: true })

function onSearch() {
  load()
}

function openCreateRecord() {
  if (!recipientOptions.value.length) {
    openCreateRecipient(true)
    return
  }
  editingRecord.value = null
  recordForm.value = {
    recipientId: recipientOptions.value[0].value,
    occasion: '',
    giftDate: localToday(),
    amount: null,
    paymentMethod: '',
    note: '',
  }
  recordModalOpen.value = true
}

function openEditRecord(row: GiftRecord) {
  editingRecord.value = row
  recordForm.value = {
    recipientId: row.gift_recipient_id,
    occasion: row.occasion,
    giftDate: row.gift_date,
    amount: row.amount,
    paymentMethod: row.payment_method || '',
    note: row.note || '',
  }
  recordModalOpen.value = true
}

async function saveRecord() {
  try {
    if (editingRecord.value) {
      await giftApi.updateRecord(editingRecord.value.id, recordForm.value)
      message.success(t('gifts.toast.recordUpdated'))
    } else {
      await giftApi.createRecord(recordForm.value)
      message.success(t('gifts.toast.recordCreated'))
    }
    recordModalOpen.value = false
    await load()
  } catch (error) {
    message.error(apiMessage(error, t('common.status.saveFailed')))
  }
}

function confirmDeleteRecord(row: GiftRecord) {
  dialog.warning({
    title: t('gifts.dialog.deleteRecordTitle'),
    content: t('gifts.dialog.deleteRecordContent', { name: row.recipient_name, amount: formatMoney(row.amount) }),
    positiveText: t('common.actions.delete'),
    negativeText: t('common.actions.cancel'),
    onPositiveClick: async () => {
      try {
        await giftApi.deleteRecord(row.id)
        message.success(t('common.status.deleted'))
        await load()
      } catch {
        message.error(t('gifts.toast.deleteFailed'))
      }
    },
  })
}

function openCreateRecipient(fromRecord = false) {
  editingRecipient.value = null
  recipientCreateForRecord.value = fromRecord
  recipientForm.value = { name: '', relationship: '', note: '' }
  recipientModalOpen.value = true
  if (fromRecord) recordModalOpen.value = false
}

function openEditRecipient(row: GiftRecipient) {
  editingRecipient.value = row
  recipientCreateForRecord.value = false
  recipientForm.value = { name: row.name, relationship: row.relationship || '', note: row.note || '' }
  recipientModalOpen.value = true
}

async function saveRecipient() {
  try {
    if (editingRecipient.value) {
      await giftApi.updateRecipient(editingRecipient.value.id, recipientForm.value)
      message.success(t('gifts.toast.recipientUpdated'))
    } else {
      const recipient = await giftApi.createRecipient(recipientForm.value)
      recordForm.value.recipientId = recipient.id
      message.success(t('gifts.toast.recipientCreated'))
    }
    recipientModalOpen.value = false
    await load()
    if (recipientCreateForRecord.value && !editingRecipient.value) {
      recordModalOpen.value = true
    }
    recipientCreateForRecord.value = false
  } catch (error) {
    message.error(apiMessage(error, t('common.status.saveFailed')))
  }
}

function confirmDeactivateRecipient(row: GiftRecipient) {
  dialog.warning({
    title: t('gifts.dialog.deactivateTitle'),
    content: t('gifts.dialog.deactivateContent', { name: row.name }),
    positiveText: t('common.actions.deactivate'),
    negativeText: t('common.actions.cancel'),
    onPositiveClick: async () => {
      try {
        await giftApi.deactivateRecipient(row.id)
        message.success(t('gifts.toast.deactivated'))
        await load()
      } catch {
        message.error(t('common.status.operationFailed'))
      }
    },
  })
}

async function openRecipientDetail(id: string) {
  detailRecipient.value = recipients.value.find((recipient) => recipient.id === id) || null
  if (!detailRecipient.value) return
  try {
    detailRecords.value = await giftApi.listRecords({ recipientId: id })
  } catch {
    detailRecords.value = []
    message.error(t('gifts.toast.historyLoadFailed'))
  }
}

function apiMessage(error: unknown, fallback: string) {
  const data = (error as { response?: { data?: { error?: string } } })?.response?.data
  return typeof data?.error === 'string' ? data.error : fallback
}
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('gifts.title')" :description="t('gifts.description')">
      <n-button @click="recipientManagerOpen = true">{{ t('gifts.recipients') }}</n-button>
      <n-button type="primary" @click="openCreateRecord">{{ t('gifts.recordGift') }}</n-button>
    </PageHeader>

    <div class="filter-bar">
      <n-input-number v-model:value="year" :input-props="{ inputmode: 'numeric' }" class="filter-bar__year" :min="2000" :max="2100" />
      <n-input v-model:value="keyword" class="filter-bar__grow" clearable :placeholder="t('gifts.searchPlaceholder')" @keyup.enter="onSearch">
        <template #prefix>
          <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
        </template>
      </n-input>
      <n-select v-model:value="recipientFilter" class="filter-bar__fixed" clearable :placeholder="t('gifts.allRecipients')" :options="allRecipientOptions" />
      <n-button @click="onSearch">{{ t('common.actions.filter') }}</n-button>
    </div>

    <n-spin :show="loading">
      <StatStrip :items="statItems" />

      <n-card v-if="occasionItems.length" class="surface-panel" :title="t('gifts.byOccasion')">
        <DonutBreakdown
          layout="split"
          bars
          :items="occasionItems"
          :center-label="t('gifts.total')"
          :meta="(item) => stats.countByOccasion[item.name] ? t('gifts.recordCount', { n: stats.countByOccasion[item.name] }) : undefined"
        />
      </n-card>

      <n-card class="surface-panel surface-panel--flush">
        <template #header>
          {{ t('gifts.listTitle') }} <span class="section-note">· {{ t('gifts.recordCount', { n: records.length }) }}</span>
        </template>
        <n-data-table
          :class="{ 'data-table--cards': isMobile }"
          :columns="isMobile ? recordCardColumns : recordColumns"
          :data="records"
          :row-key="(row: GiftRecord) => row.id"
          :scroll-x="isMobile ? undefined : 960"
        />
        <n-empty v-if="!loading && !records.length" class="panel-empty" :description="t('gifts.empty')" />
      </n-card>
    </n-spin>

    <n-modal v-model:show="recordModalOpen" preset="card" :title="editingRecord ? t('gifts.editRecord') : t('gifts.recordGift')" style="width: 520px">
      <n-form label-placement="left" label-width="90">
        <n-form-item :label="t('gifts.form.recipient')" required><n-select v-model:value="recordForm.recipientId" filterable :options="recipientOptions" /></n-form-item>
        <n-form-item :label="t('gifts.form.occasion')" required><n-input v-model:value="recordForm.occasion" :placeholder="t('gifts.form.occasionPlaceholder')" /></n-form-item>
        <n-form-item :label="t('gifts.form.giftDate')" required><n-date-picker v-model:formatted-value="recordForm.giftDate" value-format="yyyy-MM-dd" type="date" clearable /></n-form-item>
        <n-form-item :label="t('gifts.form.amount')" required><n-input-number v-model:value="recordForm.amount" :input-props="{ inputmode: 'decimal' }" :min="0.01" :precision="2" style="width: 100%"><template #prefix>¥</template></n-input-number></n-form-item>
        <n-form-item :label="t('gifts.form.paymentMethod')"><n-input v-model:value="recordForm.paymentMethod" :placeholder="t('gifts.form.paymentMethodPlaceholder')" /></n-form-item>
        <n-form-item :label="t('gifts.form.note')"><n-input v-model:value="recordForm.note" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" /></n-form-item>
      </n-form>
      <template #footer><div class="modal-footer"><n-button @click="recordModalOpen = false">{{ t('common.actions.cancel') }}</n-button><n-button type="primary" @click="saveRecord">{{ t('common.actions.save') }}</n-button></div></template>
    </n-modal>

    <n-modal v-model:show="recipientModalOpen" preset="card" :title="editingRecipient ? t('gifts.editRecipient') : t('gifts.newRecipient')" style="width: 480px">
      <n-form label-placement="left" label-width="76">
        <n-form-item :label="t('gifts.form.name')" required><n-input v-model:value="recipientForm.name" :placeholder="t('gifts.form.namePlaceholder')" /></n-form-item>
        <n-form-item :label="t('gifts.form.relationship')"><n-input v-model:value="recipientForm.relationship" :placeholder="t('gifts.form.relationshipPlaceholder')" /></n-form-item>
        <n-form-item :label="t('gifts.form.note')"><n-input v-model:value="recipientForm.note" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" /></n-form-item>
      </n-form>
      <template #footer><div class="modal-footer"><n-button @click="recipientModalOpen = false">{{ t('common.actions.cancel') }}</n-button><n-button type="primary" @click="saveRecipient">{{ t('common.actions.save') }}</n-button></div></template>
    </n-modal>

    <n-modal v-model:show="recipientManagerOpen" preset="card" :title="t('gifts.recipients')" style="width: min(760px, calc(100vw - 32px))">
      <div class="gift-recipient-manager-head">
        <n-button size="small" type="primary" @click="openCreateRecipient()">{{ t('gifts.newRecipientShort') }}</n-button>
      </div>
      <div class="gift-recipient-list">
        <div v-for="recipient in recipients" :key="recipient.id" class="gift-recipient-row">
          <div>
            <strong>{{ recipient.name }}</strong>
            <span v-if="recipient.relationship" class="section-note">{{ recipient.relationship }}</span>
            <p v-if="recipient.note" class="gift-recipient-note">{{ recipient.note }}</p>
          </div>
          <div class="gift-recipient-meta">{{ formatMoney(recipient.gift_total) }} · {{ t('gifts.recordCount', { n: recipient.gift_count }) }}</div>
          <n-tag v-if="!recipient.is_active" size="small" type="warning">{{ t('gifts.inactive') }}</n-tag>
          <n-space size="small"><n-button size="small" quaternary @click="openEditRecipient(recipient)">{{ t('common.actions.edit') }}</n-button><n-button v-if="recipient.is_active" size="small" quaternary type="error" @click="confirmDeactivateRecipient(recipient)">{{ t('common.actions.deactivate') }}</n-button></n-space>
        </div>
        <n-empty v-if="!recipients.length" :description="t('gifts.recipientsEmpty')" style="padding: 28px 0" />
      </div>
    </n-modal>

    <n-drawer :show="Boolean(detailRecipient)" :width="420" placement="right" @update:show="(show: boolean) => { if (!show) detailRecipient = null }">
      <n-drawer-content v-if="detailRecipient" :title="detailRecipient.name">
        <n-tag v-if="detailRecipient.relationship" size="small">{{ detailRecipient.relationship }}</n-tag>
        <p class="section-note">{{ t('gifts.drawer.summary', { amount: formatMoney(detailTotal), n: detailRecords.length }) }}</p>
        <n-timeline style="margin-top: 24px">
          <n-timeline-item v-for="record in detailRecords" :key="record.id" :title="`${record.occasion} · ${formatMoney(record.amount)}`" :content="record.payment_method || undefined" :time="record.gift_date">
            <span v-if="record.note" class="section-note">{{ record.note }}</span>
          </n-timeline-item>
        </n-timeline>
        <n-empty v-if="!detailRecords.length" :description="t('gifts.drawer.empty')" />
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<style scoped>
.gift-recipient-list { display: grid; gap: 8px; }
.gift-recipient-manager-head { display: flex; justify-content: flex-end; margin-bottom: 12px; }
.gift-recipient-row { display: grid; grid-template-columns: minmax(0, 1fr) auto auto auto; gap: 16px; align-items: center; padding: 12px 0; border-bottom: 1px solid var(--line-soft); }
.gift-recipient-row:last-child { border-bottom: 0; }
.gift-recipient-row .section-note { margin-left: 8px; }
.gift-recipient-note { margin: 5px 0 0; color: var(--text-2); font-size: 13px; }
.gift-recipient-meta { color: var(--text-2); white-space: nowrap; }
@media (max-width: 760px) {
  .gift-recipient-row { grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
  .gift-recipient-row > :last-child { grid-column: 1 / -1; }
}
</style>
