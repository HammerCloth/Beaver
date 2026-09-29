import { onBeforeUnmount, onMounted, ref } from 'vue'

/** 与 style.css 中移动端断点保持一致 */
const MOBILE_QUERY = '(max-width: 820px)'

/** 窄屏（手机）判断：表格在手机上切换为卡片列表等场景使用 */
export function useIsMobile() {
  const mql = typeof window !== 'undefined' ? window.matchMedia(MOBILE_QUERY) : null
  const isMobile = ref(mql?.matches ?? false)

  function onChange(e: MediaQueryListEvent) {
    isMobile.value = e.matches
  }

  onMounted(() => mql?.addEventListener('change', onChange))
  onBeforeUnmount(() => mql?.removeEventListener('change', onChange))

  return { isMobile }
}
