<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { t } from '@/i18n'

const router = useRouter()
const message = useMessage()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await auth.setup(username.value.trim(), password.value)
    message.success(t('auth.setup.success'))
    await router.replace('/dashboard')
  } catch (e: unknown) {
    message.error((e as { response?: { data?: { error?: string } } })?.response?.data?.error ?? t('auth.setup.failed'))
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
          <span class="login-hero__badge">{{ t('auth.setup.badge') }}</span>
          <h1 class="page-header__title" style="margin-top: 18px">{{ t('auth.setup.title') }}</h1>
          <p class="page-header__desc" style="margin-top: 8px">
            {{ t('auth.setup.desc') }}
          </p>
        </div>
        <n-form @submit.prevent="submit">
          <n-form-item :label="t('auth.setup.username')">
            <n-input v-model:value="username" :placeholder="t('auth.setup.usernamePlaceholder')" />
          </n-form-item>
          <n-form-item :label="t('auth.setup.password')">
            <n-input v-model:value="password" type="password" show-password-on="click" />
          </n-form-item>
          <n-button type="primary" block :loading="loading" attr-type="submit">{{ t('auth.setup.submit') }}</n-button>
        </n-form>
      </div>
    </div>
  </div>
</template>
