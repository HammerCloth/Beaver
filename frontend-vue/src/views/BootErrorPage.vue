<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { t } from '@/i18n'

const router = useRouter()
const auth = useAuthStore()

async function retry() {
  await auth.retryBootstrap()
  if (!auth.bootError) {
    if (auth.needsSetup) {
      await router.replace('/setup')
    } else if (auth.isLoggedIn) {
      await router.replace('/dashboard')
    } else {
      await router.replace('/login')
    }
  }
}
</script>

<template>
  <div class="screen-shell">
    <div class="screen-card">
      <n-result status="error" :title="t('auth.bootError.title')">
        <template #footer>
          <div class="page-stack" style="text-align: left">
            <p class="page-header__desc">{{ auth.bootError }}</p>
            <n-text depth="3">
              {{ t('auth.bootError.startBackend') }}
              <code>backend</code> {{ t('auth.bootError.runIn') }}
            </n-text>
            <n-code style="display: block; padding: 12px">./mvnw spring-boot:run</n-code>
            <n-text depth="3">
              {{ t('auth.bootError.orJarPrefix') }} <code>backend/data</code> {{ t('auth.bootError.orJarSuffix') }}
            </n-text>
            <n-button type="primary" @click="retry">{{ t('auth.bootError.retry') }}</n-button>
          </div>
        </template>
      </n-result>
    </div>
  </div>
</template>
