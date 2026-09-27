<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import type { Account } from '@/types/models'
import * as accountApi from '@/api/account'
import * as snapshotApi from '@/api/snapshot'
import { DIM_ACCOUNT_OWNER, DIM_ACCOUNT_TYPE, DIM_EVENT_CATEGORY, useSettingsStore } from '@/stores/settings'
import { amountTone, formatMoney, formatSignedMoney } from '@/lib/format'
import { useCategoryColor } from '@/composables/useCategoryColor'
import PageHeader from '@/components/PageHeader.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const settings = useSettingsStore()
const { categoryColor } = useCategoryColor()

const idParam = computed(() => (route.params.id as string) || '')
const isEdit = computed(() => route.name === 'snapshot-edit')

const date = ref<string | null>(null)
const note = ref('')
const accounts = ref<Account[]>([])
const balances = ref<Record<string, number | null>>({})
type Flow = 'in' | 'out'
const events = ref<
  { category: string; description: string; absAmount: number | null; flow: Flow }[]
>([])
const loading = ref(false)
/** 新建时预填自上一次快照，用于对比变化 */
const previous = ref<Record<string, number>>({})
const previousDate = ref<string | null>(null)

/** 与后端 BalanceLogic 一致：负债按负值计入 */
function effective(type: string, v: number) {
  return type === 'credit' ? -Math.abs(v) : v
}

const groups = computed(() => {
  const order = settings.selectOptions(DIM_ACCOUNT_TYPE).map((o) => o.value)
  const map = new Map<string, Account[]>()
  for (const a of accounts.value) {
    map.set(a.type, [...(map.get(a.type) ?? []), a])
  }
  return [...map.entries()]
    .sort((x, y) => (order.indexOf(x[0]) + 1 || 99) - (order.indexOf(y[0]) + 1 || 99))
    .map(([type, list]) => ({
      type,
      label: settings.label(DIM_ACCOUNT_TYPE, type),
      color: categoryColor(DIM_ACCOUNT_TYPE, type),
      accounts: list,
      subtotal: list.reduce((sum, a) => sum + effective(type, balances.value[a.id] ?? 0), 0),
    }))
})

const filledCount = computed(() => accounts.value.filter((a) => balances.value[a.id] != null).length)
const netWorth = computed(() => accounts.value.reduce((sum, a) => sum + effective(a.type, balances.value[a.id] ?? 0), 0))
const previousNetWorth = computed(() =>
  accounts.value.reduce((sum, a) => sum + effective(a.type, previous.value[a.id] ?? 0), 0),
)
const hasPrevious = computed(() => Object.keys(previous.value).length > 0)
const eventNet = computed(() => events.value.reduce((sum, e) => sum + signedAmount(e), 0))

function accountDelta(a: Account) {
  const now = balances.value[a.id]
  const before = previous.value[a.id]
  if (now == null || before == null) {
    return null
  }
  return effective(a.type, now) - effective(a.type, before)
}

const categorySelectOptions = computed(() => settings.selectOptions(DIM_EVENT_CATEGORY))

function defaultCategory() {
  const opts = categorySelectOptions.value
  return opts[0]?.value ?? 'other'
}

async function load() {
  await settings.load()
  const accs = await accountApi.listAccounts()
  accounts.value = accs
  for (const a of accs) {
    balances.value[a.id] = null
  }

  if (isEdit.value && idParam.value) {
    const s = await snapshotApi.getSnapshot(idParam.value)
    date.value = s.date
    note.value = s.note ?? ''
    for (const row of s.items) {
      balances.value[row.accountId] = row.balance
    }
    events.value =
      s.events?.map((e) => ({
        category: e.category,
        description: e.description,
        absAmount: Math.abs(e.amount),
        flow: e.amount < 0 ? ('out' as const) : ('in' as const),
      })) ?? []
  } else {
    const latest = await snapshotApi.latestSnapshot()
    if (latest?.items?.length) {
      previousDate.value = latest.date
      for (const row of latest.items) {
        balances.value[row.accountId] = row.balance
        previous.value[row.accountId] = row.balance
      }
    }
    const q = route.query.date
    if (typeof q === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(q)) {
      date.value = q
    } else {
      const d = new Date()
      date.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    }
  }
}

