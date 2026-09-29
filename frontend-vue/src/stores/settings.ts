import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as settingsApi from '@/api/settings'
import type { OptionItem } from '@/api/settings'
import { currentLocale, t, te } from '@/i18n'
import zhOptions from '@/i18n/locales/zh-CN/options'

export const DIM_ACCOUNT_TYPE = 'account_type'
export const DIM_ACCOUNT_OWNER = 'account_owner'
export const DIM_EVENT_CATEGORY = 'event_category'

type OptionDefaults = Record<string, Record<string, string>>

/**
 * 选项名称是用户数据：仍为内置中文默认名时按界面语言翻译，用户改过的名称原样显示。
 */
function displayLabel(dim: string, key: string, label: string) {
  if (currentLocale() === 'zh-CN') {
    return label
  }
  const builtin = (zhOptions as OptionDefaults)[dim]?.[key]
  const path = `options.${dim}.${key}`
  return builtin === label && te(path) ? t(path) : label
}

export const useSettingsStore = defineStore('settings', () => {
  const options = ref<settingsApi.OptionsResponse | null>(null)
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      options.value = await settingsApi.fetchOptions()
    } finally {
      loading.value = false
    }
  }

  function label(dim: string, key: string) {
    const rows = options.value?.[dim as keyof settingsApi.OptionsResponse] ?? []
    const row = rows.find((x) => x.key === key)
    return row ? displayLabel(dim, key, row.label) : key
  }

  function selectOptions(dim: string): { label: string; value: string }[] {
    const rows = (options.value?.[dim as keyof settingsApi.OptionsResponse] ?? []) as OptionItem[]
    return [...rows]
      .filter((x) => x.enabled)
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map((x) => ({ label: displayLabel(dim, x.key, x.label), value: x.key }))
  }

  async function saveDimension(dim: string, items: OptionItem[]) {
    await settingsApi.putDimension(dim, items)
    await load()
  }

  async function reset() {
    await settingsApi.resetOptions()
    await load()
  }

  return { options, loading, load, label, selectOptions, saveDimension, reset }
})
