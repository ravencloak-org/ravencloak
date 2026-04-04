<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usersApi } from '@/api/users'
import { useRealmStore } from '@/stores/realm'
import { useToast } from '@/composables/useToast'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import type { RealmUser } from '@/types'

defineOptions({
  name: 'RealmUsersPage',
})

const route = useRoute()
const router = useRouter()
const realmStore = useRealmStore()
const toast = useToast()

const realmName = computed(() => route.params.name as string)

const users = ref<RealmUser[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const searchQuery = ref('')

// Pagination
const currentPage = ref(1)
const pageSize = 10

onMounted(async () => {
  // Ensure realm is loaded for sidebar
  if (!realmStore.currentRealm || realmStore.currentRealm.realmName !== realmName.value) {
    realmStore.fetchRealm(realmName.value).catch(() => {})
  }
  await loadUsers()
})

async function loadUsers(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    users.value = await usersApi.list(realmName.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load users'
    toast.error('Error', error.value)
  } finally {
    loading.value = false
  }
}

const filteredUsers = computed(() => {
  if (!searchQuery.value) return users.value
  const query = searchQuery.value.toLowerCase()
  return users.value.filter(
    (user) =>
      user.email.toLowerCase().includes(query) ||
      user.displayName?.toLowerCase().includes(query) ||
      user.firstName?.toLowerCase().includes(query) ||
      user.lastName?.toLowerCase().includes(query),
  )
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredUsers.value.length / pageSize)))
const paginatedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredUsers.value.slice(start, start + pageSize)
})

function navigateToUser(user: RealmUser): void {
  router.push(`/realms/${realmName.value}/users/${user.id}`)
}