function addEvent() {
  events.value.push({
    category: defaultCategory(),
    description: '',
    absAmount: null,
    flow: 'out',
  })
}

function removeEvent(i: number) {
  events.value.splice(i, 1)
}

function signedAmount(row: { absAmount: number | null; flow: Flow }) {
  if (row.absAmount == null || Number.isNaN(row.absAmount)) {
    return 0
  }
  const v = Math.abs(row.absAmount)
  return row.flow === 'out' ? -v : v
}

async function submit() {
  if (!date.value) {
    message.error('请选择日期')
    return
  }
  const items = accounts.value.map((a) => ({
    accountId: a.id,
    balance: balances.value[a.id],
  }))
  if (items.some((i) => i.balance === null || Number.isNaN(i.balance as number))) {
    message.error('请为每个账户填写余额')
    return
  }
  const incomplete = events.value.some((e) => {
    const hasText = Boolean(e.description?.trim())
    const hasAmount = e.absAmount != null && !Number.isNaN(e.absAmount) && e.absAmount > 0
    return (hasText && !hasAmount) || (hasAmount && !hasText)
  })
  if (incomplete) {
    message.error('请把大事记的说明和金额都填完整')
    return
  }
  const evs = events.value
    .filter((e) => e.description?.trim())
    .map((e) => ({
      category: e.category,
      description: e.description.trim(),
      amount: signedAmount(e),
    }))
  loading.value = true
  try {
    if (isEdit.value && idParam.value) {
      await snapshotApi.updateSnapshot(idParam.value, {
        date: date.value,
        note: note.value || null,
        items: items.map((i) => ({ accountId: i.accountId, balance: i.balance as number })),
        events: evs,
      })
      message.success('已保存')
      await router.replace(`/snapshots/${idParam.value}`)
    } else {
      const s = await snapshotApi.createSnapshot({
        date: date.value,
        note: note.value || null,
        items: items.map((i) => ({ accountId: i.accountId, balance: i.balance as number })),
        events: evs,
      })
      message.success('已创建')
      await router.replace(`/snapshots/${s.id}`)
    }
  } catch {
    message.error('保存失败（日期重复或未填余额等）')
  } finally {
    loading.value = false
  }
}

/** 没有历史记录（直接打开、刷新后）时 router.back() 无效，回到列表或详情 */
function goBack() {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.push(isEdit.value && idParam.value ? `/snapshots/${idParam.value}` : '/snapshots')
  }
}

onMounted(() => {
  load().catch(() => message.error('加载失败'))
})
</script>

