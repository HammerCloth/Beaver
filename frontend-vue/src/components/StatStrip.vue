<script setup lang="ts">
export interface StatItem {
  label: string
  value: string | number
  /** 数值后的单位，例如「笔」 */
  unit?: string
  hint?: string
  tone?: 'positive' | 'negative' | 'warning' | ''
}

defineProps<{ items: StatItem[] }>()
</script>

<template>
  <section class="stat-strip" :style="{ '--stat-cols': items.length }">
    <div v-for="item in items" :key="item.label" class="stat-strip__cell">
      <div class="stat-strip__label">{{ item.label }}</div>
      <div class="stat-strip__value" :class="item.tone && `is-${item.tone}`">
        {{ item.value }}<span v-if="item.unit" class="stat-strip__unit">{{ item.unit }}</span>
      </div>
      <div v-if="item.hint" class="stat-strip__hint">{{ item.hint }}</div>
    </div>
  </section>
</template>
