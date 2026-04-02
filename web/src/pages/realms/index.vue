<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useRealmStore } from '@/stores/realm'
import { useToast } from '@/composables/useToast'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppBadge from '@/components/ui/AppBadge.vue'

defineOptions({
  name: 'RealmsPage',
})

const router = useRouter()
const realmStore = useRealmStore()
const toast = useToast()

const loading = ref(true)
const syncing = ref(false)
const error = ref<string | null>(null)

onMounted(async () => {
  await loadRealms()
})

async function loadRealms(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    await realmStore.fetchRealms()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load realms'
    toast.error('Error', error.value)
  } finally {
    loading.value = false
  }
}

function navigateToRealm(realmName: string): void {
  router.push(`/realms/${realmName}`)
}

function navigateToCreate(): void {
  router.push('/realms/create')
}

async function syncFromKeycloak(): Promise<void> {
  syncing.value = true
  try {
    await realmStore.syncAllRealms()
    await loadRealms()
    toast.success('Synced', 'Realms imported from Keycloak')
  } catch (err) {
    toast.error('Sync Failed', err instanceof Error ? err.message : 'Failed to import realms')
  } finally {
    syncing.value = false
  }
}

function formatDate(dateString: string): string {
  return new Date(dateString).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}
</script>

<template>
  <SidebarLayout>
    <div class="mx-auto max-w-7xl">
      <!-- Page header -->
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-semibold text-zinc-900 dark:text-zinc-100">
            Realms
          </h1>
          <p class="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Manage your authentication realms
          </p>
        </div>
        <AppButton @click="navigateToCreate">
          Create Realm
        </AppButton>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="mt-12 flex items-center justify-center">
        <svg
          class="h-8 w-8 animate-spin text-zinc-400"
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
        </svg>
      </div>

      <!-- Error -->
      <div
        v-else-if="error"
        class="mt-8 rounded-lg bg-red-50 p-4 text-sm text-red-700 ring-1 ring-inset ring-red-600/20 dark:bg-red-500/10 dark:text-red-400 dark:ring-red-500/20"
      >
        {{ error }}
      </div>

      <!-- Empty state -->
      <div
        v-else-if="!realmStore.hasRealms"
        class="mt-8 flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-zinc-300 px-6 py-16 text-center dark:border-zinc-700"
      >
        <svg class="h-12 w-12 text-zinc-400" fill="none" viewBox="0 0 24 24" stroke-width="1" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" d="M2.25 13.5h3.86a2.25 2.25 0 012.012 1.244l.256.512a2.25 2.25 0 002.013 1.244h3.218a2.25 2.25 0 002.013-1.244l.256-.512a2.25 2.25 0 012.013-1.244h3.859m-19.5.338V18a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18v-4.162c0-.224-.034-.447-.1-.661L19.24 5.338a2.25 2.25 0 00-2.15-1.588H6.911a2.25 2.25 0 00-2.15 1.588L2.35 13.177a2.25 2.25 0 00-.1.661z" />
        </svg>
        <h3 class="mt-4 text-sm font-semibold text-zinc-900 dark:text-zinc-100">No realms found</h3>
        <p class="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          Import existing realms from Keycloak or create a new one.
        </p>
        <div class="mt-6 flex gap-3">
          <AppButton variant="secondary" :loading="syncing" @click="syncFromKeycloak">
            Import from Keycloak
          </AppButton>
          <AppButton @click="navigateToCreate">
            Create New Realm
          </AppButton>
        </div>
      </div>

      <!-- Realm grid -->
      <div v-else class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <button
          v-for="realm in realmStore.realms"
          :key="realm.id"
          class="group flex flex-col rounded-lg bg-white p-5 text-left ring-1 ring-zinc-200 transition-all hover:ring-zinc-300 hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:bg-zinc-900 dark:ring-zinc-800 dark:hover:ring-zinc-700"
          @click="navigateToRealm(realm.realmName)"
        >
          <!-- Realm display name -->
          <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            {{ realm.displayName || realm.realmName }}
          </h3>

          <!-- Realm name (muted) -->
          <p class="mt-0.5 font-mono text-xs text-zinc-500 dark:text-zinc-400">
            {{ realm.realmName }}
          </p>

          <!-- Badges -->
          <div class="mt-3 flex flex-wrap gap-1.5">
            <AppBadge :variant="realm.enabled ? 'success' : 'danger'" dot>
              {{ realm.enabled ? 'Enabled' : 'Disabled' }}
            </AppBadge>
            <AppBadge v-if="realm.spiEnabled" variant="info" dot>
              SPI Enabled
            </AppBadge>
          </div>

          <!-- Created date -->
          <p class="mt-auto pt-4 text-xs text-zinc-400 dark:text-zinc-500">
            Created {{ formatDate(realm.createdAt) }}
          </p>
        </button>
      </div>
    </div>
  </SidebarLayout>
</template>
