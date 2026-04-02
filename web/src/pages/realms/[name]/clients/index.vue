<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRealmStore } from '@/stores/realm'
import { clientsApi } from '@/api'
import { useToast } from '@/composables/useToast'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import type { Client } from '@/types'

defineOptions({
  name: 'RealmClientsPage',
})

const route = useRoute()
const router = useRouter()
const realmStore = useRealmStore()
const toast = useToast()

const realmName = computed(() => route.params.name as string)

const clients = ref<Client[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const activeTab = ref<'applications' | 'custom'>('applications')

onMounted(async () => {
  await loadClients()
})

async function loadClients(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    // Ensure realm is loaded for sidebar context
    if (!realmStore.currentRealm || realmStore.currentRealm.realmName !== realmName.value) {
      await realmStore.fetchRealm(realmName.value)
    }
    clients.value = await clientsApi.list(realmName.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load clients'
    toast.error('Error', error.value)
  } finally {
    loading.value = false
  }
}

// Applications = clients with paired clients or public clients (frontend-oriented)
const applicationClients = computed(() =>
  clients.value.filter((c) => c.publicClient),
)

// Custom = confidential / non-public clients
const customClients = computed(() =>
  clients.value.filter((c) => !c.publicClient),
)

const displayedClients = computed(() =>
  activeTab.value === 'applications' ? applicationClients.value : customClients.value,
)

function navigateToClient(client: Client): void {
  router.push(`/realms/${realmName.value}/clients/${client.id}`)
}

function navigateToCreate(): void {
  router.push(`/realms/${realmName.value}/clients/create`)
}
</script>

<template>
  <SidebarLayout>
    <div class="mx-auto max-w-7xl">
      <!-- Page header -->
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-semibold text-zinc-900 dark:text-zinc-100">
            Clients
          </h1>
          <p class="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Manage OAuth2 clients for {{ realmName }}
          </p>
        </div>
        <AppButton @click="navigateToCreate">
          Create Application
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

      <template v-else>
        <!-- Tabs -->
        <div class="mt-6 border-b border-zinc-200 dark:border-zinc-800">
          <nav class="-mb-px flex gap-x-6" aria-label="Tabs">
            <button
              :class="[
                'border-b-2 px-1 pb-3 text-sm font-medium transition-colors',
                activeTab === 'applications'
                  ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                  : 'border-transparent text-zinc-500 hover:border-zinc-300 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-300',
              ]"
              @click="activeTab = 'applications'"
            >
              Applications
              <span
                class="ml-2 rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400"
              >
                {{ applicationClients.length }}
              </span>
            </button>
            <button
              :class="[
                'border-b-2 px-1 pb-3 text-sm font-medium transition-colors',
                activeTab === 'custom'
                  ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                  : 'border-transparent text-zinc-500 hover:border-zinc-300 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-300',
              ]"
              @click="activeTab = 'custom'"
            >
              Custom Clients
              <span
                class="ml-2 rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400"
              >
                {{ customClients.length }}
              </span>
            </button>
          </nav>
        </div>

        <!-- Empty state -->
        <div
          v-if="displayedClients.length === 0"
          class="mt-8 flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-zinc-300 px-6 py-16 text-center dark:border-zinc-700"
        >
          <svg class="h-12 w-12 text-zinc-400" fill="none" viewBox="0 0 24 24" stroke-width="1" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" d="M6.429 9.75L2.25 12l4.179 2.25m0-4.5l5.571 3 5.571-3m-11.142 0L2.25 7.5 12 2.25l9.75 5.25-4.179 2.25m0 0L12 12.75 6.43 9.75m11.14 0l4.179 2.25L12 17.25 2.25 12l4.179-2.25m11.142 0l4.179 2.25v4.5l-9.75 5.25-9.75-5.25v-4.5" />
          </svg>
          <h3 class="mt-4 text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            No {{ activeTab === 'applications' ? 'applications' : 'custom clients' }} yet
          </h3>
          <p class="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Get started by creating a new client.
          </p>
          <div class="mt-6">
            <AppButton @click="navigateToCreate">
              Create Application
            </AppButton>
          </div>
        </div>

        <!-- Client list -->
        <ul v-else class="mt-4 divide-y divide-zinc-100 dark:divide-zinc-800">
          <li
            v-for="client in displayedClients"
            :key="client.id"
          >
            <button
              class="flex w-full items-center gap-4 rounded-lg px-4 py-4 text-left transition-colors hover:bg-zinc-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:hover:bg-zinc-800/50"
              @click="navigateToClient(client)"
            >
              <!-- Client icon -->
              <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-zinc-100 dark:bg-zinc-800">
                <svg class="h-5 w-5 text-zinc-600 dark:text-zinc-400" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M6.429 9.75L2.25 12l4.179 2.25m0-4.5l5.571 3 5.571-3m-11.142 0L2.25 7.5 12 2.25l9.75 5.25-4.179 2.25m0 0L12 12.75 6.43 9.75m11.14 0l4.179 2.25L12 17.25 2.25 12l4.179-2.25m11.142 0l4.179 2.25v4.5l-9.75 5.25-9.75-5.25v-4.5" />
                </svg>
              </div>

              <!-- Client info -->
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <p class="truncate text-sm font-medium text-zinc-900 dark:text-zinc-100">
                    {{ client.name || client.clientId }}
                  </p>
                  <!-- Status dot -->
                  <span
                    :class="[
                      'inline-block h-2 w-2 rounded-full',
                      client.enabled ? 'bg-emerald-500' : 'bg-zinc-400',
                    ]"
                  />
                </div>
                <p class="mt-0.5 truncate font-mono text-xs text-zinc-500 dark:text-zinc-400">
                  {{ client.clientId }}
                </p>
              </div>

              <!-- Badges -->
              <div class="hidden shrink-0 items-center gap-2 sm:flex">
                <AppBadge :variant="client.publicClient ? 'info' : 'warning'">
                  {{ client.publicClient ? 'Public' : 'Confidential' }}
                </AppBadge>
              </div>

              <!-- Chevron -->
              <svg class="h-5 w-5 shrink-0 text-zinc-400" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z" clip-rule="evenodd" />
              </svg>
            </button>
          </li>
        </ul>
      </template>
    </div>
  </SidebarLayout>
</template>
