<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import type { Account } from '@/types/models'
import * as accountApi from '@/api/account'
import * as snapshotApi from '@/api/snapshot'
import * as fxApi from '@/api/fx'
import { t } from '@/i18n'
import { fxSourceText, type FxQuote } from '@/api/fx'
import { DIM_ACCOUNT_OWNER, DIM_ACCOUNT_TYPE, DIM_EVENT_CATEGORY, useSettingsStore } from '@/stores/settings'
import { amountTone, formatMoney, formatSignedMoney } from '@/lib/format'
import {
  BASE_CURRENCY,
  CURRENCIES,
  FOREIGN_CURRENCIES,
  currencyLabel,
  currencySymbol,
  type CurrencyCode,
  type ForeignCurrency,
} from '@/lib/currency'
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
/** 原币金额 */
const balances = ref<Record<string, number | null>>({})
const currencies = ref<Record<string, CurrencyCode>>({})
/** 1 外币 = x 人民币，按快照日期获取，可手动修改 */
const fxRates = ref<Record<ForeignCurrency, number | null>>({ USD: null, HKD: null })
const fxQuotes = ref<Partial<Record<ForeignCurrency, FxQuote>>>({})
/** 用户手动改过的汇率才提交，其余由后端按日期获取，避免把借用的汇率存成手动记录 */
const fxEdited = ref<Record<ForeignCurrency, boolean>>({ USD: false, HKD: false })
const fxLoading = ref(false)
type Flow = 'in' | 'out'
const events = ref<
  { category: string; description: string; absAmount: number | null; flow: Flow }[]
>([])
const loading = ref(false)
/** 新建时预填自上一次快照，用于对比变化；previous 为折合人民币的有效余额 */
const previous = ref<Record<string, number>>({})
const previousRaw = ref<Record<string, { balance: number; currency: CurrencyCode }>>({})
const previousDate = ref<string | null>(null)

/** 与后端 BalanceLogic 一致：负债按负值计入 */
function effective(type: string, v: number) {
  return type === 'credit' ? -Math.abs(v) : v
}

function currencyOf(a: Account): CurrencyCode {
  return currencies.value[a.id] ?? BASE_CURRENCY
}

function rateOf(c: CurrencyCode) {
  return c === BASE_CURRENCY ? 1 : (fxRates.value[c as ForeignCurrency] ?? 0)
}

/** 实时折算：原币余额 × 汇率，负债为负 */
function baseValue(a: Account) {
  return effective(a.type, (balances.value[a.id] ?? 0) * rateOf(currencyOf(a)))
}

/** 当前快照用到的外币 */
const usedForeign = computed(() =>
  FOREIGN_CURRENCIES.filter((c) => accounts.value.some((a) => currencyOf(a) === c)),
)
const currencyOptions = CURRENCIES.map((c) => ({ label: c.code, value: c.code }))

function fxHint(c: ForeignCurrency) {
  const q = fxQuotes.value[c]
  if (fxEdited.value[c]) return t('snapshots.form.fxHint.edited')
  if (fxLoading.value) return t('snapshots.form.fxHint.loading')
  if (!q || q.rate == null) return t('snapshots.form.fxHint.unavailable')
  if (q.fallback) return t('snapshots.form.fxHint.fallback', { date: q.rateDate })
  return t('snapshots.form.fxHint.source', { source: fxSourceText(q) })
}

let fxRequestSeq = 0

/** 按快照日期获取汇率；日期快速切换时丢弃过期的响应 */
async function loadFxRates() {
  const requested = date.value
  if (!requested) return
  const seq = ++fxRequestSeq
  fxLoading.value = true
  try {
    const rates = await fxApi.getFxRates(requested)
    if (seq !== fxRequestSeq) return
    fxQuotes.value = rates
    for (const c of FOREIGN_CURRENCIES) {
      fxRates.value[c] = rates[c]?.rate ?? null
      fxEdited.value[c] = false
    }
  } catch {
    if (seq === fxRequestSeq) {
      fxQuotes.value = {}
      message.warning(t('snapshots.form.errors.fxFetchFailed'))
    }
  } finally {
    if (seq === fxRequestSeq) {
      fxLoading.value = false
    }
  }
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
      subtotal: list.reduce((sum, a) => sum + baseValue(a), 0),
    }))
})

const filledCount = computed(() => accounts.value.filter((a) => balances.value[a.id] != null).length)
const netWorth = computed(() => accounts.value.reduce((sum, a) => sum + baseValue(a), 0))
const previousNetWorth = computed(() => accounts.value.reduce((sum, a) => sum + (previous.value[a.id] ?? 0), 0))
const hasPrevious = computed(() => Object.keys(previous.value).length > 0)
const eventNet = computed(() => events.value.reduce((sum, e) => sum + signedAmount(e), 0))

