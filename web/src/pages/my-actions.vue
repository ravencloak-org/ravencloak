<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { auditApi, type AuditLog } from '@/api/audit'
import { useToast } from '@/composables/useToast'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppTable from '@/components/ui/AppTable.vue'
import AppSlideOver from '@/components/ui/AppSlideOver.vue'
import AppEmptyState from '@/components/ui/AppEmptyState.vue'
import {
  ClockIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
} from '@heroicons/vue/24/outline'

const toast = useToast()

const logs = ref<AuditLog[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

const page = ref(0)
const size = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)

// Detail slide-over
const detailOpen = ref(false)
const selectedLog = ref<AuditLog | null>(null)

onMounted(async () => {
  await loadMyActions()
})

async function loadMyActions(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    const response = await auditApi.getMyActions(page.value, size.value)
    logs.value = response.content
    totalElements.value = response.totalElements
    totalPages.value = response.totalPages
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load action history'
    toast.error('Failed to load actions')
  } finally {
    loading.value = false
  }
}

function getActionBadgeVariant(action: string): 'success' | 'info' | 'danger' {
  switch (action) {
    case 'CREATE':
      return 'success'
    case 'UPDATE':
      return 'info'
    case 'DELETE':
      return 'danger'
    default:
      return 'info'
  }
}

function getInitials(log: AuditLog): string {
  if (log.actorDisplayName) {
    return log.actorDisplayName
      .split(' ')
      .map((w) => w[0])
      .slice(0, 2)
      .join('')
      .toUpperCase()
  }
  if (log.actorEmail) {
    return log.actorEmail.substring(0, 2).toUpperCase()
  }
  return '??'
}

function formatRelativeTime(dateString: string): string {
  const date = new Date(dateString)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (seconds < 60) return 'just now'
  if (minutes < 60) return `${minutes}m ago`
  if (hours < 24) return `${hours}h ago`
  if (days < 30) return `${days}d ago`
  return date.toLocaleDateString()
}

function openDetail(log: AuditLog): void {
  selectedLog.value = log
  detailOpen.value = true
}

function goToPage(p: number): void {
  page.value = p
  loadMyActions()
}
</script>

