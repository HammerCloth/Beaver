<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { Loan, LoanRepayment } from '@/types/models'
import * as loanApi from '@/api/loan'
import { formatMoney } from '@/lib/format'
import { localToday } from '@/lib/date'
import { t } from '@/i18n'
import { mobileCardColumns } from '@/lib/mobileCard'
import { useIsMobile } from '@/composables/useIsMobile'
import PageHeader from '@/components/PageHeader.vue'
import StatStrip from '@/components/StatStrip.vue'

const message = useMessage()
const dialog = useDialog()
const { isMobile } = useIsMobile()
const currentYear = new Date().getFullYear()
const year = ref(currentYear)
const keyword = ref('')
const status = ref<string>('all')
const loans = ref<Loan[]>([])
const stats = ref<loanApi.LoanStats>({
  year: currentYear,
  loanCount: 0,
  openCount: 0,
  settledCount: 0,
  principalTotal: 0,
  outstandingTotal: 0,
  repaidThisYear: 0,
})
const loading = ref(true)
const loanModalOpen = ref(false)
const editingLoan = ref<Loan | null>(null)
const detailLoan = ref<Loan | null>(null)
const editingRepayment = ref<LoanRepayment | null>(null)

const today = localToday

const loanForm = ref<loanApi.LoanBody>({
  borrowerName: '',
  relationship: '',
  amount: null,
  loanDate: today(),
  dueDate: null,
  note: '',
})
const repaymentForm = ref<loanApi.RepaymentBody>({
  amount: null,
  repayDate: today(),
  note: '',
})

const statusOptions = [
  { label: t('loans.status.all'), value: 'all' },
  { label: t('loans.status.open'), value: 'open' },
  { label: t('loans.status.settled'), value: 'settled' },
]

function isOverdue(row: Loan) {
  return !row.settled && !!row.due_date && row.due_date < today()
}

const statItems = computed(() => [
  { label: t('loans.stats.outstandingTotal'), value: formatMoney(stats.value.outstandingTotal), tone: stats.value.outstandingTotal > 0 ? ('warning' as const) : ('' as const) },
  { label: t('loans.stats.openCount'), value: stats.value.openCount, unit: t('common.unit.items') },
  { label: t('loans.stats.repaidInYear', { year: stats.value.year }), value: formatMoney(stats.value.repaidThisYear), tone: stats.value.repaidThisYear > 0 ? ('positive' as const) : ('' as const) },
])

function progressCell(row: Loan) {
  const pct = row.amount > 0 ? Math.min(100, (row.repaid_total / row.amount) * 100) : 0
  return h('div', { class: 'progress-cell' }, [
    h('div', { class: 'progress-cell__track' }, [
      h('div', { class: ['progress-cell__fill', row.settled ? 'is-done' : ''], style: { width: `${pct}%` } }),
    ]),
    h('span', { class: 'progress-cell__label' }, `${formatMoney(row.repaid_total)} · ${pct.toFixed(0)}%`),
  ])
}

function statusBadge(row: Loan) {
  if (row.settled) {
    return h('span', { class: 'badge badge--positive' }, t('loans.status.settled'))
  }
  return isOverdue(row)
    ? h('span', { class: 'badge badge--negative' }, t('loans.status.overdue'))
    : h('span', { class: 'badge badge--warning' }, t('loans.status.open'))
}

function loanActions(row: Loan) {
  return [
    h('button', { class: 'text-action', onClick: () => openLoanDetail(row.id) }, t('loans.actions.repay')),
    h('button', { class: 'text-action', onClick: () => openEditLoan(row) }, t('common.actions.edit')),
    h('button', { class: 'text-action text-action--danger', onClick: () => confirmDeleteLoan(row) }, t('common.actions.delete')),
  ]
}

const loanColumns: DataTableColumns<Loan> = [
  {
    title: t('loans.columns.borrower'),
    key: 'borrower_name',
    minWidth: 140,
    render: (row) =>
      h('div', { class: 'cell-stack' }, [
        h('button', { class: 'text-action cell-main', style: 'text-align: left', onClick: () => openLoanDetail(row.id) }, row.borrower_name),
        row.relationship ? h('span', { class: 'cell-muted' }, row.relationship) : null,
      ]),
  },
  { title: t('loans.columns.loanDate'), key: 'loan_date', width: 112, render: (row) => h('span', { class: 'cell-muted' }, row.loan_date) },
  {
    title: t('loans.columns.dueDate'),
    key: 'due_date',
    width: 112,
    render: (row) => h('span', { class: isOverdue(row) ? 'text-danger' : 'cell-muted' }, row.due_date || '—'),
  },
  { title: t('loans.columns.principal'), key: 'amount', width: 110, align: 'right', render: (row) => h('span', { class: 'amount' }, formatMoney(row.amount)) },
  {
    title: t('loans.columns.progress'),
    key: 'repaid_total',
    width: 170,
    render: progressCell,
  },
  {
    title: t('loans.columns.remaining'),
    key: 'remaining',
    width: 110,
    align: 'right',
    render: (row) => h('span', { class: ['amount', row.remaining > 0 ? '' : 'amount--muted'] }, formatMoney(row.remaining)),
  },
  {
    title: t('loans.columns.status'),
    key: 'settled',
    width: 96,
    render: statusBadge,
  },
  {
    title: '',
    key: 'actions',
    width: 200,
    align: 'right',
    render: (row) => h('div', { class: 'table-actions' }, loanActions(row)),
  },
]

