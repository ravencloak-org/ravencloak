<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

defineOptions({
  name: 'LoginPage',
})

const authStore = useAuthStore()

async function handleLogin() {
  await authStore.login()
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-zinc-950 px-4">
    <div
      class="w-full max-w-sm rounded-xl bg-zinc-900 p-8 ring-1 ring-zinc-800 shadow-2xl"
    >
      <!-- Logo -->
      <div class="text-center">
        <h1 class="text-3xl font-bold text-white tracking-tight">Ravencloak</h1>
        <p class="mt-2 text-sm text-zinc-400">
          Multi-tenant authentication admin
        </p>
      </div>

      <!-- Error -->
      <div
        v-if="authStore.error"
        class="mt-6 rounded-lg bg-red-500/10 px-4 py-3 text-sm text-red-400 ring-1 ring-inset ring-red-500/20"
      >
        {{ authStore.error }}
      </div>

      <!-- Sign in button -->
      <div class="mt-8">
        <button
          type="button"
          :disabled="authStore.loading"
          class="flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-indigo-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-50 disabled:cursor-not-allowed"
          @click="handleLogin"
        >
          <svg
            v-if="authStore.loading"
            class="h-4 w-4 animate-spin"
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
          >
            <circle
              class="opacity-25"
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              stroke-width="4"
            />
            <path
              class="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
            />
          </svg>
          Sign in with Keycloak
        </button>
      </div>

      <!-- Footer -->
      <p class="mt-6 text-center text-xs text-zinc-500">
        Secured by Keycloak OpenID Connect
      </p>
    </div>
  </div>
</template>

<route lang="yaml">
meta:
  requiresAuth: false
</route>
