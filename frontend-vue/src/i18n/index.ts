import { createI18n } from 'vue-i18n'

export const LOCALES = ['zh-CN', 'en-US'] as const
export type AppLocale = (typeof LOCALES)[number]

const STORAGE_KEY = 'beaver.locale'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Messages = Record<string, any>

/**
 * 词条按命名空间拆成独立文件：locales/<locale>/<namespace>.ts，默认导出一个对象，
 * 通过 t('<namespace>.<key>') 访问。新增页面时两种语言各加一个同名文件即可。
 */
function loadMessages(): Record<AppLocale, Messages> {
  const modules = import.meta.glob<{ default: Messages }>('./locales/*/*.ts', { eager: true })
  const out = Object.fromEntries(LOCALES.map((l) => [l, {} as Messages])) as Record<AppLocale, Messages>
  for (const [path, mod] of Object.entries(modules)) {
    const match = path.match(/\.\/locales\/([^/]+)\/([^/]+)\.ts$/)
    if (!match) {
      continue
    }
    const [, locale, ns] = match
    if ((LOCALES as readonly string[]).includes(locale)) {
      out[locale as AppLocale][ns] = mod.default
    }
  }
  return out
}

function readStored(): AppLocale | null {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    return v && (LOCALES as readonly string[]).includes(v) ? (v as AppLocale) : null
  } catch {
    return null
  }
}

/** 用户手动选择过则用选择的；否则中文系统用中文，其余用英文 */
function detectLocale(): AppLocale {
  const stored = readStored()
  if (stored) {
    return stored
  }
  const langs = typeof navigator === 'undefined' ? [] : navigator.languages?.length ? navigator.languages : [navigator.language]
  return langs.some((l) => l?.toLowerCase().startsWith('zh')) ? 'zh-CN' : 'en-US'
}

const initial = detectLocale()
if (typeof document !== 'undefined') {
  document.documentElement.lang = initial
  // index.html 写死中文标题；进入应用后由布局按页面覆盖，这里只处理登录等页面
  if (initial === 'en-US') {
    document.title = 'Beaver · Personal wealth dashboard'
  }
}

export const i18n = createI18n({
  legacy: false,
  locale: initial,
  fallbackLocale: 'zh-CN',
  messages: loadMessages(),
  missingWarn: import.meta.env.DEV,
  fallbackWarn: false,
})

/** 切换语言会整页刷新，因此运行期间语言固定不变 */
export function currentLocale(): AppLocale {
  return initial
}

type TranslateFn = (key: string, named?: Record<string, unknown>) => string

/** 全局翻译函数，供非组件模块（lib、api、store）使用；词条无类型约束，避免深层类型推导 */
const composer = i18n.global as unknown as {
  t: (key: string, named?: Record<string, unknown>, plural?: number) => string
  te: (key: string) => boolean
}

/** 参数里有数字 n 时按它选择单复数（英文词条写成 '{n} record | {n} records'，中文只写一种） */
export const t: TranslateFn = (key, named) => {
  if (!named) {
    return composer.t(key)
  }
  return typeof named.n === 'number' ? composer.t(key, named, named.n) : composer.t(key, named)
}

/** 词条是否存在 */
export const te = (key: string) => composer.te(key)

/**
 * 切换语言后整页刷新：表格列、图表配置等在页面创建时生成，刷新比逐个做成响应式更可靠。
 */
export function switchLocale(locale: AppLocale) {
  if (locale === currentLocale()) {
    return
  }
  try {
    localStorage.setItem(STORAGE_KEY, locale)
  } catch {
    // 无法持久化时仍在本次会话内生效
  }
  window.location.reload()
}
