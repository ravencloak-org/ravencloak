<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import Button from 'primevue/button'
import Card from 'primevue/card'

defineOptions({
  name: 'LoginPage'
})

const authStore = useAuthStore()

async function handleLogin() {
  await authStore.login()
}
</script>

<template>
  <div class="flex justify-center items-center min-h-screen bg-surface-100 dark:bg-surface-900">
    <Card class="w-full max-w-md">
      <template #title>
        <div class="text-center">
          <h1 class="text-2xl font-bold">KOS Auth Admin</h1>
        </div>
      </template>
      <template #content>
        <p class="text-center text-surface-600 dark:text-surface-400 mb-6">
          Sign in with your Keycloak account to manage realms
        </p>
        <Button
          label="Sign in with Keycloak"
          icon="pi pi-sign-in"
          class="w-full"
          @click="handleLogin"
          :loading="authStore.loading"
        />
      </template>
    </Card>
  </div>
</template>

<route lang="yaml">
meta:
  requiresAuth: false
</route>
