<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { idpApi } from '@/api/idp'
import { useToast } from '@/composables/useToast'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppSlideOver from '@/components/ui/AppSlideOver.vue'
import AppInput from '@/components/ui/AppInput.vue'
import AppSelect from '@/components/ui/AppSelect.vue'
import AppEmptyState from '@/components/ui/AppEmptyState.vue'
import {
  LinkIcon,
  PlusIcon,
  ShieldCheckIcon,
} from '@heroicons/vue/24/outline'
import type { IdentityProvider, CreateIdpRequest } from '@/types'

const route = useRoute()
const toast = useToast()

const realmName = computed(() => route.params.name as string)

const providers = ref<IdentityProvider[]>([])
const loading = ref(true)

// Slide-over state
const slideOverOpen = ref(false)
const formAlias = ref('')
const formDisplayName = ref('')
const formProviderId = ref('')
const formEnabled = ref(true)
const formTrustEmail = ref(false)
const formConfig = ref('{}')
const formSubmitting = ref(false)
const formError = ref<string | null>(null)

const providerOptions = [
  { value: 'google', label: 'Google' },
  { value: 'saml', label: 'SAML' },
  { value: 'oidc', label: 'OpenID Connect (OIDC)' },
]

onMounted(async () => {
  await loadProviders()
})

async function loadProviders(): Promise<void> {
  loading.value = true
  try {
    providers.value = await idpApi.list(realmName.value)
  } catch (err) {
    toast.error('Failed to load identity providers', err instanceof Error ? err.message : undefined)
  } finally {
    loading.value = false
  }
}

function getProviderTypeBadgeVariant(providerId: string): 'info' | 'warning' | 'neutral' {
  switch (providerId) {
    case 'google':
      return 'info'
    case 'saml':
      return 'warning'
    default:
      return 'neutral'
  }
}

function getProviderLabel(providerId: string): string {
  switch (providerId) {
    case 'google':
      return 'Google'
    case 'saml':
      return 'SAML'
    case 'oidc':
      return 'OIDC'
    default:
      return providerId.toUpperCase()
  }
}

function openCreateSlideOver(): void {
  formAlias.value = ''
  formDisplayName.value = ''
  formProviderId.value = ''
  formEnabled.value = true
  formTrustEmail.value = false
  formConfig.value = '{}'
  formError.value = null
  slideOverOpen.value = true
}

async function handleCreateIdp(): Promise<void> {
  if (!formAlias.value.trim()) {
    formError.value = 'Alias is required'
    return
  }
  if (!formProviderId.value) {
    formError.value = 'Provider type is required'
    return
  }

  let parsedConfig: Record<string, string> = {}
  try {
    parsedConfig = JSON.parse(formConfig.value)
  } catch {
    formError.value = 'Config must be valid JSON'
    return
  }

  formSubmitting.value = true
  formError.value = null

  const request: CreateIdpRequest = {
    alias: formAlias.value.trim(),
    displayName: formDisplayName.value.trim() || undefined,
    providerId: formProviderId.value,
    enabled: formEnabled.value,
    trustEmail: formTrustEmail.value,
    config: Object.keys(parsedConfig).length > 0 ? parsedConfig : undefined,
  }

  try {
    await idpApi.create(realmName.value, request)
    toast.success('Provider created', `"${request.alias}" has been added`)
    slideOverOpen.value = false
    await loadProviders()
  } catch (err) {
    formError.value = err instanceof Error ? err.message : 'Failed to create identity provider'
  } finally {
    formSubmitting.value = false
  }
}
</script>

