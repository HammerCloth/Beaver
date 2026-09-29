<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { Loan, LoanRepayment } from '@/types/models'
import * as loanApi from '@/api/loan'
import { formatMoney } from '@/lib/format'
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

/** 本地日期 YYYY-MM-DD（toISOString 是 UTC，东八区凌晨会差一天） */
const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

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
  { label: '全部', value: 'all' },
  { label: '未还清', value: 'open' },
  { label: '已还清', value: 'settled' },
]

function isOverdue(row: Loan) {
  return !row.settled && !!row.due_date && row.due_date < today()
}

const statItems = computed(() => [
  { label: '未还总额', value: formatMoney(stats.value.outstandingTotal), tone: stats.value.outstandingTotal > 0 ? ('warning' as const) : ('' as const) },
  { label: '未还清', value: stats.value.openCount, unit: '笔' },
  { label: `${stats.value.year} 年收回`, value: formatMoney(stats.value.repaidThisYear), tone: stats.value.repaidThisYear > 0 ? ('positive' as const) : ('' as const) },
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
    return h('span', { class: 'badge badge--positive' }, '已还清')
  }
  return isOverdue(row)
    ? h('span', { class: 'badge badge--negative' }, '已逾期')
    : h('span', { class: 'badge badge--warning' }, '未还清')
}

function loanActions(row: Loan) {
  return [
    h('button', { class: 'text-action', onClick: () => openLoanDetail(row.id) }, '还款'),
    h('button', { class: 'text-action', onClick: () => openEditLoan(row) }, '编辑'),
    h('button', { class: 'text-action text-action--danger', onClick: () => confirmDeleteLoan(row) }, '删除'),
  ]
}

const loanColumns: DataTableColumns<Loan> = [
  {
    title: '借款人',
    key: 'borrower_name',
    minWidth: 140,
    render: (row) =>
      h('div', { class: 'cell-stack' }, [
        h('button', { class: 'text-action cell-main', style: 'text-align: left', onClick: () => openLoanDetail(row.id) }, row.borrower_name),
        row.relationship ? h('span', { class: 'cell-muted' }, row.relationship) : null,
      ]),
  },
  { title: '借款日', key: 'loan_date', width: 112, render: (row) => h('span', { class: 'cell-muted' }, row.loan_date) },
  {
    title: '约定还日',
    key: 'due_date',
    width: 112,
    render: (row) => h('span', { class: isOverdue(row) ? 'text-danger' : 'cell-muted' }, row.due_date || '—'),
  },
  { title: '本金', key: 'amount', width: 110, align: 'right', render: (row) => h('span', { class: 'amount' }, formatMoney(row.amount)) },
  {
    title: '还款进度',
    key: 'repaid_total',
    width: 170,
    render: progressCell,
  },
  {
    title: '剩余',
    key: 'remaining',
    width: 110,
    align: 'right',
    render: (row) => h('span', { class: ['amount', row.remaining > 0 ? '' : 'amount--muted'] }, formatMoney(row.remaining)),
  },
  {
    title: '状态',
    key: 'settled',
    width: 96,
    render: statusBadge,
  },
  {
    title: '',
    key: 'actions',
    width: 160,
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
    h('span', { class: 'cell-muted' }, '剩余 '),
    h('span', { class: ['amount', row.remaining > 0 ? '' : 'amount--muted'] }, formatMoney(row.remaining)),
  ],
  meta: [
    statusBadge(row),
    `借 ${row.loan_date}`,
    row.due_date ? h('span', { class: isOverdue(row) ? 'text-danger' : '' }, `约定 ${row.due_date}`) : null,
    `本金 ${formatMoney(row.amount)}`,
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
    message.error('加载借款数据失败')
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
      message.success('借款已更新')
    } else {
      await loanApi.createLoan(loanForm.value)
      message.success('借款已记录')
    }
    loanModalOpen.value = false
    await load()
  } catch (error) {
    message.error(apiMessage(error, '保存失败'))
  }
}

