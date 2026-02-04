<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { idpApi } from '@/api'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Checkbox from 'primevue/checkbox'
import Button from 'primevue/button'
import Message from 'primevue/message'
import type { CreateIdpRequest } from '@/types'

defineOptions({
  name: 'CreateIdpPage'
})

const route = useRoute()
const router = useRouter()
const toast = useToast()

const realmName = computed(() => route.params.name as string)

const alias = ref('')
const displayName = ref('')
const providerId = ref('')
const enabled = ref(true)
const trustEmail = ref(false)

// Provider-specific config
const clientId = ref('')
const clientSecret = ref('')
const authorizationUrl = ref('')
const tokenUrl = ref('')
const userInfoUrl = ref('')
const metadataUrl = ref('')

const loading = ref(false)
const error = ref<string | null>(null)

const providerTypes = [
  { label: 'Google', value: 'google' },
  { label: 'GitHub', value: 'github' },
  { label: 'Facebook', value: 'facebook' },
  { label: 'Microsoft', value: 'microsoft' },
  { label: 'OpenID Connect (OIDC)', value: 'oidc' },
  { label: 'SAML', value: 'saml' }
]

const isOidcProvider = computed(() => providerId.value === 'oidc')
const isSamlProvider = computed(() => providerId.value === 'saml')
const isSocialProvider = computed(() =>
  ['google', 'github', 'facebook', 'microsoft'].includes(providerId.value)
)

const isValidAlias = computed(() => {
  return /^[a-z][a-z0-9-]*$/.test(alias.value) && alias.value.length >= 2
})

const canSubmit = computed(() => {
  return isValidAlias.value && providerId.value && !loading.value
})

// Auto-generate alias from provider type
watch(providerId, (newValue) => {
  if (!alias.value && newValue) {
    alias.value = newValue
  }
})

async function handleSubmit(): Promise<void> {
  if (!canSubmit.value) return

  loading.value = true
  error.value = null

  const config: Record<string, string> = {}

  if (isSocialProvider.value || isOidcProvider.value) {
    if (clientId.value) config['clientId'] = clientId.value
    if (clientSecret.value) config['clientSecret'] = clientSecret.value
  }

  if (isOidcProvider.value) {
    if (authorizationUrl.value) config['authorizationUrl'] = authorizationUrl.value
    if (tokenUrl.value) config['tokenUrl'] = tokenUrl.value
    if (userInfoUrl.value) config['userInfoUrl'] = userInfoUrl.value
  }

  if (isSamlProvider.value) {
    if (metadataUrl.value) config['importFromUrl'] = metadataUrl.value
  }

  const request: CreateIdpRequest = {
    alias: alias.value,
    displayName: displayName.value || undefined,
    providerId: providerId.value,
    enabled: enabled.value,
    trustEmail: trustEmail.value,
    config: Object.keys(config).length > 0 ? config : undefined
  }

  try {
    await idpApi.create(realmName.value, request)
    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: `Identity provider "${alias.value}" created successfully`,
      life: 3000
    })
    router.push(`/realms/${realmName.value}/idp`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to create identity provider'
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.value,
      life: 5000
    })
  } finally {
    loading.value = false
  }
}

function handleCancel(): void {
  router.push(`/realms/${realmName.value}/idp`)
}
</script>

