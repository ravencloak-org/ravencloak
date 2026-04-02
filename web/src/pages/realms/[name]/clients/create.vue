<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import { clientsApi } from '@/api'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppInput from '@/components/ui/AppInput.vue'
import { ArrowLeftIcon, CubeIcon, GlobeAltIcon, ServerIcon } from '@heroicons/vue/24/outline'
import { transformToRedirectUri, transformToWebOrigin } from '@/utils/urlTransform'
import type { CreateClientRequest, CreateApplicationRequest, ApplicationType } from '@/types'

defineOptions({ name: 'CreateClientPage' })

const route = useRoute()
const router = useRouter()
const toast = useToast()

const realmName = computed(() => route.params.name as string)

type CreationMode = 'application' | 'custom'
const creationMode = ref<CreationMode>('application')
const applicationType = ref<ApplicationType>('FULL_STACK')
const applicationName = ref('')
const clientId = ref('')
const name = ref('')
const description = ref('')
const publicClient = ref(true)
const standardFlowEnabled = ref(true)
const directAccessGrantsEnabled = ref(false)
const serviceAccountsEnabled = ref(false)
const rootUrl = ref('')
const baseUrl = ref('')
// Stored as newline-separated strings; transformed on submit
const redirectUrisRaw = ref('')
const webOriginsRaw = ref('')

const loading = ref(false)
const error = ref<string | null>(null)

watch(applicationName, (val) => {
  if (creationMode.value === 'application') name.value = val
})

const appTypeOptions: Array<{
  value: ApplicationType
  label: string
  description: string
  icon: object
}> = [
  {
    value: 'FULL_STACK',
    label: 'Full-Stack',
    description: 'Creates frontend + backend clients',
    icon: CubeIcon,
  },
  {
    value: 'FRONTEND_ONLY',
    label: 'Frontend Only',
    description: 'Public client for browser apps',
    icon: GlobeAltIcon,
  },
  {
    value: 'BACKEND_ONLY',
    label: 'Backend Only',
    description: 'Confidential client for APIs',
    icon: ServerIcon,
  },
]

const isValidAppName = computed(
  () => /^[a-z][a-z0-9_-]*$/.test(applicationName.value) && applicationName.value.length >= 2,
)
const isValidClientId = computed(
  () => /^[a-z][a-z0-9_-]*$/.test(clientId.value) && clientId.value.length >= 2,
)
const canSubmit = computed(() => {
  if (loading.value) return false
  return creationMode.value === 'application' ? isValidAppName.value : isValidClientId.value
})

const clientNamePreview = computed(() => {
  if (creationMode.value !== 'application' || !applicationName.value) return []
  switch (applicationType.value) {
    case 'FRONTEND_ONLY':
      return [`${applicationName.value}-web`]
    case 'BACKEND_ONLY':
      return [`${applicationName.value}-backend`]
    case 'FULL_STACK':
      return [`${applicationName.value}-web`, `${applicationName.value}-backend`]
    default:
      return []
  }
})

function parseUrls(raw: string, transformer: (s: string) => string): string[] {
  return raw
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean)
    .map(transformer)
}