function accountDelta(a: Account) {
  const now = balances.value[a.id]
  const before = previous.value[a.id]
  if (now == null || before == null) {
    return null
  }
  // 按展示精度取整，避免汇率微小波动显示成 +¥0
  return Math.round(baseValue(a) - before)
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
    currencies.value[a.id] = BASE_CURRENCY
  }

  if (isEdit.value && idParam.value) {
    const s = await snapshotApi.getSnapshot(idParam.value)
    date.value = s.date
    note.value = s.note ?? ''
    for (const row of s.items) {
      balances.value[row.accountId] = row.balance
      currencies.value[row.accountId] = row.currency ?? BASE_CURRENCY
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
        const currency = row.currency ?? BASE_CURRENCY
        balances.value[row.accountId] = row.balance
        currencies.value[row.accountId] = currency
        previousRaw.value[row.accountId] = { balance: row.balance, currency }
        previous.value[row.accountId] = row.baseBalance ?? effective(row.type ?? '', row.balance)
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
    message.error(t('snapshots.form.errors.dateRequired'))
    return
  }
  const items = accounts.value.map((a) => ({
    accountId: a.id,
    balance: balances.value[a.id],
    currency: currencyOf(a),
  }))
  if (items.some((i) => i.balance === null || Number.isNaN(i.balance as number))) {
    message.error(t('snapshots.form.errors.balanceRequired'))
    return
  }
  const rates: snapshotApi.SnapshotFxRatesInput = {}
  for (const c of usedForeign.value) {
    const r = fxRates.value[c]
    if (r == null || !(r > 0)) {
      message.error(t('snapshots.form.errors.rateRequired', { currency: currencyLabel(c) }))
      return
    }
    if (fxEdited.value[c]) {
      rates[c] = r
    }
  }
  const incomplete = events.value.some((e) => {
    const hasText = Boolean(e.description?.trim())
    const hasAmount = e.absAmount != null && !Number.isNaN(e.absAmount) && e.absAmount > 0
    return (hasText && !hasAmount) || (hasAmount && !hasText)
  })
  if (incomplete) {
    message.error(t('snapshots.form.errors.eventIncomplete'))
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
        items: items.map((i) => ({ accountId: i.accountId, balance: i.balance as number, currency: i.currency })),
        events: evs,
        fxRates: rates,
      })
      message.success(t('common.status.saved'))
      await router.replace(`/snapshots/${idParam.value}`)
    } else {
      const s = await snapshotApi.createSnapshot({
        date: date.value,
        note: note.value || null,
        items: items.map((i) => ({ accountId: i.accountId, balance: i.balance as number, currency: i.currency })),
        events: evs,
        fxRates: rates,
      })
      message.success(t('common.status.created'))
      await router.replace(`/snapshots/${s.id}`)
    }
  } catch {
    message.error(t('snapshots.form.errors.saveFailed'))
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

onMounted(async () => {
  try {
    await load()
  } catch {
    message.error(t('common.status.loadFailed'))
    return
  }
  // 新建和编辑都按快照日期取汇率：该日已记录的汇率直接返回，没有则获取当天汇率
  await loadFxRates()
  watch(date, loadFxRates)
})
</script>

<template>
  <div class="page-stack snapshot-form">
    <PageHeader :title="isEdit ? t('snapshots.form.editTitle') : t('snapshots.newSnapshot')">
      <template #eyebrow>
        <button type="button" class="page-back" @click="goBack">{{ t('snapshots.form.back') }}</button>
      </template>
      <template #description>
        <template v-if="!isEdit && previousDate">{{ t('snapshots.form.prefilledHint', { date: previousDate }) }}</template>
        <template v-else>{{ t('snapshots.form.defaultHint') }}</template>
      </template>
    </PageHeader>

    <n-card class="surface-panel" :title="t('snapshots.form.basicInfo')">
      <div class="form-grid">
        <n-form-item :label="t('snapshots.form.date')" :show-feedback="false">
          <n-date-picker v-model:formatted-value="date" type="date" value-format="yyyy-MM-dd" style="width: 100%" />
        </n-form-item>
        <n-form-item :label="t('snapshots.form.note')" :show-feedback="false">
          <n-input v-model:value="note" :placeholder="t('snapshots.form.notePlaceholder')" />
        </n-form-item>
      </div>
      <div v-if="usedForeign.length" class="fx-rates">
        <div v-for="c in usedForeign" :key="c" class="fx-rate">
          <span class="fx-rate__label">1 {{ c }} =</span>
          <n-input-number
            v-model:value="fxRates[c]"
            :input-props="{ inputmode: 'decimal' }"
            class="fx-rate__input"
            @update:value="fxEdited[c] = true"
            :show-button="false"
            :min="0"
            :status="fxRates[c] == null ? 'warning' : undefined"
            :placeholder="t('snapshots.form.ratePlaceholder')"
          >
            <template #suffix>CNY</template>
          </n-input-number>
          <span class="cell-muted">{{ fxHint(c) }}</span>
        </div>
      </div>
    </n-card>

    <n-card class="surface-panel surface-panel--flush">
      <template #header>
        {{ t('snapshots.form.balances') }} <span class="section-note">{{ t('snapshots.form.filled', { filled: filledCount, total: accounts.length }) }}</span>
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
            <template v-if="previousRaw[a.id]">{{ t('snapshots.form.previous', { amount: formatMoney(previousRaw[a.id].balance, previousRaw[a.id].currency) }) }}</template>
          </span>
          <span class="balance-row__delta amount" :class="accountDelta(a) ? `amount--${amountTone(accountDelta(a) ?? 0)}` : 'amount--muted'">
            <template v-if="accountDelta(a) !== null">{{ accountDelta(a) ? formatSignedMoney(accountDelta(a) ?? 0) : t('snapshots.form.noChange') }}</template>
          </span>
          <div class="balance-row__input">
            <n-input-group>
              <n-select
                v-model:value="currencies[a.id]"
                class="balance-row__currency"
                :options="currencyOptions"
                :consistent-menu-width="false"
              />
              <n-input-number
                v-model:value="balances[a.id]"
                :input-props="{ inputmode: 'decimal' }"
                :show-button="false"
                :status="balances[a.id] == null ? 'warning' : undefined"
                placeholder="0.00"
              >
                <template #prefix>{{ currencySymbol(currencies[a.id] ?? 'CNY') }}</template>
              </n-input-number>
            </n-input-group>
            <span v-if="currencies[a.id] && currencies[a.id] !== 'CNY'" class="balance-row__base">
              ≈ {{ formatMoney(baseValue(a)) }}
            </span>
          </div>
        </div>
      </div>
    </n-card>

    <n-card class="surface-panel">
      <template #header>
        {{ t('snapshots.form.events') }} <span class="section-note">{{ t('snapshots.form.eventsNote') }}</span>
      </template>
      <div v-if="events.length" class="event-list">
        <div v-for="(ev, i) in events" :key="i" class="event-row">
          <n-select v-model:value="ev.category" :options="categorySelectOptions" />
          <n-input v-model:value="ev.description" :placeholder="t('snapshots.form.eventDescPlaceholder')" />
          <n-radio-group v-model:value="ev.flow">
            <n-radio-button value="out">{{ t('snapshots.flow.out') }}</n-radio-button>
            <n-radio-button value="in">{{ t('snapshots.flow.in') }}</n-radio-button>
          </n-radio-group>
          <n-input-number v-model:value="ev.absAmount" :input-props="{ inputmode: 'decimal' }" :placeholder="t('snapshots.form.amountPlaceholder')" :min="0" :show-button="false">
            <template #prefix>¥</template>
          </n-input-number>
          <n-button quaternary type="error" @click="removeEvent(i)">{{ t('snapshots.form.remove') }}</n-button>
        </div>
      </div>
      <n-button dashed block :style="events.length ? 'margin-top: 12px' : ''" @click="addEvent">{{ t('snapshots.form.addEvent') }}</n-button>
    </n-card>

    <div class="action-bar">
      <div class="action-bar__summary">
        <span>{{ t('snapshots.form.summary.netWorth') }} <strong>{{ formatMoney(netWorth) }}</strong></span>
        <span v-if="hasPrevious" :class="`amount--${amountTone(netWorth - previousNetWorth)}`">
          {{ t('snapshots.form.summary.vsLast') }} {{ formatSignedMoney(netWorth - previousNetWorth) }}
        </span>
        <span v-if="events.length">{{ t('snapshots.form.summary.events') }} {{ formatSignedMoney(eventNet) }}</span>
      </div>
      <div class="inline-control">
        <n-button @click="goBack">{{ t('common.actions.cancel') }}</n-button>
        <n-button type="primary" :loading="loading" @click="submit">{{ isEdit ? t('snapshots.form.saveChanges') : t('snapshots.form.saveSnapshot') }}</n-button>
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
  grid-template-columns: minmax(0, 1fr) 140px 110px 240px;
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

.balance-row__input {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.balance-row__input :deep(input) {
  text-align: right;
}

.balance-row__currency {
  width: 84px;
  flex: none;
}

.balance-row__base {
  color: var(--text-3);
  font-size: 12px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.fx-rates {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 32px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--line-soft);
}

.fx-rate {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
  font-size: 13px;
}

.fx-rate__label {
  color: var(--text-2);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.fx-rate__input {
  width: 150px;
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
    grid-template-columns: minmax(0, 1fr) 200px;
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

@media (max-width: 560px) {
  .balance-row {
    grid-template-columns: minmax(0, 1fr) 176px;
    gap: 4px 12px;
  }

  .balance-row__currency {
    width: 72px;
  }

  /* 币种已在左侧下拉框中显示，窄屏上省掉金额前的符号，给 HK$ 等较长币种的数字留出空间 */
  .balance-row__input :deep(.n-input__prefix) {
    display: none;
  }

  .fx-rate__input {
    width: 100%;
  }
}
</style>
