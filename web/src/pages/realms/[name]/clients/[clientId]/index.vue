<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { clientsApi } from '@/api'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import InputText from 'primevue/inputtext'
import Chips from 'primevue/chips'
import ProgressSpinner from 'primevue/progressspinner'
import Message from 'primevue/message'
import type { ClientDetailResponse, UpdateClientRequest } from '@/types'

defineOptions({
  name: 'ClientDetailPage'
})

const route = useRoute()
const router = useRouter()
const toast = useToast()
const confirm = useConfirm()

const realmName = computed(() => route.params.name as string)
const clientId = computed(() => route.params.clientId as string)

const client = ref<ClientDetailResponse | null>(null)
const clientSecret = ref<string | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

// Edit mode state
const editingUrls = ref(false)
const savingUrls = ref(false)
const editRootUrl = ref('')
const editBaseUrl = ref('')
const editRedirectUris = ref<string[]>([])
const editWebOrigins = ref<string[]>([])

onMounted(async () => {
  await loadClient()
})

async function loadClient(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    client.value = await clientsApi.get(realmName.value, clientId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load client'
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

function startEditingUrls(): void {
  if (!client.value) return
  editRootUrl.value = client.value.rootUrl || ''
  editBaseUrl.value = client.value.baseUrl || ''
  editRedirectUris.value = [...(client.value.redirectUris || [])]
  editWebOrigins.value = [...(client.value.webOrigins || [])]
  editingUrls.value = true
}

function cancelEditingUrls(): void {
  editingUrls.value = false
  editRootUrl.value = ''
  editBaseUrl.value = ''
  editRedirectUris.value = []
  editWebOrigins.value = []
}

function isValidUrl(url: string): boolean {
  // Allow wildcards for Keycloak patterns
  if (url.includes('*')) return true
  try {
    new URL(url)
    return true
  } catch {
    return false
  }
}

function validateUrls(): string | null {
  if (editRootUrl.value && !isValidUrl(editRootUrl.value)) {
    return 'Root URL is not a valid URL'
  }
  if (editBaseUrl.value && !isValidUrl(editBaseUrl.value)) {
    return 'Base URL is not a valid URL'
  }
  for (const uri of editRedirectUris.value) {
    if (!isValidUrl(uri)) {
      return `Invalid redirect URI: ${uri}`
    }
  }
  for (const origin of editWebOrigins.value) {
    if (!isValidUrl(origin)) {
      return `Invalid web origin: ${origin}`
    }
  }
  return null
}

async function saveUrls(): Promise<void> {
  const validationError = validateUrls()
  if (validationError) {
    toast.add({
      severity: 'error',
      summary: 'Validation Error',
      detail: validationError,
      life: 5000
    })
    return
  }

  savingUrls.value = true

  // Store original values for rollback
  const originalClient = { ...client.value }

  // Optimistic update
  if (client.value) {
    client.value.rootUrl = editRootUrl.value || undefined
    client.value.baseUrl = editBaseUrl.value || undefined
    client.value.redirectUris = [...editRedirectUris.value]
    client.value.webOrigins = [...editWebOrigins.value]
  }

  try {
    const updateRequest: UpdateClientRequest = {
      rootUrl: editRootUrl.value || undefined,
      baseUrl: editBaseUrl.value || undefined,
      redirectUris: editRedirectUris.value,
      webOrigins: editWebOrigins.value
    }

    await clientsApi.update(realmName.value, clientId.value, updateRequest)

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'URLs updated successfully',
      life: 3000
    })
    editingUrls.value = false
  } catch (err) {
    // Rollback on error
    client.value = originalClient as ClientDetailResponse
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: err instanceof Error ? err.message : 'Failed to update URLs',
      life: 5000
    })
  } finally {
    savingUrls.value = false
  }
}

async function fetchSecret(): Promise<void> {
  try {
    const response = await clientsApi.getSecret(realmName.value, clientId.value)
    clientSecret.value = response.secret
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to fetch client secret',
      life: 5000
    })
  }
}

async function regenerateSecret(): Promise<void> {
  confirm.require({
    message: 'Are you sure you want to regenerate the client secret? This will invalidate the current secret.',
    header: 'Regenerate Secret',
    icon: 'pi pi-exclamation-triangle',
    accept: async () => {
      try {
        const response = await clientsApi.regenerateSecret(realmName.value, clientId.value)
        clientSecret.value = response.secret
        toast.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Client secret regenerated',
          life: 3000
        })
      } catch (err) {
        toast.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to regenerate secret',
          life: 5000
        })
      }
    }
  })
}

