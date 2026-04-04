<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clientsApi } from '@/api'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useRealmStore } from '@/stores/realm'
import { transformToRedirectUri, transformToWebOrigin } from '@/utils/urlTransform'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import type {
  ClientDetailResponse,
  UpdateClientRequest,
  IntegrationSnippetsResponse,
} from '@/types'

defineOptions({
  name: 'ClientDetailPage',
})

const route = useRoute()
const router = useRouter()
const toast = useToast()
const { confirm } = useConfirm()
const realmStore = useRealmStore()

const realmName = computed(() => route.params.name as string)
const clientId = computed(() => route.params.clientId as string)

const client = ref<ClientDetailResponse | null>(null)
const clientSecret = ref<string | null>(null)
const secretVisible = ref(false)
const loading = ref(true)
const error = ref<string | null>(null)
const activeTab = ref<'settings' | 'urls' | 'secrets' | 'integration'>('settings')

// URL editing state
const editingUrls = ref(false)
const savingUrls = ref(false)
const editRedirectUris = ref<string[]>([])
const editWebOrigins = ref<string[]>([])
const newRedirectUri = ref('')
const newWebOrigin = ref('')

// Integration snippets
const snippets = ref<IntegrationSnippetsResponse | null>(null)
const snippetsLoading = ref(false)

onMounted(async () => {
  // Ensure realm is loaded for sidebar
  if (!realmStore.currentRealm || realmStore.currentRealm.realmName !== realmName.value) {
    realmStore.fetchRealm(realmName.value).catch(() => {})
  }
  await loadClient()
})

async function loadClient(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    client.value = await clientsApi.get(realmName.value, clientId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load client'
    toast.error('Error', error.value)
  } finally {
    loading.value = false
  }
}

// --- URL editing ---
function startEditingUrls(): void {
  if (!client.value) return
  editRedirectUris.value = [...(client.value.redirectUris || [])]
  editWebOrigins.value = [...(client.value.webOrigins || [])]
  editingUrls.value = true
}

function cancelEditingUrls(): void {
  editingUrls.value = false
  newRedirectUri.value = ''
  newWebOrigin.value = ''
}

function addRedirectUri(): void {
  if (!newRedirectUri.value.trim()) return
  editRedirectUris.value.push(transformToRedirectUri(newRedirectUri.value.trim()))
  newRedirectUri.value = ''
}

function removeRedirectUri(index: number): void {
  editRedirectUris.value.splice(index, 1)
}

function addWebOrigin(): void {
  if (!newWebOrigin.value.trim()) return
  editWebOrigins.value.push(transformToWebOrigin(newWebOrigin.value.trim()))
  newWebOrigin.value = ''
}

function removeWebOrigin(index: number): void {
  editWebOrigins.value.splice(index, 1)
}

async function saveUrls(): Promise<void> {
  savingUrls.value = true
  try {
    const updateRequest: UpdateClientRequest = {
      redirectUris: editRedirectUris.value,
      webOrigins: editWebOrigins.value,
    }
    await clientsApi.update(realmName.value, clientId.value, updateRequest)
    // Refresh client data
    client.value = await clientsApi.get(realmName.value, clientId.value)
    editingUrls.value = false
    toast.success('Saved', 'URLs updated successfully')
  } catch (err) {
    toast.error('Error', err instanceof Error ? err.message : 'Failed to update URLs')
  } finally {
    savingUrls.value = false
  }
}

// --- Secrets ---
async function fetchSecret(): Promise<void> {
  try {
    const response = await clientsApi.getSecret(realmName.value, clientId.value)
    clientSecret.value = response.secret
    secretVisible.value = true
  } catch {
    toast.error('Error', 'Failed to fetch client secret')
  }
}

async function regenerateSecret(): Promise<void> {
  const confirmed = await confirm({
    title: 'Regenerate Secret',
    message: 'This will invalidate the current secret. All applications using it will need to be updated.',
    confirmLabel: 'Regenerate',
    destructive: true,
  })
  if (!confirmed) return

  try {
    const response = await clientsApi.regenerateSecret(realmName.value, clientId.value)
    clientSecret.value = response.secret
    secretVisible.value = true
    toast.success('Regenerated', 'Client secret has been regenerated')
  } catch {
    toast.error('Error', 'Failed to regenerate secret')
  }
}