<template>
  <div class="create-idp-page">
    <div class="page-header">
      <Button
        icon="pi pi-arrow-left"
        text
        rounded
        @click="handleCancel"
      />
      <div class="header-content">
        <h1>Add Identity Provider</h1>
        <p>{{ realmName }}</p>
      </div>
    </div>

    <Card class="form-card">
      <template #content>
        <Message
          v-if="error"
          severity="error"
          :closable="true"
          class="form-error"
          @close="error = null"
        >
          {{ error }}
        </Message>

        <form @submit.prevent="handleSubmit" class="form">
          <div class="form-section">
            <h3>Provider Type</h3>

            <div class="form-field">
              <label for="providerId">Provider *</label>
              <Select
                id="providerId"
                v-model="providerId"
                :options="providerTypes"
                option-label="label"
                option-value="value"
                placeholder="Select a provider type"
                class="w-full"
              />
            </div>
          </div>

          <div class="form-section" v-if="providerId">
            <h3>Basic Information</h3>

            <div class="form-field">
              <label for="alias">Alias *</label>
              <InputText
                id="alias"
                v-model="alias"
                placeholder="my-provider"
                :invalid="alias.length > 0 && !isValidAlias"
                class="w-full"
              />
              <small class="field-help">
                Unique identifier for this provider. Used in URLs.
              </small>
            </div>

            <div class="form-field">
              <label for="displayName">Display Name</label>
              <InputText
                id="displayName"
                v-model="displayName"
                placeholder="My Provider"
                class="w-full"
              />
              <small class="field-help">
                Friendly name shown to users on the login page.
              </small>
            </div>

            <div class="form-field checkbox-field">
              <Checkbox
                id="enabled"
                v-model="enabled"
                :binary="true"
              />
              <label for="enabled" class="checkbox-label">
                <span>Enabled</span>
                <small>Allow users to authenticate with this provider.</small>
              </label>
            </div>

            <div class="form-field checkbox-field">
              <Checkbox
                id="trustEmail"
                v-model="trustEmail"
                :binary="true"
              />
              <label for="trustEmail" class="checkbox-label">
                <span>Trust Email</span>
                <small>Trust email addresses provided by this identity provider.</small>
              </label>
            </div>
          </div>

          <div class="form-section" v-if="isSocialProvider || isOidcProvider">
            <h3>OAuth Credentials</h3>

            <div class="form-field">
              <label for="clientId">Client ID</label>
              <InputText
                id="clientId"
                v-model="clientId"
                class="w-full"
              />
            </div>

            <div class="form-field">
              <label for="clientSecret">Client Secret</label>
              <InputText
                id="clientSecret"
                v-model="clientSecret"
                type="password"
                class="w-full"
              />
            </div>
          </div>

          <div class="form-section" v-if="isOidcProvider">
            <h3>OIDC Endpoints</h3>

            <div class="form-field">
              <label for="authorizationUrl">Authorization URL</label>
              <InputText
                id="authorizationUrl"
                v-model="authorizationUrl"
                placeholder="https://provider.com/oauth/authorize"
                class="w-full"
              />
            </div>

            <div class="form-field">
              <label for="tokenUrl">Token URL</label>
              <InputText
                id="tokenUrl"
                v-model="tokenUrl"
                placeholder="https://provider.com/oauth/token"
                class="w-full"
              />
            </div>

            <div class="form-field">
              <label for="userInfoUrl">User Info URL</label>
              <InputText
                id="userInfoUrl"
                v-model="userInfoUrl"
                placeholder="https://provider.com/userinfo"
                class="w-full"
              />
            </div>
          </div>

          <div class="form-section" v-if="isSamlProvider">
            <h3>SAML Configuration</h3>

            <div class="form-field">
              <label for="metadataUrl">Import from URL</label>
              <InputText
                id="metadataUrl"
                v-model="metadataUrl"
                placeholder="https://idp.example.com/metadata"
                class="w-full"
              />
              <small class="field-help">
                URL to import SAML metadata from the identity provider.
              </small>
            </div>
          </div>

          <div class="form-actions">
            <Button
              type="button"
              label="Cancel"
              severity="secondary"
              @click="handleCancel"
            />
            <Button
              type="submit"
              label="Add Provider"
              icon="pi pi-check"
              :loading="loading"
              :disabled="!canSubmit"
            />
          </div>
        </form>
      </template>
    </Card>
  </div>
</template>

<style scoped>
.create-idp-page {
  max-width: 700px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  margin-bottom: 1.5rem;
}

.header-content h1 {
  margin: 0 0 0.25rem;
  font-size: 1.5rem;
  font-weight: 600;
}

.header-content p {
  margin: 0;
  color: var(--p-text-muted-color);
  font-family: monospace;
}

.form-card {
  background-color: var(--p-surface-card);
}

.form-error {
  margin-bottom: 1.5rem;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.form-section h3 {
  margin: 0 0 1rem;
  font-size: 1rem;
  font-weight: 600;
  color: var(--p-text-color);
  border-bottom: 1px solid var(--p-surface-border);
  padding-bottom: 0.5rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.form-field:last-child {
  margin-bottom: 0;
}

.form-field label {
  font-weight: 500;
  color: var(--p-text-color);
}

.field-help {
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}

.checkbox-field {
  flex-direction: row;
  align-items: flex-start;
  gap: 0.75rem;
}

.checkbox-label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  cursor: pointer;
}

.checkbox-label span {
  font-weight: 500;
  color: var(--p-text-color);
}

.checkbox-label small {
  color: var(--p-text-muted-color);
  font-weight: 400;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding-top: 1rem;
  border-top: 1px solid var(--p-surface-border);
}

.w-full {
  width: 100%;
}
</style>