function handleDelete(): void {
  confirm.require({
    message: `Are you sure you want to delete client "${clientId.value}"? This action cannot be undone.`,
    header: 'Delete Client',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await clientsApi.delete(realmName.value, clientId.value)
        toast.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Client deleted successfully',
          life: 3000
        })
        router.push(`/realms/${realmName.value}`)
      } catch (err) {
        toast.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to delete client',
          life: 5000
        })
      }
    }
  })
}

function navigateBack(): void {
  router.push(`/realms/${realmName.value}`)
}

function copyToClipboard(text: string): void {
  navigator.clipboard.writeText(text)
  toast.add({
    severity: 'success',
    summary: 'Copied',
    detail: 'Copied to clipboard',
    life: 2000
  })
}
</script>

<template>
  <div class="client-detail-page">
    <div v-if="loading" class="loading-container">
      <ProgressSpinner />
    </div>

    <Message v-else-if="error" severity="error" :closable="false">
      {{ error }}
    </Message>

    <template v-else-if="client">
      <div class="page-header">
        <Button
          icon="pi pi-arrow-left"
          text
          rounded
          @click="navigateBack"
        />
        <div class="header-content">
          <div class="header-title">
            <h1>{{ client.name || client.clientId }}</h1>
            <Tag
              :value="client.enabled ? 'Enabled' : 'Disabled'"
              :severity="client.enabled ? 'success' : 'danger'"
            />
            <Tag
              :value="client.publicClient ? 'Public' : 'Confidential'"
              :severity="client.publicClient ? 'info' : 'warn'"
            />
          </div>
          <p>{{ client.clientId }}</p>
        </div>
        <div class="header-actions">
          <Button
            label="Delete"
            icon="pi pi-trash"
            severity="danger"
            outlined
            @click="handleDelete"
          />
        </div>
      </div>

      <div class="content-grid">
        <Card class="info-card">
          <template #title>Client Information</template>
          <template #content>
            <div class="info-list">
              <div class="info-item">
                <span class="info-label">Client ID</span>
                <div class="info-value-row">
                  <code>{{ client.clientId }}</code>
                  <Button
                    icon="pi pi-copy"
                    text
                    rounded
                    size="small"
                    @click="copyToClipboard(client.clientId)"
                  />
                </div>
              </div>
              <div class="info-item" v-if="client.description">
                <span class="info-label">Description</span>
                <span class="info-value">{{ client.description }}</span>
              </div>
            </div>
          </template>
        </Card>

        <Card class="info-card" v-if="!client.publicClient">
          <template #title>Client Secret</template>
          <template #content>
            <div v-if="clientSecret" class="secret-display">
              <code>{{ clientSecret }}</code>
              <div class="secret-actions">
                <Button
                  icon="pi pi-copy"
                  text
                  rounded
                  size="small"
                  @click="copyToClipboard(clientSecret)"
                />
                <Button
                  label="Regenerate"
                  icon="pi pi-refresh"
                  size="small"
                  severity="secondary"
                  @click="regenerateSecret"
                />
              </div>
            </div>
            <div v-else class="secret-actions">
              <Button
                label="Show Secret"
                icon="pi pi-eye"
                size="small"
                @click="fetchSecret"
              />
            </div>
          </template>
        </Card>

        <Card class="info-card">
          <template #title>Authentication Flows</template>
          <template #content>
            <div class="flow-list">
              <div class="flow-item">
                <span>Standard Flow</span>
                <Tag
                  :value="client.standardFlowEnabled ? 'Enabled' : 'Disabled'"
                  :severity="client.standardFlowEnabled ? 'success' : 'secondary'"
                />
              </div>
              <div class="flow-item">
                <span>Direct Access Grants</span>
                <Tag
                  :value="client.directAccessGrantsEnabled ? 'Enabled' : 'Disabled'"
                  :severity="client.directAccessGrantsEnabled ? 'success' : 'secondary'"
                />
              </div>
              <div class="flow-item">
                <span>Service Accounts</span>
                <Tag
                  :value="client.serviceAccountsEnabled ? 'Enabled' : 'Disabled'"
                  :severity="client.serviceAccountsEnabled ? 'success' : 'secondary'"
                />
              </div>
            </div>
          </template>
        </Card>

        <Card class="info-card urls-card">
          <template #title>
            <div class="card-title-row">
              <span>URLs & Redirect URIs</span>
              <Button
                v-if="!editingUrls"
                label="Edit"
                icon="pi pi-pencil"
                size="small"
                text
                @click="startEditingUrls"
              />
            </div>
          </template>
          <template #content>
            <template v-if="editingUrls">
              <div class="edit-form">
                <div class="form-field">
                  <label for="rootUrl">Root URL</label>
                  <InputText
                    id="rootUrl"
                    v-model="editRootUrl"
                    placeholder="https://example.com"
                    class="w-full"
                  />
                </div>

                <div class="form-field">
                  <label for="baseUrl">Base URL</label>
                  <InputText
                    id="baseUrl"
                    v-model="editBaseUrl"
                    placeholder="/app"
                    class="w-full"
                  />
                </div>

                <div class="form-field">
                  <label for="redirectUris">Redirect URIs</label>
                  <Chips
                    id="redirectUris"
                    v-model="editRedirectUris"
                    placeholder="Press Enter to add URI"
                    class="w-full"
                    separator=","
                  />
                  <small class="field-help">Enter URIs and press Enter. Wildcards (*) are allowed.</small>
                </div>

                <div class="form-field">
                  <label for="webOrigins">Web Origins</label>
                  <Chips
                    id="webOrigins"
                    v-model="editWebOrigins"
                    placeholder="Press Enter to add origin"
                    class="w-full"
                    separator=","
                  />
                  <small class="field-help">Enter origins and press Enter. Use + to allow all redirect URI origins.</small>
                </div>

                <div class="edit-actions">
                  <Button
                    label="Cancel"
                    severity="secondary"
                    outlined
                    size="small"
                    @click="cancelEditingUrls"
                    :disabled="savingUrls"
                  />
                  <Button
                    label="Save"
                    icon="pi pi-check"
                    size="small"
                    @click="saveUrls"
                    :loading="savingUrls"
                  />
                </div>
              </div>
            </template>
            <template v-else>
              <div class="info-list">
                <div class="info-item">
                  <span class="info-label">Root URL</span>
                  <code v-if="client.rootUrl">{{ client.rootUrl }}</code>
                  <span v-else class="empty-value">Not set</span>
                </div>
                <div class="info-item">
                  <span class="info-label">Base URL</span>
                  <code v-if="client.baseUrl">{{ client.baseUrl }}</code>
                  <span v-else class="empty-value">Not set</span>
                </div>
                <div class="info-item">
                  <span class="info-label">Redirect URIs</span>
                  <ul v-if="client.redirectUris?.length" class="uri-list">
                    <li v-for="uri in client.redirectUris" :key="uri">
                      <code>{{ uri }}</code>
                    </li>
                  </ul>
                  <span v-else class="empty-value">None configured</span>
                </div>
                <div class="info-item">
                  <span class="info-label">Web Origins</span>
                  <ul v-if="client.webOrigins?.length" class="uri-list">
                    <li v-for="origin in client.webOrigins" :key="origin">
                      <code>{{ origin }}</code>
                    </li>
                  </ul>
                  <span v-else class="empty-value">None configured</span>
                </div>
              </div>
            </template>
          </template>
        </Card>
      </div>
    </template>
  </div>
