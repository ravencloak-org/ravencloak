<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
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
  CloudIcon,
  PlusIcon,
  ArrowDownTrayIcon,
  NoSymbolIcon,
} from '@heroicons/vue/24/outline'

interface NebulaCert {
  id: string
  nodeName: string
  nodeType: 'laptop' | 'ec2'
  ipAddress: string
  expiresAt: string
  status: 'Active' | 'Revoked'
  config?: string
  deviceInfo?: string
  environment?: string
}

const route = useRoute()
const toast = useToast()
const { confirm } = useConfirm()

const realmName = computed(() => route.params.name as string)

const certs = ref<NebulaCert[]>([])
const loading = ref(true)

// Slide-over state
const slideOverOpen = ref(false)
const formNodeName = ref('')
const formNodeType = ref<'laptop' | 'ec2'>('laptop')
const formDeviceInfo = ref('')
const formEnvironment = ref('')
const formSubmitting = ref(false)
const formError = ref<string | null>(null)

const environmentOptions = [
  { value: 'dev', label: 'Development' },
  { value: 'staging', label: 'Staging' },
  { value: 'uat', label: 'UAT' },
  { value: 'prod', label: 'Production' },
]

onMounted(async () => {
  await loadCerts()
})

async function loadCerts(): Promise<void> {
  loading.value = true
  try {
    const response = await axios.get<NebulaCert[]>(`/api/nebula/${realmName.value}/certs`)
    certs.value = response.data
  } catch {
    // Placeholder: API doesn't exist yet, show empty state
    certs.value = []
  } finally {
    loading.value = false
  }
}

function isExpiringSoon(expiresAt: string): boolean {
  const expiry = new Date(expiresAt)
  const now = new Date()
  const diffDays = (expiry.getTime() - now.getTime()) / (1000 * 60 * 60 * 24)
  return diffDays > 0 && diffDays < 30
}

function isExpired(expiresAt: string): boolean {
  return new Date(expiresAt).getTime() < new Date().getTime()
}

