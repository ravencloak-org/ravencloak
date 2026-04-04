<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { auditApi, type AuditLog } from '@/api/audit'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppTable from '@/components/ui/AppTable.vue'
import AppSlideOver from '@/components/ui/AppSlideOver.vue'
import AppInput from '@/components/ui/AppInput.vue'
import AppSelect from '@/components/ui/AppSelect.vue'
import AppEmptyState from '@/components/ui/AppEmptyState.vue'
import {
  ClipboardDocumentListIcon,
  ArrowUturnLeftIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
} from '@heroicons/vue/24/outline'

const route = useRoute()
const toast = useToast()
const { confirm } = useConfirm()

const realmName = computed(() => route.params.name as string)

const logs = ref<AuditLog[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

const page = ref(0)
const size = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)

// Filters
const filterEntityType = ref('')
const filterAction = ref('')

const entityTypeOptions = [
  { value: '', label: 'All Entity Types' },
  { value: 'CLIENT', label: 'Client' },
  { value: 'ROLE', label: 'Role' },
  { value: 'GROUP', label: 'Group' },
  { value: 'IDP', label: 'Identity Provider' },
  { value: 'USER', label: 'User' },
]

const actionOptions = [
  { value: '', label: 'All Actions' },
  { value: 'CREATE', label: 'Create' },
  { value: 'UPDATE', label: 'Update' },
  { value: 'DELETE', label: 'Delete' },
]

// Detail slide-over
const detailOpen = ref(false)
const selectedLog = ref<AuditLog | null>(null)

// Revert
const revertReason = ref('')
const reverting = ref(false)

onMounted(async () => {
  await loadAuditLogs()
})

watch([filterEntityType, filterAction], () => {
  page.value = 0
  loadAuditLogs()
})

watch(() => route.params.name, () => {
  if (route.params.name) {
    page.value = 0
    loadAuditLogs()
  }
})

async function loadAuditLogs(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    const response = await auditApi.getRealmAudit(realmName.value, page.value, size.value)
    // Client-side filtering since the API may not support query params for entity/action
    let filtered = response.content
    if (filterEntityType.value) {
      filtered = filtered.filter((l) => l.entityType === filterEntityType.value)
    }
    if (filterAction.value) {
      filtered = filtered.filter((l) => l.actionType === filterAction.value)
    }
    logs.value = filtered
    totalElements.value = response.totalElements
    totalPages.value = response.totalPages
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load audit logs'
    toast.error('Failed to load audit logs')
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
  revertReason.value = ''
  detailOpen.value = true
}

async function handleRevert(): Promise<void> {
  if (!selectedLog.value) return

  const confirmed = await confirm({
    title: 'Revert Action',
    message: `This will revert the ${selectedLog.value.actionType.toLowerCase()} of "${selectedLog.value.entityName}" (${selectedLog.value.entityType}). This cannot be undone.`,
    confirmLabel: 'Revert',
    destructive: true,
  })

  if (!confirmed) return

  if (!revertReason.value.trim()) {
    toast.warning('Please provide a reason for reverting')
    return
  }

  reverting.value = true
  try {
    const response = await auditApi.revertAction(
      realmName.value,
      selectedLog.value.id,
      revertReason.value.trim(),
    )
    if (response.success) {
      toast.success('Reverted', response.message)
      detailOpen.value = false
      await loadAuditLogs()
    } else {
      toast.error('Revert failed', response.message)
    }
  } catch (err) {
    toast.error('Failed to revert', err instanceof Error ? err.message : undefined)
  } finally {
    reverting.value = false
  }
}

function goToPage(p: number): void {
  page.value = p
  loadAuditLogs()
}
</script>

<template>
  <SidebarLayout>
    <div class="max-w-5xl mx-auto">
      <!-- Page header -->
      <div class="flex items-center justify-between mb-8">
        <div>
          <h1 class="text-2xl font-semibold text-zinc-900 dark:text-zinc-100">Audit Trail</h1>
          <p class="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Track all changes in
            <span class="font-mono">{{ realmName }}</span>
          </p>
        </div>
        <AppButton
          variant="secondary"
          :loading="loading"
          @click="loadAuditLogs"
        >
          Refresh
        </AppButton>
      </div>

      <!-- Filter bar -->
      <div class="flex items-center gap-3 mb-6">
        <div class="w-48">
          <AppSelect
            v-model="filterEntityType"
            :options="entityTypeOptions"
            placeholder="All Entity Types"
          />
        </div>
        <div class="w-40">
          <AppSelect
            v-model="filterAction"
            :options="actionOptions"
            placeholder="All Actions"
          />
        </div>
        <span class="ml-auto text-sm text-zinc-500 dark:text-zinc-400">
          {{ totalElements }} entries
        </span>
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
          title="No audit logs"
          description="Audit entries will appear here when changes are made to this realm."
          :icon="ClipboardDocumentListIcon"
        />

        <!-- Audit table -->
        <AppTable v-else>
          <template #header>
            <th class="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">
              Actor
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
            <th class="px-4 py-3 text-right text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">
              <span class="sr-only">Actions</span>
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
                <span class="text-sm text-zinc-900 dark:text-zinc-100 truncate max-w-[180px]">
                  {{ log.actorEmail || 'Unknown' }}
                </span>
              </div>
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

            <!-- Revert button -->
            <td class="px-4 py-3 whitespace-nowrap text-right">
              <AppButton
                v-if="log.canRevert && !log.reverted"
                variant="ghost"
                size="sm"
                @click.stop="openDetail(log)"
              >
                <ArrowUturnLeftIcon class="h-3.5 w-3.5" />
                Revert
              </AppButton>
              <AppBadge
                v-else-if="log.reverted"
                variant="warning"
              >
                Reverted
              </AppBadge>
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
        title="Audit Detail"
        @close="detailOpen = false"
      >
        <template v-if="selectedLog">
          <div class="space-y-5">
            <!-- Actor info -->
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
              <div>
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

            <!-- Revert section -->
            <div
              v-if="selectedLog.canRevert && !selectedLog.reverted"
              class="border-t border-zinc-200 dark:border-zinc-800 pt-5 mt-5"
            >
              <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100 mb-3">Revert this action</h3>
              <AppInput
                v-model="revertReason"
                label="Reason"
                placeholder="Why are you reverting this action?"
              />
              <div class="mt-3 flex justify-end">
                <AppButton
                  variant="danger"
                  :loading="reverting"
                  :disabled="!revertReason.trim()"
                  @click="handleRevert"
                >
                  <ArrowUturnLeftIcon class="h-4 w-4" />
                  Revert Action
                </AppButton>
              </div>
            </div>
          </div>
        </template>
      </AppSlideOver>
    </div>
  </SidebarLayout>
</template>