</template>

<style scoped>
.client-detail-page {
  max-width: 1200px;
  margin: 0 auto;
}

.loading-container {
  display: flex;
  justify-content: center;
  padding: 4rem;
}

.page-header {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  margin-bottom: 1.5rem;
}

.header-content {
  flex: 1;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.header-content h1 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
}

.header-content p {
  margin: 0.25rem 0 0;
  color: var(--p-text-muted-color);
  font-family: monospace;
}

.header-actions {
  display: flex;
  gap: 0.5rem;
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
  gap: 1.5rem;
}

.info-card {
  background-color: var(--p-surface-card);
}

.urls-card {
  grid-column: 1 / -1;
}

.card-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.info-label {
  font-size: 0.875rem;
  color: var(--p-text-muted-color);
}

.info-value {
  color: var(--p-text-color);
}

.info-value-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.empty-value {
  color: var(--p-text-muted-color);
  font-style: italic;
}

code {
  background-color: var(--p-surface-100);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-family: monospace;
  font-size: 0.875rem;
}

.secret-display {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.secret-display code {
  word-break: break-all;
}

.secret-actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.flow-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.flow-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.uri-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.uri-list code {
  display: block;
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-field label {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--p-text-color);
}

.field-help {
  color: var(--p-text-muted-color);
  font-size: 0.75rem;
}

.w-full {
  width: 100%;
}

.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  padding-top: 0.5rem;
}
</style>
