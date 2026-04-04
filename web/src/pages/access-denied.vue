<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { ShieldExclamationIcon } from '@heroicons/vue/24/outline'

defineOptions({
  name: 'AccessDeniedPage',
})

const authStore = useAuthStore()

async function handleLogout() {
  await authStore.logout()
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-zinc-950 px-4">
    <div
      class="w-full max-w-md rounded-xl bg-zinc-900 p-8 text-center ring-1 ring-zinc-800 shadow-2xl"
    >
      <!-- Shield icon -->
      <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-red-500/10 ring-1 ring-red-500/20">
        <ShieldExclamationIcon class="h-8 w-8 text-red-400" />
      </div>

      <!-- Heading -->
      <h1 class="mt-6 text-2xl font-semibold text-white">Access Denied</h1>

      <!-- Message -->
      <p class="mt-3 text-sm leading-6 text-zinc-400">
        You need the <span class="font-medium text-zinc-300">SUPER_ADMIN</span> role to access this app.
      </p>

      <!-- Signed-in user info -->
      <div
        v-if="authStore.user?.email"
        class="mt-4 rounded-lg bg-zinc-800/50 px-4 py-2.5 text-sm text-zinc-400"
      >
        Signed in as
        <span class="font-medium text-zinc-300">{{ authStore.user.email }}</span>
      </div>

      <!-- Sign out button -->
      <div class="mt-6">
        <button
          type="button"
          class="inline-flex items-center justify-center gap-2 rounded-lg bg-white px-4 py-2 text-sm font-semibold text-zinc-900 shadow-sm transition-colors hover:bg-zinc-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
          @click="handleLogout"
        >
          Sign Out
        </button>
      </div>
    </div>
  </div>
</template>

<route lang="yaml">
meta:
  requiresAuth: false
</route>