<template>
  <SidebarLayout>
    <div class="max-w-5xl mx-auto">
      <!-- Page header -->
      <div class="flex items-center justify-between mb-8">
        <div>
          <h1 class="text-2xl font-semibold text-zinc-900 dark:text-zinc-100">My Actions</h1>
          <p class="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Your recent activity across all realms
          </p>
        </div>
        <AppButton
          variant="secondary"
          :loading="loading"
          @click="loadMyActions"
        >
          Refresh
        </AppButton>
      </div>

      <!-- Loading state -->
      <div
        v-if="loading && logs.length === 0"
        class="flex items-center justify-center py-16"
      >
        <svg
          class="animate-spin h-6 w-6 text-zinc-400"
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
        </svg>
      </div>

      <!-- Error state -->
      <div
        v-else-if="error"
        class="rounded-lg bg-red-50 dark:bg-red-500/10 p-4 text-sm text-red-700 dark:text-red-400"
      >
        {{ error }}
      </div>

      <template v-else>
        <!-- Empty state -->
        <AppEmptyState
          v-if="logs.length === 0"
          title="No actions yet"
          description="Your activity will appear here as you make changes across realms."
          :icon="ClockIcon"
        />

        <!-- Actions table -->
        <AppTable v-else>
          <template #header>
            <th class="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">
              Actor
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">
              Realm
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">
              Action
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">
              Entity
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">
              Time
            </th>
          </template>

          <tr
            v-for="log in logs"
            :key="log.id"
            class="hover:bg-zinc-50 dark:hover:bg-zinc-800/50 cursor-pointer transition-colors"
            @click="openDetail(log)"
          >
            <!-- Actor -->
            <td class="px-4 py-3 whitespace-nowrap">
              <div class="flex items-center gap-2.5">
                <div class="flex-shrink-0 h-7 w-7 rounded-full bg-zinc-200 dark:bg-zinc-700 flex items-center justify-center">
                  <span class="text-xs font-medium text-zinc-600 dark:text-zinc-300">
                    {{ getInitials(log) }}
                  </span>
                </div>
                <span class="text-sm text-zinc-900 dark:text-zinc-100 truncate max-w-[160px]">
                  {{ log.actorEmail || 'Unknown' }}
                </span>
              </div>
            </td>

            <!-- Realm -->
            <td class="px-4 py-3 whitespace-nowrap">
              <span class="text-sm font-mono text-zinc-600 dark:text-zinc-400">
                {{ log.realmName }}
              </span>
            </td>

            <!-- Action -->
            <td class="px-4 py-3 whitespace-nowrap">
              <AppBadge :variant="getActionBadgeVariant(log.actionType)">
                {{ log.actionType }}
              </AppBadge>
            </td>

            <!-- Entity -->
            <td class="px-4 py-3 whitespace-nowrap">
              <div class="flex items-center gap-1.5">
                <span class="text-xs text-zinc-400 dark:text-zinc-500 uppercase">{{ log.entityType }}</span>
                <span class="text-sm text-zinc-900 dark:text-zinc-100">{{ log.entityName }}</span>
              </div>
            </td>

            <!-- Time -->
            <td class="px-4 py-3 whitespace-nowrap text-sm text-zinc-500 dark:text-zinc-400">
              {{ formatRelativeTime(log.createdAt) }}
            </td>
          </tr>
        </AppTable>

        <!-- Pagination -->
        <div
          v-if="totalPages > 1"
          class="mt-4 flex items-center justify-between"
        >
          <p class="text-sm text-zinc-500 dark:text-zinc-400">
            Page {{ page + 1 }} of {{ totalPages }}
          </p>
          <div class="flex items-center gap-2">
            <AppButton
              variant="secondary"
              size="sm"
              :disabled="page === 0"
              @click="goToPage(page - 1)"
            >
              <ChevronLeftIcon class="h-4 w-4" />
              Previous
            </AppButton>
            <AppButton
              variant="secondary"
              size="sm"
              :disabled="page >= totalPages - 1"
              @click="goToPage(page + 1)"
            >
              Next
              <ChevronRightIcon class="h-4 w-4" />
            </AppButton>
          </div>
        </div>
      </template>

      <!-- Detail Slide-Over -->
      <AppSlideOver
        :open="detailOpen"
        title="Action Detail"
        @close="detailOpen = false"
      >
        <template v-if="selectedLog">
          <div class="space-y-5">
            <!-- Info grid -->
            <div class="grid grid-cols-2 gap-4">
              <div>
                <p class="text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase mb-1">Actor</p>
                <p class="text-sm text-zinc-900 dark:text-zinc-100">
                  {{ selectedLog.actorDisplayName || selectedLog.actorEmail || 'Unknown' }}
                </p>
                <p
                  v-if="selectedLog.actorDisplayName && selectedLog.actorEmail"
                  class="text-xs text-zinc-500 dark:text-zinc-400"
                >
                  {{ selectedLog.actorEmail }}
                </p>
              </div>
              <div>
                <p class="text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase mb-1">Realm</p>
                <p class="text-sm font-mono text-zinc-900 dark:text-zinc-100">
                  {{ selectedLog.realmName }}
                </p>
              </div>
              <div>
                <p class="text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase mb-1">Timestamp</p>
                <p class="text-sm text-zinc-900 dark:text-zinc-100">
                  {{ new Date(selectedLog.createdAt).toLocaleString() }}
                </p>
              </div>
              <div>
                <p class="text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase mb-1">Action</p>
                <AppBadge :variant="getActionBadgeVariant(selectedLog.actionType)">
                  {{ selectedLog.actionType }}
                </AppBadge>
              </div>
              <div class="col-span-2">
                <p class="text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase mb-1">Entity</p>
                <p class="text-sm text-zinc-900 dark:text-zinc-100">
                  {{ selectedLog.entityType }} / {{ selectedLog.entityName }}
                </p>
              </div>
            </div>

            <!-- Reverted info -->
            <div
              v-if="selectedLog.reverted"
              class="rounded-lg bg-amber-50 dark:bg-amber-500/10 p-3 text-sm"
            >
              <p class="font-medium text-amber-800 dark:text-amber-400">This action has been reverted</p>
              <p class="text-amber-700 dark:text-amber-300 mt-0.5 text-xs">
                {{ new Date(selectedLog.revertedAt!).toLocaleString() }}
                <template v-if="selectedLog.revertReason"> &mdash; {{ selectedLog.revertReason }}</template>
              </p>
            </div>

            <!-- Changed fields -->
            <div v-if="selectedLog.changedFields && selectedLog.changedFields.length > 0">
              <p class="text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase mb-2">Changed Fields</p>
              <div class="flex flex-wrap gap-1.5">
                <AppBadge
                  v-for="field in selectedLog.changedFields"
                  :key="field"
                  variant="neutral"
                >
                  {{ field }}
                </AppBadge>
              </div>
            </div>

            <!-- Before state -->
            <div v-if="selectedLog.beforeState">
              <p class="text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase mb-2">Before State</p>
              <pre class="rounded-lg bg-red-50 dark:bg-red-500/5 p-3 text-xs text-red-800 dark:text-red-300 overflow-x-auto ring-1 ring-red-200 dark:ring-red-500/20">{{ JSON.stringify(selectedLog.beforeState, null, 2) }}</pre>
            </div>

            <!-- After state -->
            <div v-if="selectedLog.afterState">
              <p class="text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase mb-2">After State</p>
              <pre class="rounded-lg bg-emerald-50 dark:bg-emerald-500/5 p-3 text-xs text-emerald-800 dark:text-emerald-300 overflow-x-auto ring-1 ring-emerald-200 dark:ring-emerald-500/20">{{ JSON.stringify(selectedLog.afterState, null, 2) }}</pre>
            </div>
          </div>
        </template>
      </AppSlideOver>
    </div>
  </SidebarLayout>
</template>
