<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRealmStore } from '@/stores/realm'
import { useToast } from '@/composables/useToast'
import { auditApi, type AuditLog } from '@/api/audit'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import {
  RectangleStackIcon,
  UsersIcon,
  KeyIcon,
  UserGroupIcon,
  ArrowPathIcon,
  PlusIcon,
  UserPlusIcon,
} from '@heroicons/vue/24/outline'

defineOptions({
  name: 'RealmDashboardPage',
})

const route = useRoute()
const router = useRouter()
const realmStore = useRealmStore()
const toast = useToast()

const realmName = computed(() => route.params.name as string)

const loading = ref(true)
const syncing = ref(false)
const error = ref<string | null>(null)

const recentAudit = ref<AuditLog[]>([])
const auditLoading = ref(false)

onMounted(async () => {
  await loadRealm()
})

watch(() => route.params.name, async () => {
  await loadRealm()
})

async function loadRealm(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    await realmStore.fetchRealm(realmName.value)
    // Fetch recent audit in parallel (non-blocking)
    fetchRecentAudit()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load realm'
    toast.error('Error', error.value)
  } finally {
    loading.value = false
  }
}

async function fetchRecentAudit(): Promise<void> {
  auditLoading.value = true
  try {
    const response = await auditApi.getRealmAudit(realmName.value, 0, 5)
    recentAudit.value = response.content
  } catch {
    // Silent fail for audit -- non-critical
    recentAudit.value = []
  } finally {
    auditLoading.value = false
  }
}

async function handleSync(): Promise<void> {
  syncing.value = true
  try {
    await realmStore.syncRealm(realmName.value)
    toast.success('Synced', 'Realm synchronized successfully')
  } catch (err) {
    toast.error('Sync Failed', err instanceof Error ? err.message : 'Failed to sync realm')
  } finally {
    syncing.value = false
  }
}

const totalClients = computed(() => realmStore.currentRealm?.clients?.length ?? 0)
const totalRoles = computed(() => realmStore.currentRealm?.roles?.length ?? 0)
const totalGroups = computed(() => realmStore.currentRealm?.groups?.length ?? 0)
// Users are not in RealmDetails, so we show a placeholder or rely on realm metadata
const totalUsers = computed(() => 0)

const statCards = computed(() => [
  {
    name: 'Clients',
    value: totalClients.value,
    icon: RectangleStackIcon,
    href: `/realms/${realmName.value}/clients`,
  },
  {
    name: 'Users',
    value: totalUsers.value,
    icon: UsersIcon,
    href: `/realms/${realmName.value}/users`,
  },
  {
    name: 'Roles',
    value: totalRoles.value,
    icon: KeyIcon,
    href: `/realms/${realmName.value}/roles`,
  },
  {
    name: 'Groups',
    value: totalGroups.value,
    icon: UserGroupIcon,
    href: `/realms/${realmName.value}/groups`,
  },
])

function actionTypeColor(actionType: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (actionType) {
    case 'CREATE':
      return 'success'
    case 'UPDATE':
      return 'warning'
    case 'DELETE':
      return 'danger'
    default:
      return 'info'
  }
}

function relativeTime(dateString: string): string {
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

      <!-- Dashboard content -->
      <template v-else-if="realmStore.currentRealm">
        <!-- Page header -->
        <div class="flex items-start justify-between">
          <div>
            <div class="flex items-center gap-3">
              <h1 class="text-2xl font-semibold text-zinc-900 dark:text-zinc-100">
                {{ realmStore.currentRealm.displayName || realmStore.currentRealm.realmName }}
              </h1>
              <AppBadge :variant="realmStore.currentRealm.enabled ? 'success' : 'danger'" dot>
                {{ realmStore.currentRealm.enabled ? 'Enabled' : 'Disabled' }}
              </AppBadge>
            </div>
            <p class="mt-1 font-mono text-sm text-zinc-500 dark:text-zinc-400">
              {{ realmStore.currentRealm.realmName }}
            </p>
          </div>
          <div class="flex gap-2">
            <AppButton variant="secondary" :loading="syncing" @click="handleSync">
              <ArrowPathIcon class="h-4 w-4" />
              Sync
            </AppButton>
          </div>
        </div>

        <!-- Stat cards -->
        <div class="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <button
            v-for="stat in statCards"
            :key="stat.name"
            class="flex items-center gap-4 rounded-lg bg-white p-5 text-left ring-1 ring-zinc-200 transition-all hover:ring-zinc-300 hover:shadow-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:bg-zinc-900 dark:ring-zinc-800 dark:hover:ring-zinc-700"
            @click="router.push(stat.href)"
          >
            <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-zinc-100 dark:bg-zinc-800">
              <component :is="stat.icon" class="h-5 w-5 text-zinc-600 dark:text-zinc-400" />
            </div>
            <div>
              <p class="text-2xl font-semibold text-zinc-900 dark:text-zinc-100">
                {{ stat.value }}
              </p>
              <p class="text-sm text-zinc-500 dark:text-zinc-400">
                {{ stat.name }}
              </p>
            </div>
          </button>
        </div>

        <!-- Recent audit activity -->
        <div class="mt-8">
          <h2 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Recent Activity
          </h2>
          <div class="mt-3 rounded-lg bg-white ring-1 ring-zinc-200 dark:bg-zinc-900 dark:ring-zinc-800">
            <!-- Audit loading -->
            <div v-if="auditLoading" class="flex items-center justify-center py-8">
              <svg
                class="h-5 w-5 animate-spin text-zinc-400"
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
              >
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
              </svg>
            </div>

            <!-- Empty audit -->
            <div
              v-else-if="recentAudit.length === 0"
              class="px-5 py-8 text-center text-sm text-zinc-500 dark:text-zinc-400"
            >
              No recent activity
            </div>

            <!-- Audit timeline -->
            <ul v-else class="divide-y divide-zinc-100 dark:divide-zinc-800">
              <li
                v-for="log in recentAudit"
                :key="log.id"
                class="flex items-center gap-4 px-5 py-3"
              >
                <div class="shrink-0">
                  <AppBadge :variant="actionTypeColor(log.actionType)">
                    {{ log.actionType }}
                  </AppBadge>
                </div>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm text-zinc-900 dark:text-zinc-100">
                    <span class="font-medium">{{ log.entityType }}</span>
                    <span class="text-zinc-500 dark:text-zinc-400"> &middot; {{ log.entityName }}</span>
                  </p>
                  <p class="text-xs text-zinc-500 dark:text-zinc-400">
                    {{ log.actorEmail || log.actorKeycloakId }}
                  </p>
                </div>
                <span class="shrink-0 text-xs text-zinc-400 dark:text-zinc-500">
                  {{ relativeTime(log.createdAt) }}
                </span>
              </li>
            </ul>
          </div>
        </div>

        <!-- Quick actions -->
        <div class="mt-8">
          <h2 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Quick Actions
          </h2>
          <div class="mt-3 flex flex-wrap gap-3">
            <AppButton
              variant="secondary"
              @click="router.push(`/realms/${realmName}/clients/create`)"
            >
              <PlusIcon class="h-4 w-4" />
              Create Client
            </AppButton>
            <AppButton
              variant="secondary"
              @click="router.push(`/realms/${realmName}/users/create`)"
            >
              <UserPlusIcon class="h-4 w-4" />
              Add User
            </AppButton>
            <AppButton
              variant="secondary"
              :loading="syncing"
              @click="handleSync"
            >
              <ArrowPathIcon class="h-4 w-4" />
              Sync Realm
            </AppButton>
          </div>
        </div>
      </template>
    </div>
  </SidebarLayout>
</template>
