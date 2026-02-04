<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { usersApi } from '@/api/users'
import { clientsApi } from '@/api'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Avatar from 'primevue/avatar'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import MultiSelect from 'primevue/multiselect'
import ProgressSpinner from 'primevue/progressspinner'
import Message from 'primevue/message'
import type { RealmUserDetail, Client } from '@/types'

defineOptions({
  name: 'UserDetailPage'
})

const route = useRoute()
const router = useRouter()
const toast = useToast()
const confirm = useConfirm()

const realmName = computed(() => route.params.name as string)
const userId = computed(() => route.params.userId as string)

const user = ref<RealmUserDetail | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

// Client assignment dialog
const showAssignDialog = ref(false)
const availableClients = ref<Client[]>([])
const selectedClients = ref<string[]>([])
const loadingClients = ref(false)
const assigning = ref(false)

onMounted(async () => {
  await loadUser()
})

async function loadUser(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    user.value = await usersApi.get(realmName.value, userId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load user'
  } finally {
    loading.value = false
  }
}

async function openAssignDialog(): Promise<void> {
  loadingClients.value = true
  showAssignDialog.value = true
  selectedClients.value = []

  try {
    const allClients = await clientsApi.list(realmName.value)
    // Filter out already assigned clients and only show public (frontend) clients
    const assignedIds = new Set(user.value?.authorizedClients.map(c => c.clientId) || [])
    availableClients.value = allClients.filter(c => c.publicClient && !assignedIds.has(c.id))
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load clients',
      life: 5000
    })
  } finally {
    loadingClients.value = false
  }
}

async function assignClients(): Promise<void> {
  if (selectedClients.value.length === 0) return

  assigning.value = true

  try {
    user.value = await usersApi.assignClients(realmName.value, userId.value, {
      clientIds: selectedClients.value
    })
    showAssignDialog.value = false
    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Clients assigned successfully',
      life: 3000
    })
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to assign clients',
      life: 5000
    })
  } finally {
    assigning.value = false
  }
}

function removeFromClient(clientId: string, clientName: string): void {
  confirm.require({
    message: `Remove user from client "${clientName}"?`,
    header: 'Remove Access',
    icon: 'pi pi-exclamation-triangle',
    accept: async () => {
      try {
        user.value = await usersApi.removeFromClient(realmName.value, userId.value, clientId)
        toast.add({
          severity: 'success',
          summary: 'Success',
          detail: 'User removed from client',
          life: 3000
        })
      } catch (err) {
        toast.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to remove user from client',
          life: 5000
        })
      }
    }
  })
}

function handleDelete(): void {
  confirm.require({
    message: `Are you sure you want to delete user "${user.value?.email}"?`,
    header: 'Delete User',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await usersApi.delete(realmName.value, userId.value)
        toast.add({
          severity: 'success',
          summary: 'Success',
          detail: 'User deleted successfully',
          life: 3000
        })
        router.push(`/realms/${realmName.value}/users`)
      } catch (err) {
        toast.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to delete user',
          life: 5000
        })
      }
    }
  })
}

function navigateBack(): void {
  router.push(`/realms/${realmName.value}/users`)
}

function getInitials(): string {
  if (!user.value) return ''
  if (user.value.firstName && user.value.lastName) {
    return `${user.value.firstName[0]}${user.value.lastName[0]}`.toUpperCase()
  }
  if (user.value.displayName) {
    return user.value.displayName.substring(0, 2).toUpperCase()
  }
  return user.value.email.substring(0, 2).toUpperCase()
}

function getDisplayName(): string {
  if (!user.value) return ''
  if (user.value.displayName) return user.value.displayName
  if (user.value.firstName && user.value.lastName) return `${user.value.firstName} ${user.value.lastName}`
  if (user.value.firstName) return user.value.firstName
  return user.value.email
}

function getStatusSeverity(status: string): "success" | "info" | "warn" | "danger" | "secondary" | "contrast" | undefined {
  switch (status) {
    case 'ACTIVE': return 'success'
    case 'INACTIVE': return 'secondary'
    case 'SUSPENDED': return 'danger'
    default: return 'info'
  }
}

function formatDate(dateString?: string): string {
  if (!dateString) return 'Never'
  return new Date(dateString).toLocaleString()
}
</script>

