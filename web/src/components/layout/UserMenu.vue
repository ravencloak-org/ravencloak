<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ArrowRightStartOnRectangleIcon, ClockIcon } from '@heroicons/vue/20/solid'

const router = useRouter()
const authStore = useAuthStore()

const userDisplayName = computed(() => {
  if (authStore.user?.displayName) {
    return authStore.user.displayName
  }
  if (authStore.user?.firstName) {
    return `${authStore.user.firstName} ${authStore.user.lastName ?? ''}`.trim()
  }
  return authStore.user?.email ?? 'User'
})

const userEmail = computed(() => {
  return authStore.user?.email ?? ''
})

const userInitials = computed(() => {
  const name = userDisplayName.value
  const parts = name.split(' ')
  const first = parts[0]
  const second = parts[1]
  if (parts.length >= 2 && first && second && first[0] && second[0]) {
    return (first[0] + second[0]).toUpperCase()
  }
  return name.substring(0, 2).toUpperCase()
})

function navigateToMyActions(): void {
  router.push('/my-actions')
}

async function handleLogout(): Promise<void> {
  await authStore.logout()
}
</script>

<template>
  <div class="border-t border-zinc-800 px-3 py-4">
    <!-- User info -->
    <div class="flex items-center gap-x-3 px-1 pb-3">
      <div
        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-zinc-700 text-xs font-medium text-white"
      >
        {{ userInitials }}
      </div>
      <div class="min-w-0 flex-1">
        <p class="truncate text-sm font-medium text-white">
          {{ userDisplayName }}
        </p>
        <p class="truncate text-xs text-zinc-400">
          {{ userEmail }}
        </p>
      </div>
    </div>

    <!-- Actions -->
    <div class="flex flex-col gap-0.5">
      <button
        class="flex w-full items-center gap-x-2 rounded-lg px-2 py-1.5 text-sm text-zinc-400 transition-colors hover:bg-zinc-800 hover:text-white"
        @click="navigateToMyActions"
      >
        <ClockIcon class="h-4 w-4" />
        My Actions
      </button>
      <button
        class="flex w-full items-center gap-x-2 rounded-lg px-2 py-1.5 text-sm text-zinc-400 transition-colors hover:bg-zinc-800 hover:text-white"
        @click="handleLogout"
      >
        <ArrowRightStartOnRectangleIcon class="h-4 w-4" />
        Sign Out
      </button>
    </div>
  </div>
</template>
