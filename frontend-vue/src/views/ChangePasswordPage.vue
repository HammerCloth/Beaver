<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import * as authApi from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { t } from '@/i18n'

const router = useRouter()
const message = useMessage()
const auth = useAuthStore()

const current = ref('')
const next = ref('')
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await authApi.changePassword(current.value, next.value)
    message.success(t('auth.changePassword.success'))
    await auth.refreshUser()
    await router.replace('/dashboard')
  } catch {
    message.error(t('auth.changePassword.failed'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="screen-shell">
    <div class="screen-card">
      <div class="page-stack">
        <div>
          <span class="login-hero__badge">{{ t('auth.changePassword.badge') }}</span>
          <h1 class="page-header__title" style="margin-top: 18px">{{ t('auth.changePassword.title') }}</h1>
          <p class="page-header__desc" style="margin-top: 8px">
            {{ t('auth.changePassword.desc') }}
          </p>
        </div>
        <n-alert type="info">{{ t('auth.changePassword.alert') }}</n-alert>
        <n-form @submit.prevent="submit">
          <n-form-item :label="t('auth.changePassword.current')">
            <n-input v-model:value="current" type="password" show-password-on="click" />
          </n-form-item>
          <n-form-item :label="t('auth.changePassword.next')">
            <n-input v-model:value="next" type="password" show-password-on="click" />
          </n-form-item>
          <n-button type="primary" block :loading="loading" attr-type="submit">{{ t('auth.changePassword.submit') }}</n-button>
        </n-form>
      </div>
    </div>
  </div>
</template>
