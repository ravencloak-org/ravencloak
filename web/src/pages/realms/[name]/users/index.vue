<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { usersApi } from '@/api/users'
import Card from 'primevue/card'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import InputText from 'primevue/inputtext'
import Avatar from 'primevue/avatar'
import ProgressSpinner from 'primevue/progressspinner'
import Message from 'primevue/message'
import type { RealmUser } from '@/types'

defineOptions({
  name: 'RealmUsersPage'
})

const route = useRoute()
const router = useRouter()
const toast = useToast()
const confirm = useConfirm()

const realmName = computed(() => route.params.name as string)

const users = ref<RealmUser[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const searchQuery = ref('')

const filteredUsers = computed(() => {
  if (!searchQuery.value) return users.value
  const query = searchQuery.value.toLowerCase()
  return users.value.filter(user =>
    user.email.toLowerCase().includes(query) ||
    user.displayName?.toLowerCase().includes(query) ||
    user.firstName?.toLowerCase().includes(query) ||
    user.lastName?.toLowerCase().includes(query)
  )
})

onMounted(async () => {
  await loadUsers()
})

async function loadUsers(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    users.value = await usersApi.list(realmName.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load users'
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

function navigateToUser(user: RealmUser): void {
  router.push(`/realms/${realmName.value}/users/${user.id}`)
}

function navigateToCreate(): void {
  router.push(`/realms/${realmName.value}/users/create`)
}

function navigateBack(): void {
  router.push(`/realms/${realmName.value}`)
}

function handleDelete(user: RealmUser): void {
  confirm.require({
    message: `Are you sure you want to delete user "${user.email}"?`,
    header: 'Delete User',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await usersApi.delete(realmName.value, user.id)
        toast.add({
          severity: 'success',
          summary: 'Success',
          detail: 'User deleted successfully',
          life: 3000
        })
        await loadUsers()
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

function getInitials(user: RealmUser): string {
  if (user.firstName && user.lastName) {
    return `${user.firstName[0]}${user.lastName[0]}`.toUpperCase()
  }
  if (user.displayName) {
    return user.displayName.substring(0, 2).toUpperCase()
  }
  return user.email.substring(0, 2).toUpperCase()
}

function getDisplayName(user: RealmUser): string {
  if (user.displayName) return user.displayName
  if (user.firstName && user.lastName) return `${user.firstName} ${user.lastName}`
  if (user.firstName) return user.firstName
  return user.email
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
  return new Date(dateString).toLocaleDateString()
}
</script>

<template>
  <div class="users-page">
    <div class="page-header">
      <Button
        icon="pi pi-arrow-left"
        text
        rounded
        @click="navigateBack"
      />
      <div class="header-content">
        <h1>Users</h1>
        <p>{{ realmName }}</p>
      </div>
      <div class="header-actions">
        <Button
          label="Add User"
          icon="pi pi-plus"
          @click="navigateToCreate"
        />
      </div>
    </div>

    <Card class="users-card">
      <template #content>
        <div v-if="loading" class="loading-container">
          <ProgressSpinner />
        </div>

        <Message v-else-if="error" severity="error" :closable="false">
          {{ error }}
        </Message>

        <template v-else>
          <div class="toolbar">
            <span class="p-input-icon-left search-input">
              <i class="pi pi-search" />
              <InputText
                v-model="searchQuery"
                placeholder="Search users..."
              />
            </span>
            <span class="user-count">{{ filteredUsers.length }} users</span>
          </div>

          <DataTable
            :value="filteredUsers"
            :paginator="filteredUsers.length > 10"
            :rows="10"
            stripedRows
            class="users-table"
            @row-click="(e) => navigateToUser(e.data)"
          >
            <Column header="User" style="min-width: 250px">
              <template #body="{ data }">
                <div class="user-cell">
                  <Avatar
                    v-if="data.avatarUrl"
                    :image="data.avatarUrl"
                    shape="circle"
                    size="normal"
                  />
                  <Avatar
                    v-else
                    :label="getInitials(data)"
                    shape="circle"
                    size="normal"
                    class="user-avatar"
                  />
                  <div class="user-info">
                    <span class="user-name">{{ getDisplayName(data) }}</span>
                    <span class="user-email">{{ data.email }}</span>
                  </div>
                </div>
              </template>
            </Column>

            <Column header="Status" style="width: 120px">
              <template #body="{ data }">
                <Tag
                  :value="data.status"
                  :severity="getStatusSeverity(data.status)"
                />
              </template>
            </Column>

            <Column header="Authorized Clients" style="width: 200px">
              <template #body="{ data }">
                <div class="client-tags">
                  <Tag
                    v-for="client in data.authorizedClients.slice(0, 2)"
                    :key="client.clientId"
                    :value="client.clientName"
                    severity="info"
                    class="client-tag"
                  />
                  <Tag
                    v-if="data.authorizedClients.length > 2"
                    :value="`+${data.authorizedClients.length - 2}`"
                    severity="secondary"
                  />
                  <span v-if="data.authorizedClients.length === 0" class="no-clients">
                    No clients
                  </span>
                </div>
              </template>
            </Column>

            <Column header="Last Login" style="width: 120px">
              <template #body="{ data }">
                {{ formatDate(data.lastLoginAt) }}
              </template>
            </Column>

            <Column header="Actions" style="width: 100px">
              <template #body="{ data }">
                <div class="action-buttons">
                  <Button
                    icon="pi pi-pencil"
                    text
                    rounded
                    size="small"
                    @click.stop="navigateToUser(data)"
                  />
                  <Button
                    icon="pi pi-trash"
                    text
                    rounded
                    size="small"
                    severity="danger"
                    @click.stop="handleDelete(data)"
                  />
                </div>
              </template>
            </Column>

            <template #empty>
              <div class="empty-state">
                <i class="pi pi-users" />
                <p>No users found</p>
                <Button
                  label="Add First User"
                  icon="pi pi-plus"
                  @click="navigateToCreate"
                />
              </div>
            </template>
          </DataTable>
        </template>
      </template>
    </Card>
  </div>
</template>

<style scoped>
.users-page {
  max-width: 1200px;
  margin: 0 auto;
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

.header-actions {
  display: flex;
  gap: 0.5rem;
}

.loading-container {
  display: flex;
  justify-content: center;
  padding: 3rem;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.search-input {
  position: relative;
}

.search-input i {
  position: absolute;
  left: 0.75rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--p-text-muted-color);
}

.search-input :deep(input) {
  padding-left: 2.5rem;
  width: 300px;
}

.user-count {
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}

.users-table :deep(.p-datatable-tbody > tr) {
  cursor: pointer;
}

.users-table :deep(.p-datatable-tbody > tr:hover) {
  background-color: var(--p-surface-hover);
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.user-avatar {
  background-color: var(--p-primary-color);
  color: var(--p-primary-contrast-color);
}

.user-info {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-weight: 500;
}

.user-email {
  font-size: 0.875rem;
  color: var(--p-text-muted-color);
}

.client-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.client-tag {
  font-size: 0.75rem;
}

.no-clients {
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
  font-style: italic;
}

.action-buttons {
  display: flex;
  gap: 0.25rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 3rem;
  color: var(--p-text-muted-color);
}

.empty-state i {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.empty-state p {
  margin-bottom: 1rem;
}
</style>
