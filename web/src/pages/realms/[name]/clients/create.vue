<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { clientsApi } from '@/api'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Checkbox from 'primevue/checkbox'
import Button from 'primevue/button'
import Chips from 'primevue/chips'
import Message from 'primevue/message'
import { transformToRedirectUri, transformToWebOrigin } from '@/utils/urlTransform'
import type { CreateClientRequest } from '@/types'

defineOptions({
  name: 'CreateClientPage'
})

const route = useRoute()
const router = useRouter()
const toast = useToast()

const realmName = computed(() => route.params.name as string)

const clientId = ref('')
const name = ref('')
const description = ref('')
const publicClient = ref(true)
const standardFlowEnabled = ref(true)
const directAccessGrantsEnabled = ref(false)
const serviceAccountsEnabled = ref(false)
const rootUrl = ref('')
const baseUrl = ref('')
const redirectUris = ref<string[]>([])
const webOrigins = ref<string[]>([])

const loading = ref(false)
const error = ref<string | null>(null)

// Transform redirect URIs when they're added
function handleRedirectUriAdd(event: { value: string[] }): void {
  const transformed = event.value.map(uri => transformToRedirectUri(uri))
  // Only update if values changed
  if (JSON.stringify(transformed) !== JSON.stringify(redirectUris.value)) {
    redirectUris.value = transformed
    toast.add({
      severity: 'info',
      summary: 'URL Formatted',
      detail: 'Redirect URIs have been auto-formatted for Keycloak',
      life: 2000
    })
  }
}

// Transform web origins when they're added
function handleWebOriginAdd(event: { value: string[] }): void {
  const transformed = event.value.map(origin => transformToWebOrigin(origin))
  // Only update if values changed
  if (JSON.stringify(transformed) !== JSON.stringify(webOrigins.value)) {
    webOrigins.value = transformed
    toast.add({
      severity: 'info',
      summary: 'URL Formatted',
      detail: 'Web origins have been auto-formatted for Keycloak',
      life: 2000
    })
  }
}

// Transform root URL on blur
function handleRootUrlBlur(): void {
  if (rootUrl.value && !rootUrl.value.startsWith('http')) {
    const isLocal = rootUrl.value.includes('localhost') || rootUrl.value.includes('127.0.0.1')
    rootUrl.value = (isLocal ? 'http://' : 'https://') + rootUrl.value
  }
}

const isValidClientId = computed(() => {
  return /^[a-z][a-z0-9_-]*$/.test(clientId.value) && clientId.value.length >= 2
})

const canSubmit = computed(() => {
  return isValidClientId.value && !loading.value
})

async function handleSubmit(): Promise<void> {
  if (!canSubmit.value) return

  loading.value = true
  error.value = null

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
    redirectUris: redirectUris.value.length > 0 ? redirectUris.value : undefined,
    webOrigins: webOrigins.value.length > 0 ? webOrigins.value : undefined
  }

  try {
    await clientsApi.create(realmName.value, request)
    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: `Client "${clientId.value}" created successfully`,
      life: 3000
    })
    router.push(`/realms/${realmName.value}`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to create client'
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
  router.push(`/realms/${realmName.value}`)
}
</script>

<template>
  <div class="create-client-page">
    <div class="page-header">
      <Button
        icon="pi pi-arrow-left"
        text
        rounded
        @click="handleCancel"
      />
      <div class="header-content">
        <h1>Create Client</h1>
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
            <h3>Basic Information</h3>

            <div class="form-field">
              <label for="clientId">Client ID *</label>
              <InputText
                id="clientId"
                v-model="clientId"
                placeholder="my-client"
                :invalid="clientId.length > 0 && !isValidClientId"
                class="w-full"
              />
              <small class="field-help">
                Must start with a letter and contain only lowercase letters, numbers, underscores, and hyphens.
              </small>
            </div>

            <div class="form-field">
              <label for="name">Display Name</label>
              <InputText
                id="name"
                v-model="name"
                placeholder="My Client"
                class="w-full"
              />
            </div>

            <div class="form-field">
              <label for="description">Description</label>
              <Textarea
                id="description"
                v-model="description"
                rows="3"
                class="w-full"
              />
            </div>
          </div>

          <div class="form-section">
            <h3>Client Type</h3>

            <div class="form-field checkbox-field">
              <Checkbox
                id="publicClient"
                v-model="publicClient"
                :binary="true"
              />
              <label for="publicClient" class="checkbox-label">
                <span>Public Client</span>
                <small>Public clients don't require a client secret. Use for browser-based apps.</small>
              </label>
            </div>
          </div>

          <div class="form-section">
            <h3>Authentication Flows</h3>

            <div class="form-field checkbox-field">
              <Checkbox
                id="standardFlowEnabled"
                v-model="standardFlowEnabled"
                :binary="true"
              />
              <label for="standardFlowEnabled" class="checkbox-label">
                <span>Standard Flow (Authorization Code)</span>
                <small>Enable for web applications with server-side rendering.</small>
              </label>
            </div>

            <div class="form-field checkbox-field">
              <Checkbox
                id="directAccessGrantsEnabled"
                v-model="directAccessGrantsEnabled"
                :binary="true"
              />
              <label for="directAccessGrantsEnabled" class="checkbox-label">
                <span>Direct Access Grants (Resource Owner Password)</span>
                <small>Allow direct username/password authentication. Use only for trusted clients.</small>
              </label>
            </div>

            <div class="form-field checkbox-field">
              <Checkbox
                id="serviceAccountsEnabled"
                v-model="serviceAccountsEnabled"
                :binary="true"
                :disabled="publicClient"
              />
              <label for="serviceAccountsEnabled" class="checkbox-label">
                <span>Service Accounts (Client Credentials)</span>
                <small>Allow client to authenticate as itself. Only for confidential clients.</small>
              </label>
            </div>
          </div>

          <div class="form-section">
            <h3>URLs</h3>

            <div class="form-field">
              <label for="rootUrl">Root URL</label>
              <InputText
                id="rootUrl"
                v-model="rootUrl"
                placeholder="https://myapp.example.com"
                class="w-full"
                @blur="handleRootUrlBlur"
              />
              <small class="field-help">
                Enter a URL like "localhost:3000" or "example.com" - scheme will be auto-added.
              </small>
            </div>

            <div class="form-field">
              <label for="baseUrl">Base URL</label>
              <InputText
                id="baseUrl"
                v-model="baseUrl"
                placeholder="/app"
                class="w-full"
              />
            </div>

            <div class="form-field">
              <label for="redirectUris">Valid Redirect URIs</label>
              <Chips
                id="redirectUris"
                v-model="redirectUris"
                separator=","
                class="w-full"
                @add="handleRedirectUriAdd"
              />
              <small class="field-help">
                Enter URLs like "localhost:5173" → auto-converts to "http://localhost:5173/*"
              </small>
            </div>

            <div class="form-field">
              <label for="webOrigins">Web Origins</label>
              <Chips
                id="webOrigins"
                v-model="webOrigins"
                separator=","
                class="w-full"
                @add="handleWebOriginAdd"
              />
              <small class="field-help">
                Enter URLs like "example.com" → auto-converts to "https://example.com". Use + for all redirect URIs.
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
              label="Create Client"
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
.create-client-page {
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