async function handleSubmit(): Promise<void> {
  if (!canSubmit.value) return
  loading.value = true
  error.value = null

  try {
    if (creationMode.value === 'application') {
      const request: CreateApplicationRequest = {
        applicationName: applicationName.value,
        displayName: name.value || undefined,
        description: description.value || undefined,
        applicationType: applicationType.value,
        rootUrl: rootUrl.value || undefined,
        baseUrl: baseUrl.value || undefined,
        redirectUris:
          redirectUrisRaw.value.trim()
            ? parseUrls(redirectUrisRaw.value, transformToRedirectUri)
            : undefined,
        webOrigins:
          webOriginsRaw.value.trim()
            ? parseUrls(webOriginsRaw.value, transformToWebOrigin)
            : undefined,
      }
      const response = await clientsApi.createApplication(realmName.value, request)
      const created: string[] = []
      if (response.frontendClient) created.push(response.frontendClient.clientId)
      if (response.backendClient) created.push(response.backendClient.clientId)
      toast.success('Application created', `Created: ${created.join(', ')}`)
    } else {
      const request: CreateClientRequest = {
        clientId: clientId.value,
        name: name.value || undefined,
        description: description.value || undefined,
        publicClient: publicClient.value,
        standardFlowEnabled: standardFlowEnabled.value,
        directAccessGrantsEnabled: directAccessGrantsEnabled.value,
        serviceAccountsEnabled: serviceAccountsEnabled.value,
        rootUrl: rootUrl.value || undefined,
        baseUrl: baseUrl.value || undefined,
        redirectUris:
          redirectUrisRaw.value.trim()
            ? parseUrls(redirectUrisRaw.value, transformToRedirectUri)
            : undefined,
        webOrigins:
          webOriginsRaw.value.trim()
            ? parseUrls(webOriginsRaw.value, transformToWebOrigin)
            : undefined,
      }
      await clientsApi.create(realmName.value, request)
      toast.success('Client created', `"${clientId.value}" created successfully`)
    }
    router.push(`/realms/${realmName.value}/clients`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to create client'
    toast.error('Error', error.value ?? undefined)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <SidebarLayout>
    <div class="mx-auto max-w-2xl px-4 py-8 sm:px-6">
      <div class="mb-6 flex items-center gap-3">
        <button
          class="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-800 hover:text-white"
          @click="router.push(`/realms/${realmName}/clients`)"
        >
          <ArrowLeftIcon class="h-5 w-5" />
        </button>
        <div>
          <h1 class="text-xl font-semibold text-white">Create Client</h1>
          <p class="font-mono text-sm text-zinc-400">{{ realmName }}</p>
        </div>
      </div>

      <form class="space-y-6" @submit.prevent="handleSubmit">
        <div
          v-if="error"
          class="rounded-lg border border-red-500/20 bg-red-500/10 p-3 text-sm text-red-400"
        >
          {{ error }}
        </div>

        <!-- Mode selector -->
        <div class="rounded-xl border border-zinc-800 bg-zinc-900 p-6">
          <h2 class="mb-3 text-sm font-semibold uppercase tracking-wider text-zinc-500">
            Creation Mode
          </h2>
          <div class="grid grid-cols-2 gap-3">
            <button
              type="button"
              :class="[
                'rounded-lg border px-4 py-3 text-left text-sm transition-colors',
                creationMode === 'application'
                  ? 'border-indigo-500/50 bg-indigo-500/10 text-white'
                  : 'border-zinc-700 text-zinc-400 hover:border-zinc-600 hover:text-white',
              ]"
              @click="creationMode = 'application'"
            >
              <p class="font-semibold">Application</p>
              <p class="mt-0.5 text-xs opacity-70">Auto-configures paired clients</p>
            </button>
            <button
              type="button"
              :class="[
                'rounded-lg border px-4 py-3 text-left text-sm transition-colors',
                creationMode === 'custom'
                  ? 'border-indigo-500/50 bg-indigo-500/10 text-white'
                  : 'border-zinc-700 text-zinc-400 hover:border-zinc-600 hover:text-white',
              ]"
              @click="creationMode = 'custom'"
            >
              <p class="font-semibold">Custom Client</p>
              <p class="mt-0.5 text-xs opacity-70">Full control over configuration</p>
            </button>
          </div>
        </div>

        <!-- Application type cards (application mode only) -->
        <div v-if="creationMode === 'application'" class="rounded-xl border border-zinc-800 bg-zinc-900 p-6">
          <h2 class="mb-3 text-sm font-semibold uppercase tracking-wider text-zinc-500">
            Application Type
          </h2>
          <div class="grid grid-cols-3 gap-3">
            <button
              v-for="opt in appTypeOptions"
              :key="opt.value"
              type="button"
              :class="[
                'flex flex-col items-center rounded-lg border px-3 py-4 text-center text-sm transition-colors',
                applicationType === opt.value
                  ? 'border-indigo-500/50 bg-indigo-500/10 text-white'
                  : 'border-zinc-700 text-zinc-400 hover:border-zinc-600 hover:text-white',
              ]"
              @click="applicationType = opt.value"
            >
              <component
                :is="opt.icon"
                class="mb-2 h-6 w-6"
                :class="applicationType === opt.value ? 'text-indigo-400' : ''"
              />
              <span class="font-semibold">{{ opt.label }}</span>
              <span class="mt-1 text-xs opacity-70">{{ opt.description }}</span>
            </button>
          </div>
        </div>

        <!-- Basic info -->
        <div class="rounded-xl border border-zinc-800 bg-zinc-900 p-6">
          <h2 class="mb-4 text-sm font-semibold uppercase tracking-wider text-zinc-500">
            Basic Information
          </h2>
          <div class="space-y-4">
            <!-- Application name or Client ID -->
            <div v-if="creationMode === 'application'" class="space-y-1.5">
              <label class="block text-sm font-medium text-zinc-300" for="applicationName">
                Application Name <span class="text-red-400">*</span>
              </label>
              <AppInput
                id="applicationName"
                v-model="applicationName"
                placeholder="my-app"
                :invalid="applicationName.length > 0 && !isValidAppName"
              />
              <p
                v-if="applicationName.length > 0 && !isValidAppName"
                class="text-xs text-red-400"
              >
                Lowercase letters, numbers, underscores and hyphens. Must start with a letter.
              </p>
              <div v-if="clientNamePreview.length" class="flex flex-wrap items-center gap-2 pt-1">
                <span class="text-xs text-zinc-500">Will create:</span>
                <code
                  v-for="n in clientNamePreview"
                  :key="n"
                  class="rounded bg-indigo-500/10 px-2 py-0.5 font-mono text-xs text-indigo-300"
                >
                  {{ n }}
                </code>
              </div>
            </div>

            <div v-else class="space-y-1.5">
              <label class="block text-sm font-medium text-zinc-300" for="clientId">
                Client ID <span class="text-red-400">*</span>
              </label>
              <AppInput
                id="clientId"
                v-model="clientId"
                placeholder="my-client"
                :invalid="clientId.length > 0 && !isValidClientId"
              />
              <p v-if="clientId.length > 0 && !isValidClientId" class="text-xs text-red-400">
                Lowercase letters, numbers, underscores and hyphens. Must start with a letter.
              </p>
            </div>

            <div class="space-y-1.5">
              <label class="block text-sm font-medium text-zinc-300" for="name">
                Display Name
              </label>
              <AppInput id="name" v-model="name" placeholder="My Application" />
            </div>

            <div class="space-y-1.5">
              <label class="block text-sm font-medium text-zinc-300" for="description">
                Description
              </label>
              <textarea
                id="description"
                v-model="description"
                rows="2"
                placeholder="Optional description"
                class="w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-white placeholder-zinc-500 hover:border-white/20 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
            </div>
          </div>
        </div>

        <!-- Client type (custom mode only) -->
        <div v-if="creationMode === 'custom'" class="rounded-xl border border-zinc-800 bg-zinc-900 p-6">
          <h2 class="mb-4 text-sm font-semibold uppercase tracking-wider text-zinc-500">
            Client Type &amp; Flows
          </h2>
          <div class="space-y-4">
            <label class="flex cursor-pointer items-start gap-3">
              <input
                v-model="publicClient"
                type="checkbox"
                class="mt-0.5 h-4 w-4 rounded border-zinc-600 bg-zinc-800 text-indigo-500 focus:ring-indigo-500"
              />
              <div>
                <p class="text-sm font-medium text-zinc-300">Public Client</p>
                <p class="text-xs text-zinc-500">No client secret required. Use for browser-based apps.</p>
              </div>
            </label>
            <label class="flex cursor-pointer items-start gap-3">
              <input
                v-model="standardFlowEnabled"
                type="checkbox"
                class="mt-0.5 h-4 w-4 rounded border-zinc-600 bg-zinc-800 text-indigo-500 focus:ring-indigo-500"
              />
              <div>
                <p class="text-sm font-medium text-zinc-300">Standard Flow (Authorization Code)</p>
                <p class="text-xs text-zinc-500">Recommended for web applications.</p>
              </div>
            </label>
            <label class="flex cursor-pointer items-start gap-3">
              <input
                v-model="directAccessGrantsEnabled"
                type="checkbox"
                class="mt-0.5 h-4 w-4 rounded border-zinc-600 bg-zinc-800 text-indigo-500 focus:ring-indigo-500"
              />
              <div>
                <p class="text-sm font-medium text-zinc-300">Direct Access Grants (Resource Owner Password)</p>
                <p class="text-xs text-zinc-500">Only for trusted internal clients.</p>
              </div>
            </label>
            <label
              class="flex cursor-pointer items-start gap-3"
              :class="publicClient ? 'opacity-40' : ''"
            >
              <input
                v-model="serviceAccountsEnabled"
                type="checkbox"
                :disabled="publicClient"
                class="mt-0.5 h-4 w-4 rounded border-zinc-600 bg-zinc-800 text-indigo-500 focus:ring-indigo-500"
              />
              <div>
                <p class="text-sm font-medium text-zinc-300">Service Accounts (Client Credentials)</p>
                <p class="text-xs text-zinc-500">Confidential clients only.</p>
              </div>
            </label>
          </div>
        </div>

        <!-- URLs (shown unless backend-only application) -->
        <div
          v-if="creationMode === 'custom' || applicationType !== 'BACKEND_ONLY'"
          class="rounded-xl border border-zinc-800 bg-zinc-900 p-6"
        >
          <h2 class="mb-1 text-sm font-semibold uppercase tracking-wider text-zinc-500">URLs</h2>
          <p v-if="creationMode === 'application'" class="mb-4 text-xs text-zinc-500">
            Applied to the frontend client.
          </p>
          <div v-else class="mb-4" />
          <div class="space-y-4">
            <div class="space-y-1.5">
              <label class="block text-sm font-medium text-zinc-300" for="rootUrl">Root URL</label>
              <AppInput id="rootUrl" v-model="rootUrl" placeholder="https://myapp.example.com" />
            </div>
            <div class="space-y-1.5">
              <label class="block text-sm font-medium text-zinc-300" for="baseUrl">Base URL</label>
              <AppInput id="baseUrl" v-model="baseUrl" placeholder="/app" />
            </div>
            <div class="space-y-1.5">
              <label class="block text-sm font-medium text-zinc-300" for="redirectUris">
                Valid Redirect URIs
              </label>
              <textarea
                id="redirectUris"
                v-model="redirectUrisRaw"
                rows="3"
                placeholder="localhost:5173&#10;example.com/app"
                class="w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 font-mono text-sm text-white placeholder-zinc-500 hover:border-white/20 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
              <p class="text-xs text-zinc-500">
                One URL per line. Schemes and wildcards are added automatically.
              </p>
            </div>
            <div class="space-y-1.5">
              <label class="block text-sm font-medium text-zinc-300" for="webOrigins">
                Web Origins
              </label>
              <textarea
                id="webOrigins"
                v-model="webOriginsRaw"
                rows="2"
                placeholder="localhost:5173&#10;example.com"
                class="w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 font-mono text-sm text-white placeholder-zinc-500 hover:border-white/20 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
              <p class="text-xs text-zinc-500">One origin per line. Use + to allow all redirect URI origins.</p>
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-3">
          <AppButton outline type="button" @click="router.push(`/realms/${realmName}/clients`)">
            Cancel
          </AppButton>
          <AppButton color="indigo" type="submit" :loading="loading" :disabled="!canSubmit">
            {{ creationMode === 'application' ? 'Create Application' : 'Create Client' }}
          </AppButton>
        </div>
      </form>
    </div>
  </SidebarLayout>
</template>
