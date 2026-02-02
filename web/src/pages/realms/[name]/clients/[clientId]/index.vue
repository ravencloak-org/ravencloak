<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { clientsApi } from '@/api'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import ProgressSpinner from 'primevue/progressspinner'
import Message from 'primevue/message'
import type { ClientDetailResponse } from '@/types'

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
              <div class="info-item" v-if="client.rootUrl">
                <span class="info-label">Root URL</span>
                <code>{{ client.rootUrl }}</code>
              </div>
              <div class="info-item" v-if="client.baseUrl">
                <span class="info-label">Base URL</span>
                <code>{{ client.baseUrl }}</code>
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

        <Card class="info-card" v-if="client.redirectUris?.length">
          <template #title>Redirect URIs</template>
          <template #content>
            <ul class="uri-list">
              <li v-for="uri in client.redirectUris" :key="uri">
                <code>{{ uri }}</code>
              </li>
            </ul>
          </template>
        </Card>

        <Card class="info-card" v-if="client.webOrigins?.length">
          <template #title>Web Origins</template>
          <template #content>
            <ul class="uri-list">
              <li v-for="origin in client.webOrigins" :key="origin">
                <code>{{ origin }}</code>
              </li>
            </ul>
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
</style>
