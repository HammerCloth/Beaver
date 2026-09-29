<script setup lang="ts">
import { useRouter } from 'vue-router'
import { t } from '@/i18n'

/** 快照的两种视图：日历（默认）与列表，放在两个页面的标题栏里互相切换 */
const props = defineProps<{ current: 'calendar' | 'list' }>()
const router = useRouter()

function onChange(value: string) {
  if (value !== props.current) {
    router.push(value === 'calendar' ? '/snapshots' : '/snapshots/list')
  }
}
</script>

<template>
  <n-tabs :value="current" type="segment" size="small" class="view-switch" @update:value="onChange">
    <n-tab name="calendar">{{ t('snapshots.viewSwitch.calendar') }}</n-tab>
    <n-tab name="list">{{ t('snapshots.viewSwitch.list') }}</n-tab>
  </n-tabs>
</template>

<style scoped>
.view-switch {
  width: 132px;
}
</style>
