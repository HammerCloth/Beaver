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
  giftDate: new Date().toISOString().slice(0, 10),
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
  { label: `${stats.value.year} 年礼金支出`, value: formatMoney(stats.value.grandTotal) },
  { label: '记录', value: stats.value.count, unit: '笔' },
  { label: '涉及对象', value: stats.value.recipientCount, unit: '位' },
  {
    label: '平均每笔',
    value: stats.value.count ? formatMoney(stats.value.grandTotal / stats.value.count) : '—',
  },
])

const recordColumns: DataTableColumns<GiftRecord> = [
  { title: '日期', key: 'gift_date', width: 112, render: (row) => h('span', { class: 'cell-muted' }, row.gift_date) },
  {
    title: '对象', key: 'recipient_name', minWidth: 150,
    render(row) {
      return h('a', { class: 'text-action', onClick: () => openRecipientDetail(row.gift_recipient_id) }, row.recipient_name)
    },
  },
  { title: '关系', key: 'recipient_relationship', width: 100, render: (row) => row.recipient_relationship || '—' },
  { title: '场合', key: 'occasion', width: 110, render: (row) => h('span', { class: 'badge badge--plain' }, row.occasion) },
  { title: '方式', key: 'payment_method', width: 100, render: (row) => row.payment_method || '—' },
  { title: '备注', key: 'note', ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'cell-muted' }, row.note || '—') },
  { title: '实际承担', key: 'amount', width: 120, align: 'right', render: (row) => h('span', { class: 'amount' }, formatMoney(row.amount)) },
  {
    title: '', key: 'actions', width: 110, align: 'right',
    render: (row) => h('div', { class: 'table-actions' }, recordActions(row)),
  },
]

function recordActions(row: GiftRecord) {
  return [
    h('button', { class: 'text-action', onClick: () => openEditRecord(row) }, '编辑'),
    h('button', { class: 'text-action text-action--danger', onClick: () => confirmDeleteRecord(row) }, '删除'),
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
    message.error('加载礼金数据失败')
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
    giftDate: new Date().toISOString().slice(0, 10),
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
      message.success('礼金记录已更新')
    } else {
      await giftApi.createRecord(recordForm.value)
      message.success('礼金已记录')
    }
    recordModalOpen.value = false
    await load()
  } catch (error) {
    message.error(apiMessage(error, '保存失败'))
  }
}