function confirmDeleteLoan(row: Loan) {
  dialog.warning({
    title: '删除借款',
    content: `确定删除借给 ${row.borrower_name} 的 ${formatMoney(row.amount)} 吗？关联的还款记录会一并删除。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await loanApi.deleteLoan(row.id)
        if (detailLoan.value?.id === row.id) {
          detailLoan.value = null
        }
        message.success('已删除')
        await load()
      } catch {
        message.error('删除失败')
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
    message.error('加载还款记录失败')
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
      message.success('还款记录已更新')
    } else {
      await loanApi.createRepayment(detailLoan.value.id, repaymentForm.value)
      message.success('已登记一笔还款')
    }
    await load()
    resetRepaymentForm()
  } catch (error) {
    message.error(apiMessage(error, '保存失败'))
  }
}

function confirmDeleteRepayment(row: LoanRepayment) {
  if (!detailLoan.value) return
  const loanId = detailLoan.value.id
  dialog.warning({
    title: '删除还款记录',
    content: `确定删除 ${row.repay_date} 的 ${formatMoney(row.amount)} 还款吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await loanApi.deleteRepayment(loanId, row.id)
        message.success('已删除')
        await load()
        resetRepaymentForm()
      } catch {
        message.error('删除失败')
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
    <PageHeader title="借款" description="记录别人向我们借的钱，按批次登记还款；借款不计入资产快照。">
      <n-button type="primary" @click="openCreateLoan">新增借款</n-button>
    </PageHeader>

    <div class="filter-bar">
      <n-select v-model:value="status" class="filter-bar__fixed" :options="statusOptions" />
      <n-input v-model:value="keyword" class="filter-bar__grow" clearable placeholder="搜索借款人、关系或备注" @keyup.enter="onSearch">
        <template #prefix>
          <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
        </template>
      </n-input>
      <n-input-number v-model:value="year" class="filter-bar__year" :min="2000" :max="2100" />
      <n-button @click="onSearch">筛选</n-button>
    </div>

    <n-spin :show="loading">
      <StatStrip :items="statItems" />

      <n-card class="surface-panel surface-panel--flush">
        <template #header>
          借款明细 <span class="section-note">· {{ loans.length }} 笔</span>
        </template>
        <n-data-table
          :class="{ 'data-table--cards': isMobile }"
          :columns="isMobile ? loanCardColumns : loanColumns"
          :data="loans"
          :row-key="(row: Loan) => row.id"
          :scroll-x="isMobile ? undefined : 1040"
        />
        <n-empty v-if="!loading && !loans.length" class="panel-empty" description="还没有符合条件的借款记录" />
      </n-card>
    </n-spin>

    <n-modal v-model:show="loanModalOpen" preset="card" :title="editingLoan ? '编辑借款' : '新增借款'" style="width: 520px">
      <n-form label-placement="left" label-width="90">
        <n-form-item label="借款人" required>
          <n-input v-model:value="loanForm.borrowerName" placeholder="谁向我们借的钱" />
        </n-form-item>
        <n-form-item label="关系">
          <n-input v-model:value="loanForm.relationship" placeholder="例如：亲戚、同事、朋友" />
        </n-form-item>
        <n-form-item label="本金" required>
          <n-input-number v-model:value="loanForm.amount" :min="0.01" :precision="2" style="width: 100%">
            <template #prefix>¥</template>
          </n-input-number>
        </n-form-item>
        <n-form-item label="借款日" required>
          <n-date-picker v-model:formatted-value="loanForm.loanDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
        </n-form-item>
        <n-form-item label="约定还日">
          <n-date-picker v-model:formatted-value="loanForm.dueDate" value-format="yyyy-MM-dd" type="date" clearable style="width: 100%" />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="loanForm.note" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </n-form-item>
      </n-form>
      <template #footer><div class="modal-footer"><n-button @click="loanModalOpen = false">取消</n-button><n-button type="primary" @click="saveLoan">保存</n-button></div></template>
    </n-modal>

    <n-drawer :show="Boolean(detailLoan)" :width="460" placement="right" @update:show="(show: boolean) => { if (!show) detailLoan = null }">
      <n-drawer-content v-if="detailLoan" :title="detailLoan.borrower_name">
        <div class="drawer-badges">
          <span v-if="detailLoan.relationship" class="badge badge--plain">{{ detailLoan.relationship }}</span>
          <span class="badge" :class="detailLoan.settled ? 'badge--positive' : 'badge--warning'">
            {{ detailLoan.settled ? '已还清' : '未还清' }}
          </span>
        </div>
        <div class="drawer-stats">
          <div><span>本金</span><strong>{{ formatMoney(detailLoan.amount) }}</strong></div>
          <div><span>已还</span><strong>{{ formatMoney(detailLoan.repaid_total) }}</strong></div>
          <div><span>剩余</span><strong>{{ formatMoney(detailLoan.remaining) }}</strong></div>
        </div>
        <p v-if="detailLoan.due_date" class="section-note">约定还日 {{ detailLoan.due_date }}</p>
        <p v-if="detailLoan.note" class="loan-note">{{ detailLoan.note }}</p>

        <h3 class="loan-drawer-title">{{ editingRepayment ? '编辑还款' : '登记还款' }}</h3>
        <n-form label-placement="left" label-width="76">
          <n-form-item label="金额" required>
            <n-input-number v-model:value="repaymentForm.amount" :min="0.01" :precision="2" :max="detailLoan.remaining + (editingRepayment?.amount ?? 0)" style="width: 100%">
              <template #prefix>¥</template>
            </n-input-number>
          </n-form-item>
          <n-form-item label="还款日" required>
            <n-date-picker v-model:formatted-value="repaymentForm.repayDate" value-format="yyyy-MM-dd" type="date" style="width: 100%" />
          </n-form-item>
          <n-form-item label="备注">
            <n-input v-model:value="repaymentForm.note" placeholder="例如：第一次还款、微信转账" />
          </n-form-item>
        </n-form>
        <n-space>
          <n-button type="primary" :disabled="detailLoan.settled && !editingRepayment" @click="saveRepayment">
            {{ editingRepayment ? '保存修改' : '登记还款' }}
          </n-button>
          <n-button v-if="editingRepayment" @click="resetRepaymentForm()">取消编辑</n-button>
        </n-space>

        <h3 class="loan-drawer-title">分批还款</h3>
        <div class="loan-repayment-list">
          <div v-for="item in detailLoan.repayments" :key="item.id" class="loan-repayment-row">
            <div>
              <strong>{{ formatMoney(item.amount) }}</strong>
              <span class="section-note">{{ item.repay_date }}</span>
              <p v-if="item.note" class="loan-note">{{ item.note }}</p>
            </div>
            <n-space size="small">
              <n-button size="small" @click="resetRepaymentForm(item)">编辑</n-button>
              <n-button size="small" quaternary type="error" @click="confirmDeleteRepayment(item)">删除</n-button>
            </n-space>
          </div>
          <n-empty v-if="!detailLoan.repayments?.length" description="还没有还款记录，可按批次登记" style="padding: 20px 0" />
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