function navigateToCreate(): void {
  router.push(`/realms/${realmName.value}/users/create`)
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

function relativeTime(dateString?: string): string {
  if (!dateString) return 'Never'
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffSec = Math.floor(diffMs / 1000)
  const diffMin = Math.floor(diffSec / 60)
  const diffHr = Math.floor(diffMin / 60)
  const diffDay = Math.floor(diffHr / 24)

  if (diffSec < 60) return 'just now'
  if (diffMin < 60) return `${diffMin}m ago`
  if (diffHr < 24) return `${diffHr}h ago`
  if (diffDay < 30) return `${diffDay}d ago`
  return date.toLocaleDateString()
}
</script>

<template>
  <SidebarLayout>
    <div class="mx-auto max-w-7xl">
      <!-- Page header -->
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-semibold text-zinc-900 dark:text-zinc-100">
            Users
          </h1>
          <p class="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Manage users in {{ realmName }}
          </p>
        </div>
        <AppButton @click="navigateToCreate">
          Add User
        </AppButton>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="mt-12 flex items-center justify-center">
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
        class="mt-8 rounded-lg bg-red-50 p-4 text-sm text-red-700 ring-1 ring-inset ring-red-600/20 dark:bg-red-500/10 dark:text-red-400 dark:ring-red-500/20"
      >
        {{ error }}
      </div>

      <template v-else>
        <!-- Search bar -->
        <div class="mt-6 flex items-center justify-between gap-4">
          <div class="relative max-w-sm flex-1">
            <svg
              class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="2"
              stroke="currentColor"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
            </svg>
            <input
              v-model="searchQuery"
              type="text"
              placeholder="Search users..."
              class="w-full rounded-lg border-0 bg-white py-2 pl-10 pr-3 text-sm text-zinc-900 ring-1 ring-inset ring-zinc-300 placeholder:text-zinc-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 dark:bg-zinc-900 dark:text-zinc-100 dark:ring-zinc-700 dark:placeholder:text-zinc-500 dark:focus:ring-indigo-500"
              @input="currentPage = 1"
            />
          </div>
          <span class="text-sm text-zinc-500 dark:text-zinc-400">
            {{ filteredUsers.length }} {{ filteredUsers.length === 1 ? 'user' : 'users' }}
          </span>
        </div>

        <!-- Empty state -->
        <div
          v-if="filteredUsers.length === 0 && !searchQuery"
          class="mt-8 flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-zinc-300 px-6 py-16 text-center dark:border-zinc-700"
        >
          <svg class="h-12 w-12 text-zinc-400" fill="none" viewBox="0 0 24 24" stroke-width="1" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
          </svg>
          <h3 class="mt-4 text-sm font-semibold text-zinc-900 dark:text-zinc-100">No users yet</h3>
          <p class="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Get started by adding a user.
          </p>
          <div class="mt-6">
            <AppButton @click="navigateToCreate">
              Add User
            </AppButton>
          </div>
        </div>

        <!-- No search results -->
        <div
          v-else-if="filteredUsers.length === 0 && searchQuery"
          class="mt-8 py-12 text-center text-sm text-zinc-500 dark:text-zinc-400"
        >
          No users matching "{{ searchQuery }}"
        </div>

        <!-- Users table -->
        <div v-else class="mt-4 overflow-hidden rounded-lg bg-white ring-1 ring-zinc-200 dark:bg-zinc-900 dark:ring-zinc-800">
          <table class="min-w-full divide-y divide-zinc-200 dark:divide-zinc-800">
            <thead>
              <tr>
                <th class="px-5 py-3 text-left text-xs font-medium uppercase tracking-wide text-zinc-500 dark:text-zinc-400">
                  User
                </th>
                <th class="hidden px-5 py-3 text-left text-xs font-medium uppercase tracking-wide text-zinc-500 dark:text-zinc-400 sm:table-cell">
                  Status
                </th>
                <th class="hidden px-5 py-3 text-left text-xs font-medium uppercase tracking-wide text-zinc-500 dark:text-zinc-400 md:table-cell">
                  Last Login
                </th>
              </tr>
            </thead>
            <tbody class="divide-y divide-zinc-100 dark:divide-zinc-800">
              <tr
                v-for="user in paginatedUsers"
                :key="user.id"
                class="cursor-pointer transition-colors hover:bg-zinc-50 dark:hover:bg-zinc-800/50"
                @click="navigateToUser(user)"
              >
                <td class="px-5 py-3">
                  <div class="flex items-center gap-3">
                    <!-- Avatar (initials) -->
                    <div
                      class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-indigo-600 text-xs font-medium text-white"
                    >
                      {{ getInitials(user) }}
                    </div>
                    <div class="min-w-0">
                      <p class="truncate text-sm font-medium text-zinc-900 dark:text-zinc-100">
                        {{ getDisplayName(user) }}
                      </p>
                      <p class="truncate text-xs text-zinc-500 dark:text-zinc-400">
                        {{ user.email }}
                      </p>
                    </div>
                  </div>
                </td>
                <td class="hidden px-5 py-3 sm:table-cell">
                  <AppBadge :variant="statusVariant(user.status)" dot>
                    {{ user.status }}
                  </AppBadge>
                </td>
                <td class="hidden px-5 py-3 text-sm text-zinc-500 dark:text-zinc-400 md:table-cell">
                  {{ relativeTime(user.lastLoginAt) }}
                </td>
              </tr>
            </tbody>
          </table>

          <!-- Pagination -->
          <div
            v-if="totalPages > 1"
            class="flex items-center justify-between border-t border-zinc-200 px-5 py-3 dark:border-zinc-800"
          >
            <p class="text-xs text-zinc-500 dark:text-zinc-400">
              Page {{ currentPage }} of {{ totalPages }}
            </p>
            <div class="flex gap-2">
              <button
                :disabled="currentPage <= 1"
                class="rounded-lg px-3 py-1.5 text-xs font-medium text-zinc-700 ring-1 ring-inset ring-zinc-300 transition-colors hover:bg-zinc-50 disabled:opacity-50 disabled:cursor-not-allowed dark:text-zinc-300 dark:ring-zinc-700 dark:hover:bg-zinc-800"
                @click="currentPage--"
              >
                Previous
              </button>
              <button
                :disabled="currentPage >= totalPages"
                class="rounded-lg px-3 py-1.5 text-xs font-medium text-zinc-700 ring-1 ring-inset ring-zinc-300 transition-colors hover:bg-zinc-50 disabled:opacity-50 disabled:cursor-not-allowed dark:text-zinc-300 dark:ring-zinc-700 dark:hover:bg-zinc-800"
                @click="currentPage++"
              >
                Next
              </button>
            </div>
          </div>
        </div>
      </template>
    </div>
  </SidebarLayout>
</template>