function confirmDeleteRecord(row: GiftRecord) {
  dialog.warning({
    title: '删除礼金记录',
    content: `确定删除 ${row.recipient_name} 的 ${formatMoney(row.amount)} 礼金记录吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await giftApi.deleteRecord(row.id)
        message.success('已删除')
        await load()
      } catch {
        message.error('删除失败')
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
      message.success('礼金对象已更新')
    } else {
      const recipient = await giftApi.createRecipient(recipientForm.value)
      recordForm.value.recipientId = recipient.id
      message.success('礼金对象已创建')
    }
    recipientModalOpen.value = false
    await load()
    if (recipientCreateForRecord.value && !editingRecipient.value) {
      recordModalOpen.value = true
    }
    recipientCreateForRecord.value = false
  } catch (error) {
    message.error(apiMessage(error, '保存失败'))
  }
}

function confirmDeactivateRecipient(row: GiftRecipient) {
  dialog.warning({
    title: '停用礼金对象',
    content: `停用后不能再新增关联 ${row.name} 的礼金，已有历史仍会保留。`,
    positiveText: '停用',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await giftApi.deactivateRecipient(row.id)
        message.success('已停用')
        await load()
      } catch {
        message.error('操作失败')
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
    message.error('加载对象历史失败')
  }
}

function apiMessage(error: unknown, fallback: string) {
  const data = (error as { response?: { data?: { error?: string } } })?.response?.data
  return typeof data?.error === 'string' ? data.error : fallback
}
</script>

<template>
  <div class="page-stack">
    <PageHeader title="礼金" description="记录自己实际承担的礼金，按对象沉淀往来历史，下次随礼时有据可查。">
      <n-button @click="recipientManagerOpen = true">礼金对象</n-button>
      <n-button type="primary" @click="openCreateRecord">记录礼金</n-button>
    </PageHeader>

    <div class="filter-bar">
      <n-input-number v-model:value="year" :input-props="{ inputmode: 'numeric' }" class="filter-bar__year" :min="2000" :max="2100" />
      <n-input v-model:value="keyword" class="filter-bar__grow" clearable placeholder="搜索对象、场合或备注" @keyup.enter="onSearch">
        <template #prefix>
          <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
        </template>
      </n-input>
      <n-select v-model:value="recipientFilter" class="filter-bar__fixed" clearable placeholder="全部对象" :options="allRecipientOptions" />
      <n-button @click="onSearch">筛选</n-button>
    </div>

    <n-spin :show="loading">
      <StatStrip :items="statItems" />

      <n-card v-if="occasionItems.length" class="surface-panel" title="按场合分布">
        <DonutBreakdown
          layout="split"
          bars
          :items="occasionItems"
          center-label="合计"
          :meta="(item) => stats.countByOccasion[item.name] ? `${stats.countByOccasion[item.name]} 笔` : undefined"
        />
      </n-card>

      <n-card class="surface-panel surface-panel--flush">
        <template #header>
          礼金明细 <span class="section-note">· {{ records.length }} 笔</span>
        </template>
        <n-data-table
          :class="{ 'data-table--cards': isMobile }"
          :columns="isMobile ? recordCardColumns : recordColumns"
          :data="records"
          :row-key="(row: GiftRecord) => row.id"
          :scroll-x="isMobile ? undefined : 940"
        />
        <n-empty v-if="!loading && !records.length" class="panel-empty" description="还没有符合条件的礼金记录" />
      </n-card>
    </n-spin>

    <n-modal v-model:show="recordModalOpen" preset="card" :title="editingRecord ? '编辑礼金记录' : '记录礼金'" style="width: 520px">
      <n-form label-placement="left" label-width="90">
        <n-form-item label="礼金对象" required><n-select v-model:value="recordForm.recipientId" filterable :options="recipientOptions" /></n-form-item>
        <n-form-item label="场合" required><n-input v-model:value="recordForm.occasion" placeholder="例如：结婚、满月、乔迁" /></n-form-item>
        <n-form-item label="礼金日期" required><n-date-picker v-model:formatted-value="recordForm.giftDate" value-format="yyyy-MM-dd" type="date" clearable /></n-form-item>
        <n-form-item label="实际承担" required><n-input-number v-model:value="recordForm.amount" :input-props="{ inputmode: 'decimal' }" :min="0.01" :precision="2" style="width: 100%"><template #prefix>¥</template></n-input-number></n-form-item>
        <n-form-item label="支付方式"><n-input v-model:value="recordForm.paymentMethod" placeholder="微信、现金、银行卡等" /></n-form-item>
        <n-form-item label="备注"><n-input v-model:value="recordForm.note" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" /></n-form-item>
      </n-form>
      <template #footer><div class="modal-footer"><n-button @click="recordModalOpen = false">取消</n-button><n-button type="primary" @click="saveRecord">保存</n-button></div></template>
    </n-modal>

    <n-modal v-model:show="recipientModalOpen" preset="card" :title="editingRecipient ? '编辑礼金对象' : '新建礼金对象'" style="width: 480px">
      <n-form label-placement="left" label-width="76">
        <n-form-item label="名称" required><n-input v-model:value="recipientForm.name" placeholder="例如：张三夫妇、王阿姨一家" /></n-form-item>
        <n-form-item label="关系"><n-input v-model:value="recipientForm.relationship" placeholder="例如：同学、亲戚、同事" /></n-form-item>
        <n-form-item label="备注"><n-input v-model:value="recipientForm.note" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" /></n-form-item>
      </n-form>
      <template #footer><div class="modal-footer"><n-button @click="recipientModalOpen = false">取消</n-button><n-button type="primary" @click="saveRecipient">保存</n-button></div></template>
    </n-modal>

    <n-modal v-model:show="recipientManagerOpen" preset="card" title="礼金对象" style="width: min(760px, calc(100vw - 32px))">
      <div class="gift-recipient-manager-head">
        <n-button size="small" type="primary" @click="openCreateRecipient()">新建对象</n-button>
      </div>
      <div class="gift-recipient-list">
        <div v-for="recipient in recipients" :key="recipient.id" class="gift-recipient-row">
          <div>
            <strong>{{ recipient.name }}</strong>
            <span v-if="recipient.relationship" class="section-note">{{ recipient.relationship }}</span>
            <p v-if="recipient.note" class="gift-recipient-note">{{ recipient.note }}</p>
          </div>
          <div class="gift-recipient-meta">{{ formatMoney(recipient.gift_total) }} · {{ recipient.gift_count }} 笔</div>
          <n-tag v-if="!recipient.is_active" size="small" type="warning">已停用</n-tag>
          <n-space size="small"><n-button size="small" quaternary @click="openEditRecipient(recipient)">编辑</n-button><n-button v-if="recipient.is_active" size="small" quaternary type="error" @click="confirmDeactivateRecipient(recipient)">停用</n-button></n-space>
        </div>
        <n-empty v-if="!recipients.length" description="先建立第一个礼金对象" style="padding: 28px 0" />
      </div>
    </n-modal>

    <n-drawer :show="Boolean(detailRecipient)" :width="420" placement="right" @update:show="(show: boolean) => { if (!show) detailRecipient = null }">
      <n-drawer-content v-if="detailRecipient" :title="detailRecipient.name">
        <n-tag v-if="detailRecipient.relationship" size="small">{{ detailRecipient.relationship }}</n-tag>
        <p class="section-note">累计礼金 {{ formatMoney(detailTotal) }}，共 {{ detailRecords.length }} 笔</p>
        <n-timeline style="margin-top: 24px">
          <n-timeline-item v-for="record in detailRecords" :key="record.id" :title="`${record.occasion} · ${formatMoney(record.amount)}`" :content="record.payment_method || undefined" :time="record.gift_date">
            <span v-if="record.note" class="section-note">{{ record.note }}</span>
          </n-timeline-item>
        </n-timeline>
        <n-empty v-if="!detailRecords.length" description="该对象在当前年份没有记录" />
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
