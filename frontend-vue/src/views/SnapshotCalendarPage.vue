<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import * as snapshotApi from '@/api/snapshot'
import { formatMoney } from '@/lib/format'
import PageHeader from '@/components/PageHeader.vue'
import SnapshotViewSwitch from '@/components/SnapshotViewSwitch.vue'

const router = useRouter()
const message = useMessage()

/** 始终用每月 1 号，避免 31 号时 setMonth(±1) 溢出跳月 */
function firstOfMonth(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), 1)
}

const viewMonth = ref(firstOfMonth(new Date()))

const year = computed(() => viewMonth.value.getFullYear())
const month = computed(() => viewMonth.value.getMonth())

const snapshotDates = ref<Set<string>>(new Set())
/** 日期 → 净资产，用于在格子里显示金额 */
const netWorthByDate = ref<Record<string, number>>({})
const now = new Date()
const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`

snapshotApi
  .listSnapshots()
  .then((list) => {
    netWorthByDate.value = Object.fromEntries(list.map((x) => [x.date, x.netWorth]))
  })
  .catch(() => {})

const monthCount = computed(() => snapshotDates.value.size)

/** 手机端格子放不下金额，在日历下方按日期倒序列出本月快照 */
const monthSnapshots = computed(() =>
  [...snapshotDates.value].sort((a, b) => b.localeCompare(a)).map((date) => ({ date, netWorth: netWorthByDate.value[date] })),
)

function compactMoney(v: number) {
  return Math.abs(v) >= 10000 ? `¥${(v / 10000).toFixed(2)}万` : formatMoney(v)
}

function goToday() {
  viewMonth.value = firstOfMonth(new Date())
}

function ymd(y: number, m: number, d: number) {
  return `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`
}

async function loadMonth() {
  const y = year.value
  const m = month.value
  const from = ymd(y, m, 1)
  const lastDate = new Date(y, m + 1, 0).getDate()
  const to = ymd(y, m, lastDate)
  const dates = await snapshotApi.snapshotDatesInRange(from, to)
  snapshotDates.value = new Set(dates)
}

const cells = computed(() => {
  const y = year.value
  const m = month.value
  const firstDow = new Date(y, m, 1).getDay()
  const lastDate = new Date(y, m + 1, 0).getDate()
  const out: ({ dateStr: string; day: number } | null)[] = []
  for (let i = 0; i < firstDow; i++) {
    out.push(null)
  }
  for (let d = 1; d <= lastDate; d++) {
    out.push({ dateStr: ymd(y, m, d), day: d })
  }
  while (out.length % 7 !== 0) {
    out.push(null)
  }
  return out
})

const weekDays = ['日', '一', '二', '三', '四', '五', '六']

async function onPick(dateStr: string) {
  try {
    const snap = await snapshotApi.snapshotForDate(dateStr)
    if (snap) {
      await router.push(`/snapshots/${snap.id}`)
    } else {
      await router.push({ path: '/snapshots/new', query: { date: dateStr } })
    }
  } catch {
    message.error('加载失败')
  }
}

function prevMonth() {
  const d = new Date(viewMonth.value)
  d.setMonth(d.getMonth() - 1)
  viewMonth.value = d
}

function nextMonth() {
  const d = new Date(viewMonth.value)
  d.setMonth(d.getMonth() + 1)
  viewMonth.value = d
}

watch(
  viewMonth,
  () => {
    loadMonth().catch(() => message.error('加载日历失败'))
  },
  { immediate: true },
)
</script>

<template>
  <div class="page-stack">
    <PageHeader title="快照" description="点击已记录的日期查看详情，点击空白日期直接新建当天快照。">
      <SnapshotViewSwitch current="calendar" />
      <n-button type="primary" @click="router.push('/snapshots/new')">记录快照</n-button>
    </PageHeader>

    <n-card class="surface-panel">
      <template #header>
        <div class="cal-toolbar">
          <n-button size="small" quaternary @click="prevMonth">‹</n-button>
          <strong class="calendar-label">{{ year }} 年 {{ month + 1 }} 月</strong>
          <n-button size="small" quaternary @click="nextMonth">›</n-button>
          <n-button size="small" @click="goToday">今天</n-button>
        </div>
      </template>
      <template #header-extra>
        <span class="section-note">本月 {{ monthCount }} 次快照</span>
      </template>
      <div class="cal-grid">
        <div v-for="w in weekDays" :key="w" class="cal-head">{{ w }}</div>
        <template v-for="(c, i) in cells" :key="i">
          <div v-if="!c" class="cal-cell cal-empty" />
          <button
            v-else
            type="button"
            class="cal-cell cal-day"
            :class="{
              'has-snap': snapshotDates.has(c.dateStr),
              'is-today': c.dateStr === todayStr,
              'is-future': c.dateStr > todayStr,
            }"
            @click="onPick(c.dateStr)"
          >
            <span class="day-num">{{ c.day }}</span>
            <span v-if="snapshotDates.has(c.dateStr)" class="day-value">
              {{ netWorthByDate[c.dateStr] != null ? compactMoney(netWorthByDate[c.dateStr]) : '已记录' }}
            </span>
            <span v-else class="day-add">+ 新建</span>
          </button>
        </template>
      </div>
      <div class="cal-month-list">
        <div class="cal-month-list__title">本月快照</div>
        <button v-for="s in monthSnapshots" :key="s.date" type="button" class="cal-month-list__item" @click="onPick(s.date)">
          <span class="cell-main">{{ s.date }}</span>
          <span class="amount">{{ s.netWorth != null ? formatMoney(s.netWorth) : '已记录' }}</span>
        </button>
        <p v-if="!monthSnapshots.length" class="cal-month-list__empty">本月还没有快照，点击日期即可新建</p>
      </div>
    </n-card>
  </div>
</template>

<style scoped>
.cal-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
}

.calendar-label {
  min-width: 108px;
  font-size: 15px;
  font-weight: 600;
  text-align: center;
}

.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: var(--line-soft);
  gap: 1px;
}

.cal-head {
  padding: 8px;
  background: var(--surface-0);
  color: var(--text-3);
  font-size: 12px;
  font-weight: 500;
  text-align: center;
}

.cal-cell {
  min-height: 88px;
  background: var(--surface-1);
}

.cal-empty {
  background: var(--surface-0);
}

.cal-day {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: space-between;
  width: 100%;
  padding: 10px;
  border: 0;
  color: var(--text-1);
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.cal-day:hover {
  background: var(--surface-hover);
}

.day-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.cal-day.is-today .day-num {
  background: var(--text-1);
  color: #ffffff;
}

.cal-day.is-future {
  color: var(--text-3);
}

.day-value {
  align-self: stretch;
  padding: 3px 6px;
  border-radius: 6px;
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.day-add {
  color: var(--text-3);
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.cal-day:hover .day-add {
  opacity: 1;
}

.cal-month-list {
  display: none;
}

@media (max-width: 640px) {
  .cal-cell {
    min-height: 44px;
  }

  .cal-day {
    align-items: center;
    justify-content: center;
    padding: 0;
  }

  .day-num {
    min-width: 30px;
    height: 30px;
  }

  /* 金额放到下方列表，格子里只用高亮日期表示已记录 */
  .day-value,
  .day-add {
    display: none;
  }

  .cal-day.has-snap .day-num {
    background: var(--accent-soft);
    color: var(--accent-strong);
    font-weight: 700;
  }

  .cal-day.is-today.has-snap .day-num {
    background: var(--text-1);
    color: #ffffff;
    box-shadow: 0 0 0 2px var(--surface-1), 0 0 0 4px var(--accent);
  }

  .cal-month-list {
    display: flex;
    flex-direction: column;
    margin-top: 16px;
  }

  .cal-month-list__title {
    padding-bottom: 6px;
    color: var(--text-3);
    font-size: 12px;
    font-weight: 500;
  }

  .cal-month-list__item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 12px 0;
    border: 0;
    border-top: 1px solid var(--line-soft);
    background: none;
    color: var(--text-1);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .cal-month-list__empty {
    margin: 0;
    padding: 12px 0;
    border-top: 1px solid var(--line-soft);
    color: var(--text-3);
    font-size: 13px;
  }
}
</style>
