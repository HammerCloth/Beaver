<script setup lang="ts">
import logoUrl from '@/assets/logo.svg'

withDefaults(defineProps<{ size?: number }>(), { size: 32 })
</script>

<template>
  <span class="app-brand__mark" :style="{ width: `${size}px`, height: `${size}px` }">
    <img :src="logoUrl" alt="Beaver" />
  </span>
</template>