<template>
  <div class="page-stack snapshot-form">
    <PageHeader :title="isEdit ? '编辑快照' : '记录快照'">
      <template #eyebrow>
        <button type="button" class="page-back" @click="goBack">← 返回</button>
      </template>
      <template #description>
        <template v-if="!isEdit && previousDate">余额已按 {{ previousDate }} 的快照预填，只需修改有变化的账户。</template>
        <template v-else>填写这一天每个账户的余额，负债账户直接填欠款金额。</template>
      </template>
    </PageHeader>

    <n-card class="surface-panel" title="基本信息">
      <div class="form-grid">
        <n-form-item label="快照日期" :show-feedback="false">
          <n-date-picker v-model:formatted-value="date" type="date" value-format="yyyy-MM-dd" style="width: 100%" />
        </n-form-item>
        <n-form-item label="备注" :show-feedback="false">
          <n-input v-model:value="note" placeholder="可选，例如：年终奖到账、基金调仓" />
        </n-form-item>
      </div>
    </n-card>

    <n-card class="surface-panel surface-panel--flush">
      <template #header>
        账户余额 <span class="section-note">· 已填 {{ filledCount }}/{{ accounts.length }}</span>
      </template>
      <div v-for="group in groups" :key="group.type" class="balance-group">
        <div class="balance-group__head">
          <span class="cell-name"><span class="swatch" :style="{ background: group.color }" />{{ group.label }}</span>
          <span class="amount">{{ formatMoney(group.subtotal) }}</span>
        </div>
        <div v-for="a in group.accounts" :key="a.id" class="balance-row">
          <div class="cell-stack">
            <span class="cell-main">{{ a.name }}</span>
            <span class="cell-muted">{{ settings.label(DIM_ACCOUNT_OWNER, a.owner) }}</span>
          </div>
          <span class="balance-row__prev">
            <template v-if="previous[a.id] != null">上次 {{ formatMoney(previous[a.id]) }}</template>
          </span>
          <span class="balance-row__delta amount" :class="accountDelta(a) ? `amount--${amountTone(accountDelta(a) ?? 0)}` : 'amount--muted'">
            <template v-if="accountDelta(a) !== null">{{ accountDelta(a) ? formatSignedMoney(accountDelta(a) ?? 0) : '无变化' }}</template>
          </span>
          <n-input-number
            v-model:value="balances[a.id]"
            class="balance-row__input"
            :show-button="false"
            :status="balances[a.id] == null ? 'warning' : undefined"
            placeholder="0.00"
          >
            <template #prefix>¥</template>
          </n-input-number>
        </div>
      </div>
    </n-card>

    <n-card class="surface-panel">
      <template #header>
        大事记 <span class="section-note">· 这段时间的重要收支，可选</span>
      </template>
      <div v-if="events.length" class="event-list">
        <div v-for="(ev, i) in events" :key="i" class="event-row">
          <n-select v-model:value="ev.category" :options="categorySelectOptions" />
          <n-input v-model:value="ev.description" placeholder="说明，例如：换手机" />
          <n-radio-group v-model:value="ev.flow">
            <n-radio-button value="out">支出</n-radio-button>
            <n-radio-button value="in">收入</n-radio-button>
          </n-radio-group>
          <n-input-number v-model:value="ev.absAmount" placeholder="金额" :min="0" :show-button="false">
            <template #prefix>¥</template>
          </n-input-number>
          <n-button quaternary type="error" @click="removeEvent(i)">移除</n-button>
        </div>
      </div>
      <n-button dashed block :style="events.length ? 'margin-top: 12px' : ''" @click="addEvent">+ 添加大事记</n-button>
    </n-card>

    <div class="action-bar">
      <div class="action-bar__summary">
        <span>净资产 <strong>{{ formatMoney(netWorth) }}</strong></span>
        <span v-if="hasPrevious" :class="`amount--${amountTone(netWorth - previousNetWorth)}`">
          较上次 {{ formatSignedMoney(netWorth - previousNetWorth) }}
        </span>
        <span v-if="events.length">大事记 {{ formatSignedMoney(eventNet) }}</span>
      </div>
      <div class="inline-control">
        <n-button @click="goBack">取消</n-button>
        <n-button type="primary" :loading="loading" @click="submit">{{ isEdit ? '保存修改' : '保存快照' }}</n-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.balance-group + .balance-group {
  border-top: 1px solid var(--line-soft);
}

.balance-group__head {
  display: flex;
  justify-content: space-between;
  padding: 10px 20px;
  background: var(--surface-0);
  color: var(--text-2);
  font-size: 13px;
  font-weight: 500;
}

.balance-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px 110px 180px;
  align-items: center;
  gap: 16px;
  padding: 10px 20px;
  border-top: 1px solid var(--line-soft);
}

.balance-row__prev {
  color: var(--text-3);
  font-size: 12px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.balance-row__delta {
  font-size: 13px;
  text-align: right;
}

.balance-row__input :deep(input) {
  text-align: right;
}

.event-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.event-row {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr) auto 150px auto;
  align-items: center;
  gap: 8px;
}

@media (max-width: 820px) {
  .balance-row {
    grid-template-columns: minmax(0, 1fr) 150px;
    padding: 10px 16px;
  }

  .balance-row__prev {
    display: none;
  }

  .balance-row__delta {
    grid-column: 1;
    grid-row: 2;
    text-align: left;
  }

  .balance-row__input {
    grid-column: 2;
    grid-row: 1 / 3;
  }

  .event-row {
    grid-template-columns: 1fr 1fr;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--line-soft);
  }

  .event-row > :nth-child(2) {
    grid-column: 1 / -1;
    grid-row: 1;
  }
}
</style>