const loanCardColumns = mobileCardColumns<Loan>((row) => ({
  title: [
    h('button', { class: 'text-action cell-main', style: 'text-align: left', onClick: () => openLoanDetail(row.id) }, row.borrower_name),
    row.relationship ? h('span', { class: 'cell-muted' }, row.relationship) : null,
  ],
  value: [
    h('span', { class: 'cell-muted' }, `${t('loans.card.remaining')} `),
    h('span', { class: ['amount', row.remaining > 0 ? '' : 'amount--muted'] }, formatMoney(row.remaining)),
  ],
  meta: [
    statusBadge(row),
    t('loans.card.loanDate', { date: row.loan_date }),
    row.due_date ? h('span', { class: isOverdue(row) ? 'text-danger' : '' }, t('loans.card.dueDate', { date: row.due_date })) : null,
    t('loans.card.principal', { amount: formatMoney(row.amount) }),
  ],
  extra: progressCell(row),
  actions: loanActions(row),
}))

async function load() {
  loading.value = true
  try {
    const [loanRows, statsData] = await Promise.all([
      loanApi.listLoans({ keyword: keyword.value || undefined, status: status.value }),
      loanApi.loanStats(year.value),
    ])
    loans.value = loanRows
    stats.value = statsData
    if (detailLoan.value) {
      const latest = loanRows.find((row) => row.id === detailLoan.value?.id)
      if (latest) {
        await refreshDetail(latest.id)
      }
    }
  } catch {
    message.error(t('loans.toast.loadFailed'))
  } finally {
    loading.value = false
  }
}

watch([year, status], load, { immediate: true })

function onSearch() {
  load()
}

function openCreateLoan() {
  editingLoan.value = null
  loanForm.value = {
    borrowerName: '',
    relationship: '',
    amount: null,
    loanDate: today(),
    dueDate: null,
    note: '',
  }
  loanModalOpen.value = true
}

function openEditLoan(row: Loan) {
  editingLoan.value = row
  loanForm.value = {
    borrowerName: row.borrower_name,
    relationship: row.relationship || '',
    amount: row.amount,
    loanDate: row.loan_date,
    dueDate: row.due_date || null,
    note: row.note || '',
  }
  loanModalOpen.value = true
}

async function saveLoan() {
  try {
    if (editingLoan.value) {
      await loanApi.updateLoan(editingLoan.value.id, loanForm.value)
      message.success(t('loans.toast.loanUpdated'))
    } else {
      await loanApi.createLoan(loanForm.value)
      message.success(t('loans.toast.loanCreated'))
    }
    loanModalOpen.value = false
    await load()
  } catch (error) {
    message.error(apiMessage(error, t('common.status.saveFailed')))
  }
}

function confirmDeleteLoan(row: Loan) {
  dialog.warning({
    title: t('loans.dialog.deleteLoanTitle'),
    content: t('loans.dialog.deleteLoanContent', { name: row.borrower_name, amount: formatMoney(row.amount) }),
    positiveText: t('common.actions.delete'),
    negativeText: t('common.actions.cancel'),
    onPositiveClick: async () => {
      try {
        await loanApi.deleteLoan(row.id)
        if (detailLoan.value?.id === row.id) {
          detailLoan.value = null
        }
        message.success(t('common.status.deleted'))
        await load()
      } catch {
        message.error(t('loans.toast.deleteFailed'))
      }
    },
  })
}

async function openLoanDetail(id: string) {
  await refreshDetail(id)
  resetRepaymentForm()
}

async function refreshDetail(id: string) {
  try {
    detailLoan.value = await loanApi.getLoan(id)
  } catch {
    detailLoan.value = null
    message.error(t('loans.toast.repaymentsLoadFailed'))
  }
}

