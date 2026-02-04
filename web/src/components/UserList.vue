<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usersApi } from '@/api/users'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Avatar from 'primevue/avatar'
import ProgressSpinner from 'primevue/progressspinner'
import type { RealmUser } from '@/types'

const props = defineProps<{
  realmName: string
}>()

const router = useRouter()
const users = ref<RealmUser[]>([])
const loading = ref(true)

onMounted(async () => {
  await loadUsers()
})

async function loadUsers(): Promise<void> {
  loading.value = true
  try {
    users.value = await usersApi.list(props.realmName)
  } catch {
    // Silent fail - component will show empty state
  } finally {
    loading.value = false
  }
}

function navigateToUsers(): void {
  router.push(`/realms/${props.realmName}/users`)
}

function navigateToCreateUser(): void {
  router.push(`/realms/${props.realmName}/users/create`)
}

function navigateToUser(userId: string): void {
  router.push(`/realms/${props.realmName}/users/${userId}`)
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
</script>

<template>
  <div class="user-list">
    <div class="list-header">
      <Button
        label="Add User"
        icon="pi pi-plus"
        size="small"
        @click="navigateToCreateUser"
      />
      <Button
        label="Manage Users"
        icon="pi pi-external-link"
        size="small"
        severity="secondary"
        @click="navigateToUsers"
      />
    </div>

    <div v-if="loading" class="loading-container">
      <ProgressSpinner />
    </div>

    <div v-else-if="users.length === 0" class="empty-state">
      <div class="empty-content">
        <i class="pi pi-user empty-icon" />
        <h3 class="empty-title">No users yet</h3>
        <p class="empty-description">
          Add users to this realm to manage their access to your applications.
        </p>
        <Button
          label="Add first user"
          icon="pi pi-plus"
          @click="navigateToCreateUser"
        />
      </div>
    </div>

    <template v-else>
      <DataTable
        :value="users.slice(0, 5)"
        stripedRows
        class="users-table"
        @row-click="(e) => navigateToUser(e.data.id)"
      >
        <Column header="User" style="min-width: 200px">
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

        <Column header="Status" style="width: 100px">
          <template #body="{ data }">
            <Tag
              :value="data.status"
              :severity="getStatusSeverity(data.status)"
            />
          </template>
        </Column>

        <Column header="Clients" style="width: 100px">
          <template #body="{ data }">
            {{ data.authorizedClients.length }}
          </template>
        </Column>
      </DataTable>

      <div v-if="users.length > 5" class="view-all">
        <Button
          :label="`View all ${users.length} users`"
          text
          @click="navigateToUsers"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.user-list {
  padding: 1rem 0;
}

.list-header {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.loading-container {
  display: flex;
  justify-content: center;
  padding: 2rem;
}

.empty-state {
  display: flex;
  justify-content: center;
  padding: 3rem;
}

.empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  max-width: 400px;
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
  color: var(--p-text-muted-color);
  opacity: 0.5;
}

.empty-title {
  margin: 0 0 0.5rem 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--p-text-color);
}

.empty-description {
  margin: 0 0 1.5rem 0;
  color: var(--p-text-muted-color);
  line-height: 1.5;
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

.view-all {
  display: flex;
  justify-content: center;
  padding-top: 1rem;
}
</style>
