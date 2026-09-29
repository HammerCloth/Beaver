<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuOption } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { useSettingsStore } from '@/stores/settings'
import { navIcons } from '@/components/navIcons'
import BrandMark from '@/components/BrandMark.vue'
import { currentLocale, switchLocale, t } from '@/i18n'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const settings = useSettingsStore()
const collapsed = ref(false)
const mobileOpen = ref(false)
const mobile = ref(typeof window !== 'undefined' && window.innerWidth < 768)

function onResize() {
  mobile.value = window.innerWidth < 768
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', onResize)
  }
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', onResize)
  }
})

watch(
  () => auth.isLoggedIn,
  (v) => {
    if (v) {
      settings.load().catch(() => {})
    }
  },
  { immediate: true },
)

function item(label: string, key: string): MenuOption {
  return { label, key, icon: navIcons[key] }
}

const menuOptions = computed<MenuOption[]>(() => {
  const system: MenuOption[] = [item(t('nav.aiClients'), '/ai-clients'), item(t('nav.settings'), '/settings')]
  if (auth.isAdmin) {
    system.push(item(t('nav.users'), '/users'))
  }
  return [
    {
      type: 'group',
      label: t('nav.groups.assets'),
      key: 'g-assets',
      children: [item(t('nav.dashboard'), '/dashboard'), item(t('nav.snapshots'), '/snapshots'), item(t('nav.accounts'), '/accounts')],
    },
    {
      type: 'group',
      label: t('nav.groups.records'),
      key: 'g-records',
      children: [item(t('nav.events'), '/events'), item(t('nav.gifts'), '/gifts'), item(t('nav.loans'), '/loans')],
    },
    { type: 'group', label: t('nav.groups.system'), key: 'g-system', children: system },
  ]
})

/** 用户菜单：切换语言（显示另一种语言的名称）+ 退出登录 */
const userMenuOptions = [
  { label: t('nav.switchLanguage'), key: 'language' },
  { type: 'divider', key: 'd1' },
  { label: t('common.actions.logout'), key: 'logout' },
]

function onUserMenu(key: string) {
  if (key === 'logout') {
    onLogout()
  } else if (key === 'language') {
    switchLocale(currentLocale() === 'zh-CN' ? 'en-US' : 'zh-CN')
  }
}

const activeKey = computed(() => {
  const p = route.path
  if (p.startsWith('/snapshots')) {
    return '/snapshots'
  }
  if (p.startsWith('/settings')) {
    return '/settings'
  }
  if (p.startsWith('/ai-clients')) {
    return '/ai-clients'
  }
  if (p.startsWith('/gifts')) {
    return '/gifts'
  }
  if (p.startsWith('/loans')) {
    return '/loans'
  }
  return p
})

const pageTitle = computed(() => {
  switch (activeKey.value) {
    case '/dashboard':
      return t('nav.dashboard')
    case '/snapshots':
      return t('nav.snapshots')
    case '/accounts':
      return t('nav.accounts')
    case '/events':
      return t('nav.events')
    case '/gifts':
      return t('nav.gifts')
    case '/loans':
      return t('nav.loans')
    case '/ai-clients':
      return t('nav.aiClients')
    case '/settings':
      return t('nav.settings')
    case '/users':
      return t('nav.users')
    default:
      return 'Beaver'
  }
})

watch(
  pageTitle,
  (title) => {
    document.title = title === 'Beaver' ? title : `${title} · Beaver`
  },
  { immediate: true },
)

const userInitial = computed(() => (auth.user?.username?.slice(0, 1) || 'Z').toUpperCase())

function onMenuSelect(key: string) {
  router.push(key)
  mobileOpen.value = false
}

async function onLogout() {
  await auth.logout()
  await router.push('/login')
}
</script>

<template>
  <n-layout class="app-shell" has-sider position="absolute">
    <n-layout-sider
      v-if="!mobile"
      bordered
      collapse-mode="width"
      v-model:collapsed="collapsed"
      :collapsed-width="64"
      :width="232"
      show-trigger="bar"
    >
      <div class="app-sidebar" :class="{ 'is-collapsed': collapsed }">
        <div class="app-brand">
          <BrandMark />
          <div v-if="!collapsed" class="app-brand__text">
            <span class="app-brand__title">Beaver</span>
            <span class="app-brand__subtitle">{{ t('common.appTagline') }}</span>
          </div>
        </div>
        <n-menu
          class="app-nav"
          :collapsed="collapsed"
          :collapsed-width="64"
          :collapsed-icon-size="20"
          :icon-size="18"
          :indent="16"
          :value="activeKey"
          :options="menuOptions"
          @update:value="onMenuSelect"
        />
        <n-dropdown trigger="click" placement="top-start" :options="userMenuOptions" @select="onUserMenu">
          <button type="button" class="app-account">
            <span class="app-user__avatar">{{ userInitial }}</span>
            <span v-if="!collapsed" class="app-account__meta">
              <span class="app-account__name">{{ auth.user?.username || t('nav.notLoggedIn') }}</span>
              <span class="app-account__role">{{ auth.isAdmin ? t('common.role.admin') : t('common.role.member') }}</span>
            </span>
            <svg v-if="!collapsed" class="app-account__chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m7 15 5 5 5-5" /><path d="m7 9 5-5 5 5" /></svg>
          </button>
        </n-dropdown>
      </div>
    </n-layout-sider>

    <n-layout class="app-main" :native-scrollbar="false">
      <header v-if="mobile" class="app-topbar">
        <n-button size="small" quaternary @click="mobileOpen = true">
          <template #icon>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
          </template>
        </n-button>
        <span class="app-topbar__title">{{ pageTitle }}</span>
        <n-dropdown trigger="click" placement="bottom-end" :options="userMenuOptions" @select="onUserMenu">
          <button type="button" class="app-user__avatar app-user__avatar--button">{{ userInitial }}</button>
        </n-dropdown>
      </header>
      <main class="app-content">
        <router-view />
      </main>
    </n-layout>
  </n-layout>

  <n-drawer v-model:show="mobileOpen" placement="left" :width="264">
    <div class="app-sidebar">
      <div class="app-brand">
        <BrandMark />
        <div class="app-brand__text">
          <span class="app-brand__title">Beaver</span>
          <span class="app-brand__subtitle">{{ t('common.appTagline') }}</span>
        </div>
      </div>
      <n-menu
        class="app-nav"
        :icon-size="18"
        :indent="16"
        :value="activeKey"
        :options="menuOptions"
        @update:value="onMenuSelect"
      />
    </div>
  </n-drawer>
</template>