function resetRepaymentForm(repayment?: LoanRepayment) {
  editingRepayment.value = repayment ?? null
  repaymentForm.value = {
    amount: repayment ? repayment.amount : detailLoan.value && !detailLoan.value.settled ? detailLoan.value.remaining : null,
    repayDate: repayment?.repay_date || today(),
    note: repayment?.note || '',
  }
}

async function saveRepayment() {
  if (!detailLoan.value) return
  try {
    if (editingRepayment.value) {
      await loanApi.updateRepayment(detailLoan.value.id, editingRepayment.value.id, repaymentForm.value)
      message.success(t('loans.toast.repaymentUpdated'))
    } else {
      await loanApi.createRepayment(detailLoan.value.id, repaymentForm.value)
      message.success(t('loans.toast.repaymentCreated'))
    }
    await load()
    resetRepaymentForm()
  } catch (error) {
    message.error(apiMessage(error, t('common.status.saveFailed')))
  }
}

function confirmDeleteRepayment(row: LoanRepayment) {
  if (!detailLoan.value) return
  const loanId = detailLoan.value.id
  dialog.warning({
    title: t('loans.dialog.deleteRepaymentTitle'),
    content: t('loans.dialog.deleteRepaymentContent', { date: row.repay_date, amount: formatMoney(row.amount) }),
    positiveText: t('common.actions.delete'),
    negativeText: t('common.actions.cancel'),
    onPositiveClick: async () => {
      try {
        await loanApi.deleteRepayment(loanId, row.id)
        message.success(t('common.status.deleted'))
        await load()
        resetRepaymentForm()
      } catch {
        message.error(t('loans.toast.deleteFailed'))
      }
    },
  })
}

function apiMessage(error: unknown, fallback: string) {
  const data = (error as { response?: { data?: { error?: string } } })?.response?.data
  return typeof data?.error === 'string' ? data.error : fallback
}
</script>

