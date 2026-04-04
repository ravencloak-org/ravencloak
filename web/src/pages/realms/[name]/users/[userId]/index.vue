<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usersApi } from '@/api/users'
import { useRealmStore } from '@/stores/realm'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import type { RealmUserDetail } from '@/types'

defineOptions({
  name: 'UserDetailPage',
})

const route = useRoute()
const router = useRouter()
const realmStore = useRealmStore()
const toast = useToast()
const { confirm } = useConfirm()

const realmName = computed(() => route.params.name as string)
const userId = computed(() => route.params.userId as string)

const user = ref<RealmUserDetail | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  // Ensure realm is loaded for sidebar
  if (!realmStore.currentRealm || realmStore.currentRealm.realmName !== realmName.value) {
    realmStore.fetchRealm(realmName.value).catch(() => {})
  }
  await loadUser()
})

async function loadUser(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    user.value = await usersApi.get(realmName.value, userId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load user'
    toast.error('Error', error.value)
  } finally {
    loading.value = false
  }
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

function statusVariant(status: string): 'success' | 'danger' | 'neutral' | 'warning' {
  switch (status) {
    case 'ACTIVE':
      return 'success'
    case 'SUSPENDED':
      return 'danger'
    case 'INACTIVE':
      return 'neutral'
    default:
      return 'warning'
  }
}

async function removeFromClient(clientId: string, clientName: string): Promise<void> {
  const confirmed = await confirm({
    title: 'Remove Client Access',
    message: `Remove user from client "${clientName}"?`,
    confirmLabel: 'Remove',
    destructive: true,
  })
  if (!confirmed) return

  try {
    user.value = await usersApi.removeFromClient(realmName.value, userId.value, clientId)
    toast.success('Removed', 'User removed from client')
  } catch {
    toast.error('Error', 'Failed to remove user from client')
  }
}

async function handleDelete(): Promise<void> {
  const confirmed = await confirm({
    title: 'Delete User',
    message: `Are you sure you want to delete "${user.value?.email}"? This action cannot be undone.`,
    confirmLabel: 'Delete',
    destructive: true,
  })
  if (!confirmed) return

  try {
    await usersApi.delete(realmName.value, userId.value)
    toast.success('Deleted', 'User deleted successfully')
    router.push(`/realms/${realmName.value}/users`)
  } catch {
    toast.error('Error', 'Failed to delete user')
  }
}

function formatDate(dateString?: string): string {
  if (!dateString) return 'Never'
  return new Date(dateString).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const profileFields = computed(() => {
  if (!user.value) return []
  const fields: { label: string; value: string }[] = []

  fields.push({ label: 'Email', value: user.value.email })
  if (user.value.firstName || user.value.lastName) {
    fields.push({ label: 'Name', value: [user.value.firstName, user.value.lastName].filter(Boolean).join(' ') })
  }
  if (user.value.phone) {
    fields.push({ label: 'Phone', value: user.value.phone })
  }
  if (user.value.jobTitle) {
    fields.push({ label: 'Job Title', value: user.value.jobTitle })
  }
  if (user.value.department) {
    fields.push({ label: 'Department', value: user.value.department })
  }
  if (user.value.bio) {
    fields.push({ label: 'Bio', value: user.value.bio })
  }
  if (user.value.keycloakUserId) {
    fields.push({ label: 'Keycloak ID', value: user.value.keycloakUserId })
  }
  fields.push({ label: 'Last Login', value: formatDate(user.value.lastLoginAt) })
  fields.push({ label: 'Created', value: formatDate(user.value.createdAt) })
  if (user.value.updatedAt) {
    fields.push({ label: 'Updated', value: formatDate(user.value.updatedAt) })
  }

  return fields
})
</script>

<template>
  <SidebarLayout>
    <div class="mx-auto max-w-4xl">
      <!-- Loading -->
      <div v-if="loading" class="flex items-center justify-center py-20">
        <svg
          class="h-8 w-8 animate-spin text-zinc-400"
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
        </svg>
      </div>

      <!-- Error -->
      <div
        v-else-if="error"
        class="rounded-lg bg-red-50 p-4 text-sm text-red-700 ring-1 ring-inset ring-red-600/20 dark:bg-red-500/10 dark:text-red-400 dark:ring-red-500/20"
      >
        {{ error }}
      </div>

      <template v-else-if="user">
        <!-- Back link -->
        <button
          class="mb-4 inline-flex items-center gap-1 text-sm text-zinc-500 transition-colors hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100"
          @click="router.push(`/realms/${realmName}/users`)"
        >
          <svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M17 10a.75.75 0 01-.75.75H5.612l4.158 3.96a.75.75 0 11-1.04 1.08l-5.5-5.25a.75.75 0 010-1.08l5.5-5.25a.75.75 0 111.04 1.08L5.612 9.25H16.25A.75.75 0 0117 10z" clip-rule="evenodd" />
          </svg>
          Users
        </button>

        <!-- Heading -->
        <div class="flex items-start justify-between">
          <div class="flex items-center gap-4">
            <!-- Avatar -->
            <div
              class="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-indigo-600 text-lg font-semibold text-white"
            >
              {{ getInitials() }}
            </div>
            <div>
              <div class="flex items-center gap-3">
                <h1 class="text-2xl font-semibold text-zinc-900 dark:text-zinc-100">
                  {{ getDisplayName() }}
                </h1>
                <AppBadge :variant="statusVariant(user.status)" dot>
                  {{ user.status }}
                </AppBadge>
              </div>
              <p class="mt-0.5 text-sm text-zinc-500 dark:text-zinc-400">
                {{ user.email }}
              </p>
            </div>
          </div>
          <AppButton variant="danger" @click="handleDelete">
            Delete
          </AppButton>
        </div>

        <!-- Profile details -->
        <div class="mt-8">
          <h2 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">Profile</h2>
          <div class="mt-3 rounded-lg bg-white ring-1 ring-zinc-200 dark:bg-zinc-900 dark:ring-zinc-800">
            <dl class="divide-y divide-zinc-100 dark:divide-zinc-800">
              <div
                v-for="field in profileFields"
                :key="field.label"
                class="px-5 py-4 sm:grid sm:grid-cols-3 sm:gap-4"
              >
                <dt class="text-sm font-medium text-zinc-500 dark:text-zinc-400">
                  {{ field.label }}
                </dt>
                <dd class="mt-1 text-sm text-zinc-900 dark:text-zinc-100 sm:col-span-2 sm:mt-0">
                  <code
                    v-if="field.label === 'Keycloak ID'"
                    class="rounded bg-zinc-100 px-1.5 py-0.5 font-mono text-xs dark:bg-zinc-800 dark:text-zinc-300"
                  >
                    {{ field.value }}
                  </code>
                  <template v-else>{{ field.value }}</template>
                </dd>
              </div>
            </dl>
          </div>
        </div>

        <!-- Authorized Clients -->
        <div class="mt-8">
          <h2 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">Authorized Clients</h2>

          <!-- No clients -->
          <div
            v-if="user.authorizedClients.length === 0"
            class="mt-3 rounded-lg bg-white px-5 py-8 text-center ring-1 ring-zinc-200 dark:bg-zinc-900 dark:ring-zinc-800"
          >
            <p class="text-sm text-zinc-500 dark:text-zinc-400">No clients assigned</p>
            <p class="mt-1 text-xs text-zinc-400 dark:text-zinc-500">
              This user has realm-wide access or hasn't been assigned to specific clients.
            </p>
          </div>

          <!-- Client list -->
          <ul v-else class="mt-3 divide-y divide-zinc-100 rounded-lg bg-white ring-1 ring-zinc-200 dark:divide-zinc-800 dark:bg-zinc-900 dark:ring-zinc-800">
            <li
              v-for="ac in user.authorizedClients"
              :key="ac.clientId"
              class="flex items-center justify-between px-5 py-3"
            >
              <div class="min-w-0">
                <p class="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                  {{ ac.clientDisplayName || ac.clientIdName }}
                </p>
                <p class="mt-0.5 font-mono text-xs text-zinc-500 dark:text-zinc-400">
                  {{ ac.clientIdName }}
                </p>
              </div>
              <div class="flex items-center gap-3">
                <AppBadge :variant="ac.publicClient ? 'info' : 'warning'">
                  {{ ac.publicClient ? 'Public' : 'Confidential' }}
                </AppBadge>
                <button
                  class="rounded p-1 text-zinc-400 transition-colors hover:bg-zinc-100 hover:text-red-500 dark:hover:bg-zinc-800 dark:hover:text-red-400"
                  title="Remove"
                  @click="removeFromClient(ac.clientId, ac.clientDisplayName || ac.clientIdName)"
                >
                  <svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                    <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
                  </svg>
                </button>
              </div>
            </li>
          </ul>
        </div>

        <!-- Role Assignments placeholder -->
        <div class="mt-8">
          <h2 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">Role Assignments</h2>
          <div class="mt-3 rounded-lg bg-white px-5 py-8 text-center ring-1 ring-zinc-200 dark:bg-zinc-900 dark:ring-zinc-800">
            <p class="text-sm text-zinc-500 dark:text-zinc-400">
              Role assignments are managed via Keycloak groups and realm roles.
            </p>
          </div>
        </div>
      </template>
    </div>
  </SidebarLayout>
</template>
