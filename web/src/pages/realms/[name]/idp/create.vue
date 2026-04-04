<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import { idpApi } from '@/api'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppInput from '@/components/ui/AppInput.vue'
import AppSelect from '@/components/ui/AppSelect.vue'
import { ArrowLeftIcon } from '@heroicons/vue/24/outline'
import type { CreateIdpRequest } from '@/types'

defineOptions({ name: 'CreateIdpPage' })

const route = useRoute()
const router = useRouter()
const toast = useToast()

const realmName = computed(() => route.params.name as string)

const alias = ref('')
const displayName = ref('')
const providerId = ref('')
const enabled = ref(true)
const trustEmail = ref(false)
const clientId = ref('')
const clientSecret = ref('')
const authorizationUrl = ref('')
const tokenUrl = ref('')
const userInfoUrl = ref('')
const metadataUrl = ref('')

const loading = ref(false)
const error = ref<string | null>(null)

const providerOptions = [
  { value: 'google', label: 'Google' },
  { value: 'github', label: 'GitHub' },
  { value: 'facebook', label: 'Facebook' },
  { value: 'microsoft', label: 'Microsoft' },
  { value: 'oidc', label: 'OpenID Connect (OIDC)' },
  { value: 'saml', label: 'SAML' },
]

const isOidc = computed(() => providerId.value === 'oidc')
const isSaml = computed(() => providerId.value === 'saml')
const isSocial = computed(() =>
  ['google', 'github', 'facebook', 'microsoft'].includes(providerId.value),
)

const isValidAlias = computed(
  () => /^[a-z][a-z0-9-]*$/.test(alias.value) && alias.value.length >= 2,
)
const canSubmit = computed(() => isValidAlias.value && !!providerId.value && !loading.value)

watch(providerId, (val) => {
  if (!alias.value && val) alias.value = val
})