<template>
  <div class="page-stack">
    <PageHeader :title="t('loans.title')" :description="t('loans.description')">
      <n-button type="primary" @click="openCreateLoan">{{ t('loans.newLoan') }}</n-button>
    </PageHeader>

    <div class="filter-bar">
      <n-select v-model:value="status" class="filter-bar__fixed" :options="statusOptions" />
      <n-input v-model:value="keyword" class="filter-bar__grow" clearable :placeholder="t('loans.searchPlaceholder')" @keyup.enter="onSearch">
        <template #prefix>
          <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
        </template>
      </n-input>
      <n-input-number v-model:value="year" :input-props="{ inputmode: 'numeric' }" class="filter-bar__year" :min="2000" :max="2100" />
      <n-button @click="onSearch">{{ t('common.actions.filter') }}</n-button>
    </div>

    <n-spin :show="loading">
      <StatStrip :items="statItems" />

      <n-card class="surface-panel surface-panel--flush">
        <template #header>
          {{ t('loans.listTitle') }} <span class="section-note">· {{ t('loans.recordCount', { n: loans.length }) }}</span>
        </template>
        <n-data-table
          :class="{ 'data-table--cards': isMobile }"
          :columns="isMobile ? loanCardColumns : loanColumns"
          :data="loans"
          :row-key="(row: Loan) => row.id"
          :scroll-x="isMobile ? undefined : 1080"
        />
        <n-empty v-if="!loading && !loans.length" class="panel-empty" :description="t('loans.empty')" />
      </n-card>
    </n-spin>

    <n-modal v-model:show="loanModalOpen" preset="card" :title="editingLoan ? t('loans.editLoan') : t('loans.newLoan')" style="width: 520px">
      <n-form label-placement="left" label-width="90">
        <n-form-item :label="t('loans.columns.borrower')" required>
          <n-input v-model:value="loanForm.borrowerName" :placeholder="t('loans.form.borrowerPlaceholder')" />
        </n-form-item>
        <n-form-item :label="t('loans.form.relationship')">
          <n-input v-model:value="loanForm.relationship" :placeholder="t('loans.form.relationshipPlaceholder')" />
        </n-form-item>
        <n-form-item :label="t('loans.columns.principal')" required>
          <n-input-number v-model:value="loanForm.amount" :input-props="{ inputmode: 'decimal' }" :min="0.01" :precision="2" style="width: 100%">
            <template #prefix>¥</template>
          </n-input-number>
        </n-form-item>
        <n-form-item :label="t('loans.columns.loanDate')" required>
          <n-date-picker v-model:formatted-value="loanForm.loanDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
        </n-form-item>
        <n-form-item :label="t('loans.columns.dueDate')">
          <n-date-picker v-model:formatted-value="loanForm.dueDate" value-format="yyyy-MM-dd" type="date" clearable style="width: 100%" />
        </n-form-item>
        <n-form-item :label="t('loans.form.note')">
          <n-input v-model:value="loanForm.note" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </n-form-item>
      </n-form>
      <template #footer><div class="modal-footer"><n-button @click="loanModalOpen = false">{{ t('common.actions.cancel') }}</n-button><n-button type="primary" @click="saveLoan">{{ t('common.actions.save') }}</n-button></div></template>
    </n-modal>

    <n-drawer :show="Boolean(detailLoan)" :width="460" placement="right" @update:show="(show: boolean) => { if (!show) detailLoan = null }">
      <n-drawer-content v-if="detailLoan" :title="detailLoan.borrower_name">
        <div class="drawer-badges">
          <span v-if="detailLoan.relationship" class="badge badge--plain">{{ detailLoan.relationship }}</span>
          <span class="badge" :class="detailLoan.settled ? 'badge--positive' : 'badge--warning'">
            {{ detailLoan.settled ? t('loans.status.settled') : t('loans.status.open') }}
          </span>
        </div>
        <div class="drawer-stats">
          <div><span>{{ t('loans.columns.principal') }}</span><strong>{{ formatMoney(detailLoan.amount) }}</strong></div>
          <div><span>{{ t('loans.drawer.repaid') }}</span><strong>{{ formatMoney(detailLoan.repaid_total) }}</strong></div>
          <div><span>{{ t('loans.columns.remaining') }}</span><strong>{{ formatMoney(detailLoan.remaining) }}</strong></div>
        </div>
        <p v-if="detailLoan.due_date" class="section-note">{{ t('loans.drawer.dueDate', { date: detailLoan.due_date }) }}</p>
        <p v-if="detailLoan.note" class="loan-note">{{ detailLoan.note }}</p>

        <h3 class="loan-drawer-title">{{ editingRepayment ? t('loans.drawer.editRepayment') : t('loans.drawer.addRepayment') }}</h3>
        <n-form label-placement="left" label-width="76">
          <n-form-item :label="t('loans.drawer.amount')" required>
            <n-input-number v-model:value="repaymentForm.amount" :input-props="{ inputmode: 'decimal' }" :min="0.01" :precision="2" :max="detailLoan.remaining + (editingRepayment?.amount ?? 0)" style="width: 100%">
              <template #prefix>¥</template>
            </n-input-number>
          </n-form-item>
          <n-form-item :label="t('loans.drawer.repayDate')" required>
            <n-date-picker v-model:formatted-value="repaymentForm.repayDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
          </n-form-item>
          <n-form-item :label="t('loans.form.note')">
            <n-input v-model:value="repaymentForm.note" :placeholder="t('loans.drawer.repaymentNotePlaceholder')" />
          </n-form-item>
        </n-form>
        <n-space>
          <n-button type="primary" :disabled="detailLoan.settled && !editingRepayment" @click="saveRepayment">
            {{ editingRepayment ? t('loans.drawer.saveChanges') : t('loans.drawer.addRepayment') }}
          </n-button>
          <n-button v-if="editingRepayment" @click="resetRepaymentForm()">{{ t('loans.drawer.cancelEdit') }}</n-button>
        </n-space>

        <h3 class="loan-drawer-title">{{ t('loans.drawer.repaymentsTitle') }}</h3>
        <div class="loan-repayment-list">
          <div v-for="item in detailLoan.repayments" :key="item.id" class="loan-repayment-row">
            <div>
              <strong>{{ formatMoney(item.amount) }}</strong>
              <span class="section-note">{{ item.repay_date }}</span>
              <p v-if="item.note" class="loan-note">{{ item.note }}</p>
            </div>
            <n-space size="small">
              <n-button size="small" @click="resetRepaymentForm(item)">{{ t('common.actions.edit') }}</n-button>
              <n-button size="small" quaternary type="error" @click="confirmDeleteRepayment(item)">{{ t('common.actions.delete') }}</n-button>
            </n-space>
          </div>
          <n-empty v-if="!detailLoan.repayments?.length" :description="t('loans.drawer.repaymentsEmpty')" style="padding: 20px 0" />
        </div>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<style scoped>
.loan-drawer-title { margin: 24px 0 12px; font-size: 15px; }
.loan-note { margin: 5px 0 0; color: var(--text-2); font-size: 13px; }
.loan-repayment-list { display: grid; gap: 8px; }
.loan-repayment-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 12px; align-items: center; padding: 12px 0; border-bottom: 1px solid var(--line-soft); }
.loan-repayment-row:last-child { border-bottom: 0; }
.loan-repayment-row .section-note { margin-left: 8px; }
@media (max-width: 760px) {
}
</style>