<template>
  <SidebarLayout>
    <div class="max-w-4xl mx-auto">
      <!-- Page header -->
      <div class="flex items-center justify-between mb-8">
        <div>
          <h1 class="text-2xl font-semibold text-zinc-900 dark:text-zinc-100">Identity Providers</h1>
          <p class="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Configure SSO federation for
            <span class="font-mono">{{ realmName }}</span>
          </p>
        </div>
        <AppButton @click="openCreateSlideOver">
          <PlusIcon class="h-4 w-4" />
          Add Provider
        </AppButton>
      </div>

      <!-- Loading state -->
      <div
        v-if="loading"
        class="flex items-center justify-center py-16"
      >
        <svg
          class="animate-spin h-6 w-6 text-zinc-400"
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
        </svg>
      </div>

      <template v-else>
        <!-- Empty state -->
        <AppEmptyState
          v-if="providers.length === 0"
          title="No identity providers"
          description="Add identity providers to enable SSO with Google, SAML, or OIDC."
          :icon="LinkIcon"
        >
          <AppButton @click="openCreateSlideOver">
            <PlusIcon class="h-4 w-4" />
            Add Provider
          </AppButton>
        </AppEmptyState>

        <!-- Provider list -->
        <div
          v-else
          class="space-y-3"
        >
          <div
            v-for="provider in providers"
            :key="provider.alias"
            class="bg-white dark:bg-zinc-900 rounded-lg ring-1 ring-zinc-200 dark:ring-zinc-800 p-4 hover:ring-zinc-300 dark:hover:ring-zinc-700 transition-all"
          >
            <div class="flex items-start justify-between">
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2.5 flex-wrap">
                  <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                    {{ provider.alias }}
                  </h3>
                  <AppBadge :variant="getProviderTypeBadgeVariant(provider.providerId)">
                    {{ getProviderLabel(provider.providerId) }}
                  </AppBadge>
                  <AppBadge
                    :variant="provider.enabled ? 'success' : 'danger'"
                    :dot="true"
                  >
                    {{ provider.enabled ? 'Enabled' : 'Disabled' }}
                  </AppBadge>
                </div>
                <p
                  v-if="provider.displayName"
                  class="mt-1 text-sm text-zinc-500 dark:text-zinc-400"
                >
                  {{ provider.displayName }}
                </p>
              </div>

              <!-- Trust email indicator -->
              <div
                v-if="provider.trustEmail"
                class="flex items-center gap-1 text-xs text-emerald-600 dark:text-emerald-400 flex-shrink-0"
                title="Trust email is enabled"
              >
                <ShieldCheckIcon class="h-4 w-4" />
                <span>Trust email</span>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- Create IDP Slide-Over -->
      <AppSlideOver
        :open="slideOverOpen"
        title="Add Identity Provider"
        @close="slideOverOpen = false"
      >
        <form
          class="flex flex-col gap-5"
          @submit.prevent="handleCreateIdp"
        >
          <!-- Error -->
          <div
            v-if="formError"
            class="rounded-lg bg-red-50 dark:bg-red-500/10 p-3 text-sm text-red-700 dark:text-red-400"
          >
            {{ formError }}
          </div>

          <AppInput
            v-model="formAlias"
            label="Alias"
            placeholder="e.g. google-sso, company-saml"
          />

          <AppInput
            v-model="formDisplayName"
            label="Display Name"
            placeholder="e.g. Sign in with Google"
          />

          <AppSelect
            v-model="formProviderId"
            label="Provider Type"
            :options="providerOptions"
            placeholder="Select a provider type"
          />

          <!-- Enabled toggle -->
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-zinc-900 dark:text-zinc-100">Enabled</span>
            <button
              type="button"
              role="switch"
              :aria-checked="formEnabled"
              :class="[
                formEnabled ? 'bg-primary-600' : 'bg-zinc-200 dark:bg-zinc-700',
                'relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-600 focus:ring-offset-2 dark:focus:ring-offset-zinc-900',
              ]"
              @click="formEnabled = !formEnabled"
            >
              <span
                :class="[
                  formEnabled ? 'translate-x-4' : 'translate-x-0.5',
                  'pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out mt-0.5',
                ]"
              />
            </button>
          </div>

          <!-- Trust email toggle -->
          <div class="flex items-center justify-between">
            <div>
              <span class="text-sm font-medium text-zinc-900 dark:text-zinc-100">Trust email</span>
              <p class="text-xs text-zinc-500 dark:text-zinc-400">
                Trust the email provided by the identity provider
              </p>
            </div>
            <button
              type="button"
              role="switch"
              :aria-checked="formTrustEmail"
              :class="[
                formTrustEmail ? 'bg-primary-600' : 'bg-zinc-200 dark:bg-zinc-700',
                'relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-600 focus:ring-offset-2 dark:focus:ring-offset-zinc-900',
              ]"
              @click="formTrustEmail = !formTrustEmail"
            >
              <span
                :class="[
                  formTrustEmail ? 'translate-x-4' : 'translate-x-0.5',
                  'pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out mt-0.5',
                ]"
              />
            </button>
          </div>

          <!-- Config JSON -->
          <div>
            <label class="block text-sm font-medium text-zinc-900 dark:text-zinc-100 mb-1.5">
              Configuration (JSON)
            </label>
            <textarea
              v-model="formConfig"
              rows="6"
              class="block w-full rounded-lg border-0 px-3 py-2 text-sm bg-white text-zinc-900 shadow-sm ring-1 ring-inset ring-zinc-300 placeholder:text-zinc-400 focus:ring-2 focus:ring-inset focus:ring-primary-600 dark:bg-zinc-900 dark:text-white dark:ring-zinc-700 dark:placeholder:text-zinc-500 dark:focus:ring-primary-500 font-mono"
              placeholder='{ "clientId": "...", "clientSecret": "..." }'
            />
          </div>

          <div class="mt-4 flex justify-end gap-3">
            <AppButton
              variant="secondary"
              type="button"
              @click="slideOverOpen = false"
            >
              Cancel
            </AppButton>
            <AppButton
              type="submit"
              :loading="formSubmitting"
              :disabled="!formAlias.trim() || !formProviderId"
            >
              Add Provider
            </AppButton>
          </div>
        </form>
      </AppSlideOver>
    </div>
  </SidebarLayout>
</template>