async function handleSubmit(): Promise<void> {
  if (!canSubmit.value) return
  loading.value = true
  error.value = null

  const config: Record<string, string> = {}
  if (isSocial.value || isOidc.value) {
    if (clientId.value) config['clientId'] = clientId.value
    if (clientSecret.value) config['clientSecret'] = clientSecret.value
  }
  if (isOidc.value) {
    if (authorizationUrl.value) config['authorizationUrl'] = authorizationUrl.value
    if (tokenUrl.value) config['tokenUrl'] = tokenUrl.value
    if (userInfoUrl.value) config['userInfoUrl'] = userInfoUrl.value
  }
  if (isSaml.value && metadataUrl.value) {
    config['importFromUrl'] = metadataUrl.value
  }

  const request: CreateIdpRequest = {
    alias: alias.value,
    displayName: displayName.value || undefined,
    providerId: providerId.value,
    enabled: enabled.value,
    trustEmail: trustEmail.value,
    config: Object.keys(config).length > 0 ? config : undefined,
  }

  try {
    await idpApi.create(realmName.value, request)
    toast.success('Identity provider added', `"${alias.value}" configured successfully`)
    router.push(`/realms/${realmName.value}/idp`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to create identity provider'
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
          @click="router.push(`/realms/${realmName}/idp`)"
        >
          <ArrowLeftIcon class="h-5 w-5" />
        </button>
        <div>
          <h1 class="text-xl font-semibold text-white">Add Identity Provider</h1>
          <p class="font-mono text-sm text-zinc-400">{{ realmName }}</p>
        </div>
      </div>

      <div class="space-y-6">
        <div
          v-if="error"
          class="rounded-lg border border-red-500/20 bg-red-500/10 p-3 text-sm text-red-400"
        >
          {{ error }}
        </div>

        <form class="space-y-6" @submit.prevent="handleSubmit">
          <!-- Provider Type -->
          <div class="rounded-xl border border-zinc-800 bg-zinc-900 p-6">
            <h2 class="mb-4 text-sm font-semibold uppercase tracking-wider text-zinc-500">
              Provider Type
            </h2>
            <div class="space-y-1.5">
              <label class="block text-sm font-medium text-zinc-300" for="providerId">
                Provider <span class="text-red-400">*</span>
              </label>
              <AppSelect
                id="providerId"
                v-model="providerId"
                :options="providerOptions"
                placeholder="Select a provider"
              />
            </div>
          </div>

          <!-- Basic Info (shown once provider is selected) -->
          <div v-if="providerId" class="rounded-xl border border-zinc-800 bg-zinc-900 p-6">
            <h2 class="mb-4 text-sm font-semibold uppercase tracking-wider text-zinc-500">
              Basic Information
            </h2>
            <div class="space-y-4">
              <div class="space-y-1.5">
                <label class="block text-sm font-medium text-zinc-300" for="alias">
                  Alias <span class="text-red-400">*</span>
                </label>
                <AppInput
                  id="alias"
                  v-model="alias"
                  placeholder="my-provider"
                  :invalid="alias.length > 0 && !isValidAlias"
                />
                <p v-if="alias.length > 0 && !isValidAlias" class="text-xs text-red-400">
                  Lowercase letters, numbers and hyphens only. Must start with a letter.
                </p>
                <p v-else class="text-xs text-zinc-500">Unique identifier used in URLs.</p>
              </div>

              <div class="space-y-1.5">
                <label class="block text-sm font-medium text-zinc-300" for="displayName">
                  Display Name
                </label>
                <AppInput id="displayName" v-model="displayName" placeholder="My Provider" />
                <p class="text-xs text-zinc-500">Shown to users on the login page.</p>
              </div>

              <div class="flex items-center gap-3">
                <input
                  id="enabled"
                  v-model="enabled"
                  type="checkbox"
                  class="h-4 w-4 rounded border-zinc-600 bg-zinc-800 text-indigo-500 focus:ring-indigo-500"
                />
                <div>
                  <label class="text-sm font-medium text-zinc-300" for="enabled">Enabled</label>
                  <p class="text-xs text-zinc-500">Allow users to authenticate with this provider.</p>
                </div>
              </div>

              <div class="flex items-center gap-3">
                <input
                  id="trustEmail"
                  v-model="trustEmail"
                  type="checkbox"
                  class="h-4 w-4 rounded border-zinc-600 bg-zinc-800 text-indigo-500 focus:ring-indigo-500"
                />
                <div>
                  <label class="text-sm font-medium text-zinc-300" for="trustEmail">
                    Trust Email
                  </label>
                  <p class="text-xs text-zinc-500">
                    Trust email addresses provided by this identity provider.
                  </p>
                </div>
              </div>
            </div>
          </div>

          <!-- OAuth Credentials -->
          <div
            v-if="isSocial || isOidc"
            class="rounded-xl border border-zinc-800 bg-zinc-900 p-6"
          >
            <h2 class="mb-4 text-sm font-semibold uppercase tracking-wider text-zinc-500">
              OAuth Credentials
            </h2>
            <div class="space-y-4">
              <div class="space-y-1.5">
                <label class="block text-sm font-medium text-zinc-300" for="clientId">
                  Client ID
                </label>
                <AppInput id="clientId" v-model="clientId" placeholder="your-client-id" />
              </div>
              <div class="space-y-1.5">
                <label class="block text-sm font-medium text-zinc-300" for="clientSecret">
                  Client Secret
                </label>
                <AppInput
                  id="clientSecret"
                  v-model="clientSecret"
                  type="password"
                  placeholder="your-client-secret"
                />
              </div>
            </div>
          </div>

          <!-- OIDC Endpoints -->
          <div v-if="isOidc" class="rounded-xl border border-zinc-800 bg-zinc-900 p-6">
            <h2 class="mb-4 text-sm font-semibold uppercase tracking-wider text-zinc-500">
              OIDC Endpoints
            </h2>
            <div class="space-y-4">
              <div class="space-y-1.5">
                <label class="block text-sm font-medium text-zinc-300" for="authorizationUrl">
                  Authorization URL
                </label>
                <AppInput
                  id="authorizationUrl"
                  v-model="authorizationUrl"
                  placeholder="https://provider.com/oauth/authorize"
                />
              </div>
              <div class="space-y-1.5">
                <label class="block text-sm font-medium text-zinc-300" for="tokenUrl">
                  Token URL
                </label>
                <AppInput
                  id="tokenUrl"
                  v-model="tokenUrl"
                  placeholder="https://provider.com/oauth/token"
                />
              </div>
              <div class="space-y-1.5">
                <label class="block text-sm font-medium text-zinc-300" for="userInfoUrl">
                  User Info URL
                </label>
                <AppInput
                  id="userInfoUrl"
                  v-model="userInfoUrl"
                  placeholder="https://provider.com/userinfo"
                />
              </div>
            </div>
          </div>

          <!-- SAML -->
          <div v-if="isSaml" class="rounded-xl border border-zinc-800 bg-zinc-900 p-6">
            <h2 class="mb-4 text-sm font-semibold uppercase tracking-wider text-zinc-500">
              SAML Configuration
            </h2>
            <div class="space-y-1.5">
              <label class="block text-sm font-medium text-zinc-300" for="metadataUrl">
                Import Metadata from URL
              </label>
              <AppInput
                id="metadataUrl"
                v-model="metadataUrl"
                placeholder="https://idp.example.com/metadata"
              />
              <p class="text-xs text-zinc-500">URL to import SAML metadata from the identity provider.</p>
            </div>
          </div>

          <div class="flex justify-end gap-3">
            <AppButton
              outline
              type="button"
              @click="router.push(`/realms/${realmName}/idp`)"
            >
              Cancel
            </AppButton>
            <AppButton color="indigo" type="submit" :loading="loading" :disabled="!canSubmit">
              Add Provider
            </AppButton>
          </div>
        </form>
      </div>
    </div>
  </SidebarLayout>
</template>
