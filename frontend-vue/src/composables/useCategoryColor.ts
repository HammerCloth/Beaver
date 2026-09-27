import { chartPalette } from '@/lib/chartTheme'
import { useSettingsStore } from '@/stores/settings'

/** 类别 → 固定颜色：按设置中的排序分配，保证同一类别在所有页面、所有图表里颜色一致 */
export function useCategoryColor() {
  const settings = useSettingsStore()

  function categoryColor(dim: string, key: string) {
    const opts = [...((settings.options?.[dim as keyof typeof settings.options] ?? []) as { key: string; sortOrder: number }[])]
      .sort((a, b) => a.sortOrder - b.sortOrder)
    const idx = opts.findIndex((o) => o.key === key)
    if (idx >= 0) {
      return chartPalette[idx % chartPalette.length]
    }
    let hash = 0
    for (const ch of key) {
      hash = (hash * 31 + ch.charCodeAt(0)) >>> 0
    }
    return chartPalette[hash % chartPalette.length]
  }

  return { categoryColor }
}
