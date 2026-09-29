<script setup lang="ts">
import BrandMark from '@/components/BrandMark.vue'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { AxiosError } from 'axios'
import { useAuthStore } from '@/stores/auth'
import { currentLocale, switchLocale, t } from '@/i18n'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const remember = ref(true)
const loading = ref(false)

// 按钮显示另一种语言的名称，点击后切换（会整页刷新）
const otherLocale = currentLocale() === 'zh-CN' ? 'en-US' : 'zh-CN'
const otherLocaleName = otherLocale === 'zh-CN' ? '中文' : 'English'

function safeRedirect() {
  const raw = route.query.redirect
  const redirect = typeof raw === 'string' ? raw : '/dashboard'
  if (!redirect.startsWith('/') || redirect.startsWith('//')) {
    return '/dashboard'
  }
  return redirect
}

async function submit() {
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value, remember.value)
  } catch (err) {
    const error = err as AxiosError<{ error?: string }>
    message.error(error.response?.data?.error || t('auth.login.failed'))
    loading.value = false
    return
  }

  try {
    message.success(t('auth.login.success'))
    const redirect = safeRedirect()
    await router.replace(redirect)
  } catch {
    const redirect = safeRedirect()
    window.location.href = redirect
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-shell">
    <div class="login-wrap">
      <section class="login-hero">
        <span class="login-hero__badge">{{ t('auth.login.hero.badge') }}</span>
        <h1 class="login-hero__title">{{ t('auth.login.hero.title') }}</h1>
        <p class="login-hero__desc">
          {{ t('auth.login.hero.desc') }}
        </p>
        <div class="login-hero__highlights">
          <div class="login-hero__tile">
            <strong>{{ t('auth.login.hero.highlights.netWorth.title') }}</strong>
            <span>{{ t('auth.login.hero.highlights.netWorth.desc') }}</span>
          </div>
          <div class="login-hero__tile">
            <strong>{{ t('auth.login.hero.highlights.accounts.title') }}</strong>
            <span>{{ t('auth.login.hero.highlights.accounts.desc') }}</span>
          </div>
          <div class="login-hero__tile">
            <strong>{{ t('auth.login.hero.highlights.mobile.title') }}</strong>
            <span>{{ t('auth.login.hero.highlights.mobile.desc') }}</span>
          </div>
        </div>
      </section>

      <section class="login-card">
        <n-button
          class="login-card__lang"
          quaternary
          size="small"
          :title="t('auth.login.switchLanguage')"
          @click="switchLocale(otherLocale)"
        >
          {{ otherLocaleName }}
        </n-button>
        <div class="login-card__brand">
          <BrandMark :size="36" />
          <div class="login-card__brand-text">
            <strong>Beaver</strong>
            <span class="section-note">{{ t('common.appTagline') }}</span>
          </div>
        </div>
        <h2 class="login-card__title">{{ t('auth.login.title') }}</h2>
        <p class="login-card__desc">{{ t('auth.login.desc') }}</p>
        <n-form @submit.prevent="submit">
          <n-form-item :label="t('auth.login.username')">
            <n-input v-model:value="username" :placeholder="t('auth.login.usernamePlaceholder')" />
          </n-form-item>
          <n-form-item :label="t('auth.login.password')">
            <n-input
              v-model:value="password"
              type="password"
              show-password-on="click"
              :placeholder="t('auth.login.passwordPlaceholder')"
            />
          </n-form-item>
          <div class="login-card__actions">
            <span class="section-note">{{ t('auth.login.rememberHint') }}</span>
            <n-switch v-model:value="remember">
              <template #checked>{{ t('auth.login.rememberMe') }}</template>
              <template #unchecked>{{ t('auth.login.rememberMe') }}</template>
            </n-switch>
          </div>
          <n-button type="primary" block :loading="loading" attr-type="submit">{{ t('auth.login.submit') }}</n-button>
        </n-form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-card {
  position: relative;
}

.login-card__lang {
  position: absolute;
  top: 12px;
  right: 12px;
  color: var(--text-3);
}
</style>