function formatDate(dateString: string): string {
  return new Date(dateString).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

async function downloadConfig(cert: NebulaCert): Promise<void> {
  try {
    const response = await axios.get<string>(
      `/api/nebula/${realmName.value}/certs/${cert.id}/config`,
      { responseType: 'text' },
    )
    const blob = new Blob([response.data], { type: 'application/yaml' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${cert.nodeName}-config.yaml`
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    toast.error('Failed to download config')
  }
}

async function revokeCert(cert: NebulaCert): Promise<void> {
  const confirmed = await confirm({
    title: 'Revoke Certificate',
    message: `Are you sure you want to revoke the certificate for "${cert.nodeName}"? This action cannot be undone and the node will lose VPN access.`,
    confirmLabel: 'Revoke',
    destructive: true,
  })

  if (!confirmed) return

  try {
    await axios.post(`/api/nebula/${realmName.value}/certs/${cert.id}/revoke`)
    toast.success('Certificate revoked', `"${cert.nodeName}" has been revoked`)
    await loadCerts()
  } catch {
    toast.error('Failed to revoke certificate')
  }
}

function openGenerateSlideOver(): void {
  formNodeName.value = ''
  formNodeType.value = 'laptop'
  formDeviceInfo.value = ''
  formEnvironment.value = ''
  formError.value = null
  slideOverOpen.value = true
}

async function handleGenerate(): Promise<void> {
  if (!formNodeName.value.trim()) {
    formError.value = 'Node name is required'
    return
  }
  if (formNodeType.value === 'ec2' && !formEnvironment.value) {
    formError.value = 'Environment is required for EC2 nodes'
    return
  }

  formSubmitting.value = true
  formError.value = null

  try {
    await axios.post(`/api/nebula/${realmName.value}/certs`, {
      nodeName: formNodeName.value.trim(),
      nodeType: formNodeType.value,
      deviceInfo: formDeviceInfo.value.trim() || undefined,
      environment: formNodeType.value === 'ec2' ? formEnvironment.value : undefined,
    })
    toast.success('Certificate generated', `Certificate for "${formNodeName.value}" is ready`)
    slideOverOpen.value = false
    await loadCerts()
  } catch (err) {
    formError.value =
      err instanceof Error ? err.message : 'Failed to generate certificate'
  } finally {
    formSubmitting.value = false
  }
}
</script>

<template>
  <SidebarLayout>
    <div class="max-w-5xl mx-auto">
      <!-- Page header -->
      <div class="flex items-center justify-between mb-8">
        <div>
          <h1 class="text-2xl font-semibold text-zinc-900 dark:text-zinc-100">Nebula VPN</h1>
          <p class="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Manage VPN certificates for
            <span class="font-mono">{{ realmName }}</span>
          </p>
        </div>
        <AppButton @click="openGenerateSlideOver">
          <PlusIcon class="h-4 w-4" />
          Generate Certificate
        </AppButton>
      </div>

      <!-- Loading state -->
      <div
        v-if="loading"
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

      <template v-else>
        <!-- Empty state -->
        <AppEmptyState
          v-if="certs.length === 0"
          title="No certificates"
          description="Generate your first Nebula VPN certificate to connect nodes to your network."
          :icon="CloudIcon"
        >
          <AppButton @click="openGenerateSlideOver">
            <PlusIcon class="h-4 w-4" />
            Generate Certificate
          </AppButton>
        </AppEmptyState>

        <!-- Certificates table -->
        <AppTable v-else>
          <template #header>
            <th class="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">
              Node Name
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">
              Type
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">
              IP Address
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">
              Expires At
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">
              Status
            </th>
            <th class="px-4 py-3 text-right text-xs font-medium text-zinc-500 dark:text-zinc-400 uppercase tracking-wider">
              Actions
            </th>
          </template>

          <tr
            v-for="cert in certs"
            :key="cert.id"
            class="hover:bg-zinc-50 dark:hover:bg-zinc-800/50 transition-colors"
          >
            <!-- Node Name -->
            <td class="px-4 py-3 whitespace-nowrap">
              <span class="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                {{ cert.nodeName }}
              </span>
              <p
                v-if="cert.environment"
                class="text-xs text-zinc-500 dark:text-zinc-400"
              >
                {{ cert.environment }}
              </p>
            </td>

            <!-- Type -->
            <td class="px-4 py-3 whitespace-nowrap">
              <AppBadge :variant="cert.nodeType === 'ec2' ? 'info' : 'neutral'">
                {{ cert.nodeType === 'ec2' ? 'EC2' : 'Laptop' }}
              </AppBadge>
            </td>

            <!-- IP Address -->
            <td class="px-4 py-3 whitespace-nowrap text-sm font-mono text-zinc-900 dark:text-zinc-100">
              {{ cert.ipAddress }}
            </td>

            <!-- Expires At -->
            <td class="px-4 py-3 whitespace-nowrap">
              <div class="flex items-center gap-1.5">
                <span
                  :class="[
                    'text-sm',
                    isExpired(cert.expiresAt)
                      ? 'text-red-600 dark:text-red-400'
                      : isExpiringSoon(cert.expiresAt)
                        ? 'text-amber-600 dark:text-amber-400'
                        : 'text-zinc-900 dark:text-zinc-100',
                  ]"
                >
                  {{ formatDate(cert.expiresAt) }}
                </span>
                <AppBadge
                  v-if="isExpiringSoon(cert.expiresAt) && !isExpired(cert.expiresAt)"
                  variant="warning"
                >
                  Expiring soon
                </AppBadge>
                <AppBadge
                  v-else-if="isExpired(cert.expiresAt)"
                  variant="danger"
                >
                  Expired
                </AppBadge>
              </div>
            </td>

            <!-- Status -->
            <td class="px-4 py-3 whitespace-nowrap">
              <AppBadge
                :variant="cert.status === 'Active' ? 'success' : 'danger'"
                :dot="true"
              >
                {{ cert.status }}
              </AppBadge>
            </td>

            <!-- Actions -->
            <td class="px-4 py-3 whitespace-nowrap text-right">
              <div class="flex items-center justify-end gap-2">
                <AppButton
                  v-if="cert.status === 'Active'"
                  variant="ghost"
                  size="sm"
                  @click="downloadConfig(cert)"
                >
                  <ArrowDownTrayIcon class="h-3.5 w-3.5" />
                  Config
                </AppButton>
                <AppButton
                  v-if="cert.status === 'Active'"
                  variant="ghost"
                  size="sm"
                  @click="revokeCert(cert)"
                >
                  <NoSymbolIcon class="h-3.5 w-3.5 text-red-500" />
                  <span class="text-red-600 dark:text-red-400">Revoke</span>
                </AppButton>
              </div>
            </td>
          </tr>
        </AppTable>
      </template>

      <!-- Generate Certificate Slide-Over -->
      <AppSlideOver
        :open="slideOverOpen"
        title="Generate Certificate"
        @close="slideOverOpen = false"
      >
        <form
          class="flex flex-col gap-5"
          @submit.prevent="handleGenerate"
        >
          <!-- Error -->
          <div
            v-if="formError"
            class="rounded-lg bg-red-50 dark:bg-red-500/10 p-3 text-sm text-red-700 dark:text-red-400"
          >
            {{ formError }}
          </div>

          <AppInput
            v-model="formNodeName"
            label="Node Name"
            placeholder="e.g. jobin-macbook, api-server-1"
          />

          <!-- Node type radio -->
          <div>
            <label class="block text-sm font-medium text-zinc-900 dark:text-zinc-100 mb-2">
              Node Type
            </label>
            <div class="flex gap-4">
              <label
                class="flex items-center gap-2 cursor-pointer"
              >
                <input
                  type="radio"
                  value="laptop"
                  v-model="formNodeType"
                  class="h-4 w-4 text-primary-600 border-zinc-300 dark:border-zinc-600 focus:ring-primary-600 dark:bg-zinc-900"
                />
                <span class="text-sm text-zinc-900 dark:text-zinc-100">Laptop</span>
              </label>
              <label
                class="flex items-center gap-2 cursor-pointer"
              >
                <input
                  type="radio"
                  value="ec2"
                  v-model="formNodeType"
                  class="h-4 w-4 text-primary-600 border-zinc-300 dark:border-zinc-600 focus:ring-primary-600 dark:bg-zinc-900"
                />
                <span class="text-sm text-zinc-900 dark:text-zinc-100">EC2</span>
              </label>
            </div>
          </div>

          <AppInput
            v-model="formDeviceInfo"
            label="Device Info (optional)"
            placeholder="e.g. MacBook Pro M2, t3.medium"
          />

          <!-- Environment (EC2 only) -->
          <div v-if="formNodeType === 'ec2'">
            <AppSelect
              v-model="formEnvironment"
              label="Environment"
              :options="environmentOptions"
              placeholder="Select environment"
            />
          </div>

          <div class="mt-4 flex justify-end gap-3">
            <AppButton
              variant="secondary"
              type="button"
              @click="slideOverOpen = false"
            >
              Cancel
            </AppButton>
            <AppButton
              type="submit"
              :loading="formSubmitting"
              :disabled="!formNodeName.trim()"
            >
              Generate
            </AppButton>
          </div>
        </form>
      </AppSlideOver>
    </div>
  </SidebarLayout>
</template>