function copyToClipboard(text: string): void {
  navigator.clipboard.writeText(text)
  toast.success('Copied', 'Copied to clipboard')
}

// --- Integration snippets ---
async function loadSnippets(): Promise<void> {
  if (snippets.value || !client.value) return
  snippetsLoading.value = true
  try {
    snippets.value = await clientsApi.getIntegrationSnippets(realmName.value, client.value.clientId)
  } catch {
    toast.error('Error', 'Failed to load integration snippets')
  } finally {
    snippetsLoading.value = false
  }
}

// --- Delete ---
async function handleDelete(): Promise<void> {
  const confirmed = await confirm({
    title: 'Delete Client',
    message: `Are you sure you want to delete "${client.value?.clientId}"? This action cannot be undone.`,
    confirmLabel: 'Delete',
    destructive: true,
  })
  if (!confirmed) return

  try {
    await clientsApi.delete(realmName.value, clientId.value)
    toast.success('Deleted', 'Client deleted successfully')
    router.push(`/realms/${realmName.value}/clients`)
  } catch {
    toast.error('Error', 'Failed to delete client')
  }
}

function formatDate(dateString: string): string {
  return new Date(dateString).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

const tabs = computed(() => {
  const items: { key: string; label: string }[] = [
    { key: 'settings', label: 'Settings' },
    { key: 'urls', label: 'URLs' },
  ]
  if (client.value && !client.value.publicClient) {
    items.push({ key: 'secrets', label: 'Secrets' })
  }
  items.push({ key: 'integration', label: 'Integration' })
  return items
})
</script>

<template>
  <SidebarLayout>
    <div class="mx-auto max-w-4xl">
      <!-- Loading -->
      <div v-if="loading" class="flex items-center justify-center py-20">
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
        class="rounded-lg bg-red-50 p-4 text-sm text-red-700 ring-1 ring-inset ring-red-600/20 dark:bg-red-500/10 dark:text-red-400 dark:ring-red-500/20"
      >
        {{ error }}
      </div>

      <template v-else-if="client">
        <!-- Back link -->
        <button
          class="mb-4 inline-flex items-center gap-1 text-sm text-zinc-500 transition-colors hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
          @click="router.push(`/realms/${realmName}/clients`)"
        >
          <svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M17 10a.75.75 0 01-.75.75H5.612l4.158 3.96a.75.75 0 11-1.04 1.08l-5.5-5.25a.75.75 0 010-1.08l5.5-5.25a.75.75 0 111.04 1.08L5.612 9.25H16.25A.75.75 0 0117 10z" clip-rule="evenodd" />
          </svg>
          Clients
        </button>

        <!-- Heading -->
        <div class="flex items-start justify-between">
          <div>
            <div class="flex items-center gap-3">
              <h1 class="text-2xl font-semibold text-zinc-900 dark:text-zinc-100">
                {{ client.name || client.clientId }}
              </h1>
              <AppBadge :variant="client.enabled ? 'success' : 'danger'" dot>
                {{ client.enabled ? 'Enabled' : 'Disabled' }}
              </AppBadge>
              <AppBadge :variant="client.publicClient ? 'info' : 'warning'">
                {{ client.publicClient ? 'Public' : 'Confidential' }}
              </AppBadge>
            </div>
            <p class="mt-1 font-mono text-sm text-zinc-500 dark:text-zinc-400">
              {{ client.clientId }}
            </p>
          </div>
          <AppButton variant="danger" @click="handleDelete">
            Delete
          </AppButton>
        </div>

        <!-- Tabs -->
        <div class="mt-6 border-b border-zinc-200 dark:border-zinc-800">
          <nav class="-mb-px flex gap-x-6" aria-label="Tabs">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              :class="[
                'border-b-2 px-1 pb-3 text-sm font-medium transition-colors',
                activeTab === tab.key
                  ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                  : 'border-transparent text-zinc-500 hover:border-zinc-300 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-300',
              ]"
              @click="activeTab = tab.key as any; tab.key === 'integration' && loadSnippets()"
            >
              {{ tab.label }}
            </button>
          </nav>
        </div>

        <!-- Settings tab -->
        <div v-if="activeTab === 'settings'" class="mt-6">
          <div class="rounded-lg bg-white ring-1 ring-zinc-200 dark:bg-zinc-900 dark:ring-zinc-800">
            <dl class="divide-y divide-zinc-100 dark:divide-zinc-800">
              <div class="px-5 py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">Client ID</dt>
                <dd class="mt-1 flex items-center gap-2 text-sm text-zinc-900 dark:text-zinc-100 sm:col-span-2 sm:mt-0">
                  <code class="rounded bg-zinc-100 px-1.5 py-0.5 font-mono text-xs dark:bg-zinc-800">{{ client.clientId }}</code>
                  <button
                    class="text-zinc-400 transition-colors hover:text-zinc-600 dark:hover:text-zinc-300"
                    @click="copyToClipboard(client.clientId)"
                  >
                    <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 17.25v3.375c0 .621-.504 1.125-1.125 1.125h-9.75a1.125 1.125 0 01-1.125-1.125V7.875c0-.621.504-1.125 1.125-1.125H6.75a9.06 9.06 0 011.5.124m7.5 10.376h3.375c.621 0 1.125-.504 1.125-1.125V11.25c0-4.46-3.243-8.161-7.5-8.876a9.06 9.06 0 00-1.5-.124H9.375c-.621 0-1.125.504-1.125 1.125v3.5m7.5 10.375H9.375a1.125 1.125 0 01-1.125-1.125v-9.25m12 6.625v-1.875a3.375 3.375 0 00-3.375-3.375h-1.5a1.125 1.125 0 01-1.125-1.125v-1.5a3.375 3.375 0 00-3.375-3.375H9.75" />
                    </svg>
                  </button>
                </dd>
              </div>
              <div v-if="client.description" class="px-5 py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">Description</dt>
                <dd class="mt-1 text-sm text-zinc-900 dark:text-zinc-100 sm:col-span-2 sm:mt-0">
                  {{ client.description }}
                </dd>
              </div>
              <div class="px-5 py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">Access Type</dt>
                <dd class="mt-1 text-sm sm:col-span-2 sm:mt-0">
                  <AppBadge :variant="client.publicClient ? 'info' : 'warning'">
                    {{ client.publicClient ? 'Public' : 'Confidential' }}
                  </AppBadge>
                </dd>
              </div>
              <div class="px-5 py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">Standard Flow</dt>
                <dd class="mt-1 text-sm text-zinc-900 dark:text-zinc-100 sm:col-span-2 sm:mt-0">
                  {{ client.standardFlowEnabled ? 'Enabled' : 'Disabled' }}
                </dd>
              </div>
              <div class="px-5 py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">Direct Access Grants</dt>
                <dd class="mt-1 text-sm text-zinc-900 dark:text-zinc-100 sm:col-span-2 sm:mt-0">
                  {{ client.directAccessGrantsEnabled ? 'Enabled' : 'Disabled' }}
                </dd>
              </div>
              <div class="px-5 py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">Service Accounts</dt>
                <dd class="mt-1 text-sm text-zinc-900 dark:text-zinc-100 sm:col-span-2 sm:mt-0">
                  {{ client.serviceAccountsEnabled ? 'Enabled' : 'Disabled' }}
                </dd>
              </div>
              <div v-if="client.pairedClientClientId" class="px-5 py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">Paired Client</dt>
                <dd class="mt-1 text-sm sm:col-span-2 sm:mt-0">
                  <button
                    class="font-mono text-xs text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300"
                    @click="router.push(`/realms/${realmName}/clients/${client!.pairedClientId}`)"
                  >
                    {{ client.pairedClientClientId }}
                  </button>
                </dd>
              </div>
              <div class="px-5 py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">Created</dt>
                <dd class="mt-1 text-sm text-zinc-900 dark:text-zinc-100 sm:col-span-2 sm:mt-0">
                  {{ formatDate(client.createdAt) }}
                </dd>
              </div>
            </dl>
          </div>
        </div>

        <!-- URLs tab -->
        <div v-else-if="activeTab === 'urls'" class="mt-6">
          <div class="rounded-lg bg-white ring-1 ring-zinc-200 dark:bg-zinc-900 dark:ring-zinc-800">
            <div class="flex items-center justify-between px-5 py-4">
              <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">Redirect URIs &amp; Web Origins</h3>
              <AppButton
                v-if="!editingUrls"
                variant="ghost"
                size="sm"
                @click="startEditingUrls"
              >
                Edit
              </AppButton>
              <div v-else class="flex gap-2">
                <AppButton variant="ghost" size="sm" @click="cancelEditingUrls">Cancel</AppButton>
                <AppButton size="sm" :loading="savingUrls" @click="saveUrls">Save</AppButton>
              </div>
            </div>

            <!-- View mode -->
            <div v-if="!editingUrls" class="border-t border-zinc-100 dark:border-zinc-800">
              <dl class="divide-y divide-zinc-100 dark:divide-zinc-800">
                <div class="px-5 py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                  <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">Root URL</dt>
                  <dd class="mt-1 text-sm sm:col-span-2 sm:mt-0">
                    <code v-if="client.rootUrl" class="rounded bg-zinc-100 px-1.5 py-0.5 font-mono text-xs dark:bg-zinc-800 dark:text-zinc-300">{{ client.rootUrl }}</code>
                    <span v-else class="text-zinc-400 dark:text-zinc-500 italic">Not set</span>
                  </dd>
                </div>
                <div class="px-5 py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                  <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">Redirect URIs</dt>
                  <dd class="mt-1 sm:col-span-2 sm:mt-0">
                    <ul v-if="client.redirectUris?.length" class="space-y-1.5">
                      <li v-for="uri in client.redirectUris" :key="uri">
                        <code class="rounded bg-zinc-100 px-1.5 py-0.5 font-mono text-xs dark:bg-zinc-800 dark:text-zinc-300">{{ uri }}</code>
                      </li>
                    </ul>
                    <span v-else class="text-sm text-zinc-400 dark:text-zinc-500 italic">None configured</span>
                  </dd>
                </div>
                <div class="px-5 py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                  <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">Web Origins</dt>
                  <dd class="mt-1 sm:col-span-2 sm:mt-0">
                    <ul v-if="client.webOrigins?.length" class="space-y-1.5">
                      <li v-for="origin in client.webOrigins" :key="origin">
                        <code class="rounded bg-zinc-100 px-1.5 py-0.5 font-mono text-xs dark:bg-zinc-800 dark:text-zinc-300">{{ origin }}</code>
                      </li>
                    </ul>
                    <span v-else class="text-sm text-zinc-400 dark:text-zinc-500 italic">None configured</span>
                  </dd>
                </div>
              </dl>
            </div>

            <!-- Edit mode -->
            <div v-else class="border-t border-zinc-100 px-5 py-4 dark:border-zinc-800">
              <div class="space-y-6">
                <!-- Redirect URIs -->
                <div>
                  <label class="block text-sm font-medium text-zinc-700 dark:text-zinc-300">Redirect URIs</label>
                  <p class="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">
                    Enter "localhost:5173" and it will auto-format to "http://localhost:5173/*"
                  </p>
                  <div class="mt-2 space-y-2">
                    <div
                      v-for="(uri, index) in editRedirectUris"
                      :key="index"
                      class="flex items-center gap-2"
                    >
                      <code class="flex-1 truncate rounded bg-zinc-100 px-2 py-1.5 font-mono text-xs dark:bg-zinc-800 dark:text-zinc-300">{{ uri }}</code>
                      <button
                        class="shrink-0 text-zinc-400 hover:text-red-500"
                        @click="removeRedirectUri(index)"
                      >
                        <svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                          <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
                        </svg>
                      </button>
                    </div>
                    <div class="flex gap-2">
                      <input
                        v-model="newRedirectUri"
                        type="text"
                        placeholder="Add redirect URI..."
                        class="flex-1 rounded-lg border-0 bg-zinc-100 px-3 py-1.5 text-sm text-zinc-900 ring-1 ring-inset ring-zinc-300 placeholder:text-zinc-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 dark:bg-zinc-800 dark:text-zinc-100 dark:ring-zinc-700 dark:placeholder:text-zinc-500 dark:focus:ring-indigo-500"
                        @keydown.enter.prevent="addRedirectUri"
                      />
                      <AppButton variant="secondary" size="sm" @click="addRedirectUri">Add</AppButton>
                    </div>
                  </div>
                </div>

                <!-- Web Origins -->
                <div>
                  <label class="block text-sm font-medium text-zinc-700 dark:text-zinc-300">Web Origins</label>
                  <p class="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">
                    Enter "example.com" and it will auto-format to "https://example.com"
                  </p>
                  <div class="mt-2 space-y-2">
                    <div
                      v-for="(origin, index) in editWebOrigins"
                      :key="index"
                      class="flex items-center gap-2"
                    >
                      <code class="flex-1 truncate rounded bg-zinc-100 px-2 py-1.5 font-mono text-xs dark:bg-zinc-800 dark:text-zinc-300">{{ origin }}</code>
                      <button
                        class="shrink-0 text-zinc-400 hover:text-red-500"
                        @click="removeWebOrigin(index)"
                      >
                        <svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                          <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
                        </svg>
                      </button>
                    </div>
                    <div class="flex gap-2">
                      <input
                        v-model="newWebOrigin"
                        type="text"
                        placeholder="Add web origin..."
                        class="flex-1 rounded-lg border-0 bg-zinc-100 px-3 py-1.5 text-sm text-zinc-900 ring-1 ring-inset ring-zinc-300 placeholder:text-zinc-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 dark:bg-zinc-800 dark:text-zinc-100 dark:ring-zinc-700 dark:placeholder:text-zinc-500 dark:focus:ring-indigo-500"
                        @keydown.enter.prevent="addWebOrigin"
                      />
                      <AppButton variant="secondary" size="sm" @click="addWebOrigin">Add</AppButton>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Secrets tab -->
        <div v-else-if="activeTab === 'secrets'" class="mt-6">
          <div class="rounded-lg bg-white ring-1 ring-zinc-200 dark:bg-zinc-900 dark:ring-zinc-800">
            <div class="px-5 py-4">
              <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">Client Secret</h3>
              <p class="mt-1 text-xs text-zinc-500 dark:text-zinc-400">
                Used for confidential clients to authenticate with the authorization server.
              </p>
            </div>
            <div class="border-t border-zinc-100 px-5 py-4 dark:border-zinc-800">
              <div v-if="secretVisible && clientSecret" class="space-y-3">
                <div class="flex items-center gap-2">
                  <code class="flex-1 break-all rounded bg-zinc-100 px-3 py-2 font-mono text-xs dark:bg-zinc-800 dark:text-zinc-300">
                    {{ clientSecret }}
                  </code>
                  <button
                    class="shrink-0 rounded p-1.5 text-zinc-400 transition-colors hover:bg-zinc-100 hover:text-zinc-600 dark:hover:bg-zinc-800 dark:hover:text-zinc-300"
                    @click="copyToClipboard(clientSecret!)"
                  >
                    <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 17.25v3.375c0 .621-.504 1.125-1.125 1.125h-9.75a1.125 1.125 0 01-1.125-1.125V7.875c0-.621.504-1.125 1.125-1.125H6.75a9.06 9.06 0 011.5.124m7.5 10.376h3.375c.621 0 1.125-.504 1.125-1.125V11.25c0-4.46-3.243-8.161-7.5-8.876a9.06 9.06 0 00-1.5-.124H9.375c-.621 0-1.125.504-1.125 1.125v3.5m7.5 10.375H9.375a1.125 1.125 0 01-1.125-1.125v-9.25m12 6.625v-1.875a3.375 3.375 0 00-3.375-3.375h-1.5a1.125 1.125 0 01-1.125-1.125v-1.5a3.375 3.375 0 00-3.375-3.375H9.75" />
                    </svg>
                  </button>
                </div>
                <AppButton variant="secondary" size="sm" @click="regenerateSecret">
                  Regenerate Secret
                </AppButton>
              </div>
              <div v-else>
                <AppButton variant="secondary" @click="fetchSecret">
                  Show Secret
                </AppButton>
              </div>
            </div>
          </div>
        </div>

        <!-- Integration tab -->
        <div v-else-if="activeTab === 'integration'" class="mt-6">
          <div v-if="snippetsLoading" class="flex items-center justify-center py-12">
            <svg
              class="h-6 w-6 animate-spin text-zinc-400"
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
            >
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
            </svg>
          </div>

          <div v-else-if="snippets" class="space-y-6">
            <!-- Connection info -->
            <div class="rounded-lg bg-white ring-1 ring-zinc-200 dark:bg-zinc-900 dark:ring-zinc-800">
              <div class="px-5 py-4">
                <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">Connection Details</h3>
              </div>
              <div class="border-t border-zinc-100 dark:border-zinc-800">
                <dl class="divide-y divide-zinc-100 dark:divide-zinc-800">
                  <div class="px-5 py-3 sm:grid sm:grid-cols-3 sm:gap-4">
                    <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">Keycloak URL</dt>
                    <dd class="mt-1 text-sm sm:col-span-2 sm:mt-0">
                      <code class="rounded bg-zinc-100 px-1.5 py-0.5 font-mono text-xs dark:bg-zinc-800 dark:text-zinc-300">{{ snippets.keycloakUrl }}</code>
                    </dd>
                  </div>
                  <div class="px-5 py-3 sm:grid sm:grid-cols-3 sm:gap-4">
                    <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">Realm</dt>
                    <dd class="mt-1 text-sm sm:col-span-2 sm:mt-0">
                      <code class="rounded bg-zinc-100 px-1.5 py-0.5 font-mono text-xs dark:bg-zinc-800 dark:text-zinc-300">{{ snippets.realmName }}</code>
                    </dd>
                  </div>
                  <div class="px-5 py-3 sm:grid sm:grid-cols-3 sm:gap-4">
                    <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">Client ID</dt>
                    <dd class="mt-1 text-sm sm:col-span-2 sm:mt-0">
                      <code class="rounded bg-zinc-100 px-1.5 py-0.5 font-mono text-xs dark:bg-zinc-800 dark:text-zinc-300">{{ snippets.clientId }}</code>
                    </dd>
                  </div>
                </dl>
              </div>
            </div>

            <!-- Frontend snippets -->
            <div v-if="snippets.snippets" class="space-y-4">
              <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">Frontend Integration</h3>
              <div v-for="(code, key) in snippets.snippets" :key="key" class="rounded-lg bg-white ring-1 ring-zinc-200 dark:bg-zinc-900 dark:ring-zinc-800">
                <div class="flex items-center justify-between px-4 py-2.5">
                  <span class="text-xs font-medium uppercase tracking-wide text-zinc-500 dark:text-zinc-400">{{ key }}</span>
                  <button
                    class="text-xs text-zinc-500 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-300"
                    @click="copyToClipboard(code)"
                  >
                    Copy
                  </button>
                </div>
                <pre class="overflow-x-auto border-t border-zinc-100 bg-zinc-50 px-4 py-3 font-mono text-xs text-zinc-800 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-300">{{ code }}</pre>
              </div>
            </div>

            <!-- Backend snippets -->
            <div v-if="snippets.backendSnippets" class="space-y-4">
              <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">Backend Integration</h3>
              <div v-for="(code, key) in snippets.backendSnippets" :key="key" class="rounded-lg bg-white ring-1 ring-zinc-200 dark:bg-zinc-900 dark:ring-zinc-800">
                <div class="flex items-center justify-between px-4 py-2.5">
                  <span class="text-xs font-medium uppercase tracking-wide text-zinc-500 dark:text-zinc-400">{{ key }}</span>
                  <button
                    class="text-xs text-zinc-500 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-300"
                    @click="copyToClipboard(code)"
                  >
                    Copy
                  </button>
                </div>
                <pre class="overflow-x-auto border-t border-zinc-100 bg-zinc-50 px-4 py-3 font-mono text-xs text-zinc-800 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-300">{{ code }}</pre>
              </div>
            </div>
          </div>

          <div v-else class="py-12 text-center text-sm text-zinc-500 dark:text-zinc-400">
            Failed to load integration snippets.
          </div>
        </div>
      </template>
    </div>
  </SidebarLayout>
</template>