<template>
  <div class="user-detail-page">
    <div v-if="loading" class="loading-container">
      <ProgressSpinner />
    </div>

    <Message v-else-if="error" severity="error" :closable="false">
      {{ error }}
    </Message>

    <template v-else-if="user">
      <div class="page-header">
        <Button
          icon="pi pi-arrow-left"
          text
          rounded
          @click="navigateBack"
        />
        <div class="header-content">
          <div class="header-title">
            <Avatar
              v-if="user.avatarUrl"
              :image="user.avatarUrl"
              shape="circle"
              size="large"
            />
            <Avatar
              v-else
              :label="getInitials()"
              shape="circle"
              size="large"
              class="user-avatar"
            />
            <div>
              <h1>{{ getDisplayName() }}</h1>
              <p>{{ user.email }}</p>
            </div>
            <Tag
              :value="user.status"
              :severity="getStatusSeverity(user.status)"
            />
          </div>
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
          <template #title>Profile</template>
          <template #content>
            <div class="info-list">
              <div class="info-item">
                <span class="info-label">Email</span>
                <span class="info-value">{{ user.email }}</span>
              </div>
              <div class="info-item" v-if="user.firstName || user.lastName">
                <span class="info-label">Name</span>
                <span class="info-value">{{ user.firstName }} {{ user.lastName }}</span>
              </div>
              <div class="info-item" v-if="user.phone">
                <span class="info-label">Phone</span>
                <span class="info-value">{{ user.phone }}</span>
              </div>
              <div class="info-item" v-if="user.jobTitle">
                <span class="info-label">Job Title</span>
                <span class="info-value">{{ user.jobTitle }}</span>
              </div>
              <div class="info-item" v-if="user.department">
                <span class="info-label">Department</span>
                <span class="info-value">{{ user.department }}</span>
              </div>
              <div class="info-item" v-if="user.bio">
                <span class="info-label">Bio</span>
                <span class="info-value">{{ user.bio }}</span>
              </div>
            </div>
          </template>
        </Card>

        <Card class="info-card">
          <template #title>Activity</template>
          <template #content>
            <div class="info-list">
              <div class="info-item">
                <span class="info-label">Keycloak ID</span>
                <code v-if="user.keycloakUserId">{{ user.keycloakUserId }}</code>
                <span v-else class="empty-value">Not linked (user hasn't logged in yet)</span>
              </div>
              <div class="info-item">
                <span class="info-label">Last Login</span>
                <span class="info-value">{{ formatDate(user.lastLoginAt) }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">Created</span>
                <span class="info-value">{{ formatDate(user.createdAt) }}</span>
              </div>
              <div class="info-item" v-if="user.updatedAt">
                <span class="info-label">Last Updated</span>
                <span class="info-value">{{ formatDate(user.updatedAt) }}</span>
              </div>
            </div>
          </template>
        </Card>

        <Card class="info-card clients-card">
          <template #title>
            <div class="card-title-row">
              <span>Authorized Clients</span>
              <Button
                label="Add Client"
                icon="pi pi-plus"
                size="small"
                text
                @click="openAssignDialog"
              />
            </div>
          </template>
          <template #content>
            <DataTable
              v-if="user.authorizedClients.length > 0"
              :value="user.authorizedClients"
              class="clients-table"
            >
              <Column header="Client" style="min-width: 200px">
                <template #body="{ data }">
                  <div class="client-cell">
                    <span class="client-name">{{ data.clientDisplayName || data.clientIdName }}</span>
                    <code class="client-id">{{ data.clientIdName }}</code>
                  </div>
                </template>
              </Column>
              <Column header="Type" style="width: 120px">
                <template #body="{ data }">
                  <Tag
                    :value="data.publicClient ? 'Public' : 'Confidential'"
                    :severity="data.publicClient ? 'info' : 'warn'"
                  />
                </template>
              </Column>
              <Column header="Assigned" style="width: 150px">
                <template #body="{ data }">
                  {{ formatDate(data.assignedAt) }}
                </template>
              </Column>
              <Column header="" style="width: 60px">
                <template #body="{ data }">
                  <Button
                    icon="pi pi-times"
                    text
                    rounded
                    size="small"
                    severity="danger"
                    @click="removeFromClient(data.clientId, data.clientDisplayName || data.clientIdName)"
                  />
                </template>
              </Column>
            </DataTable>
            <div v-else class="empty-clients">
              <i class="pi pi-inbox" />
              <p>No clients assigned</p>
              <small>This user has realm-wide access or hasn't been assigned to specific clients.</small>
            </div>
          </template>
        </Card>
      </div>
    </template>

    <!-- Assign Clients Dialog -->
    <Dialog
      v-model:visible="showAssignDialog"
      header="Assign Clients"
      :style="{ width: '450px' }"
      modal
    >
      <div class="dialog-content">
        <p>Select clients to grant access to this user:</p>
        <MultiSelect
          v-model="selectedClients"
          :options="availableClients"
          optionLabel="clientId"
          optionValue="id"
          placeholder="Select clients"
          :loading="loadingClients"
          class="w-full"
          display="chip"
        >
          <template #option="{ option }">
            <div class="client-option">
              <span>{{ option.name || option.clientId }}</span>
            </div>
          </template>
        </MultiSelect>
      </div>
      <template #footer>
        <Button
          label="Cancel"
          severity="secondary"
          @click="showAssignDialog = false"
        />
        <Button
          label="Assign"
          icon="pi pi-check"
          :loading="assigning"
          :disabled="selectedClients.length === 0"
          @click="assignClients"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.user-detail-page {
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
  gap: 1rem;
}

.user-avatar {
  background-color: var(--p-primary-color);
  color: var(--p-primary-contrast-color);
}

.header-title h1 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
}

.header-title p {
  margin: 0.25rem 0 0;
  color: var(--p-text-muted-color);
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

.clients-card {
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

.client-cell {
  display: flex;
  flex-direction: column;
}

.client-name {
  font-weight: 500;
}

.client-id {
  font-size: 0.75rem;
  padding: 0.125rem 0.25rem;
}

.empty-clients {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 2rem;
  color: var(--p-text-muted-color);
  text-align: center;
}

.empty-clients i {
  font-size: 2rem;
  margin-bottom: 0.5rem;
}

.empty-clients p {
  margin: 0 0 0.25rem;
}

.empty-clients small {
  font-size: 0.875rem;
}

.dialog-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.dialog-content p {
  margin: 0;
  color: var(--p-text-muted-color);
}

.w-full {
  width: 100%;
}
</style>
