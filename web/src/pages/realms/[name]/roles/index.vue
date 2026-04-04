<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { rolesApi } from '@/api/roles'
import { clientsApi } from '@/api/clients'
import { useToast } from '@/composables/useToast'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppSlideOver from '@/components/ui/AppSlideOver.vue'
import AppInput from '@/components/ui/AppInput.vue'
import AppEmptyState from '@/components/ui/AppEmptyState.vue'
import {
  ShieldCheckIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  PlusIcon,
} from '@heroicons/vue/24/outline'
import type { Role, Client, CreateRoleRequest } from '@/types'

const route = useRoute()
const toast = useToast()

const realmName = computed(() => route.params.name as string)

const realmRoles = ref<Role[]>([])
const clientRolesMap = ref<Map<string, { client: Client; roles: Role[] }>>(new Map())
const loading = ref(true)
const expandedClients = ref<Set<string>>(new Set())

// Slide-over state
const slideOverOpen = ref(false)
const formName = ref('')
const formDescription = ref('')
const formIsComposite = ref(false)
const formSubmitting = ref(false)
const formError = ref<string | null>(null)

onMounted(async () => {
  await loadRoles()
})

async function loadRoles(): Promise<void> {
  loading.value = true
  try {
    const [roles, clients] = await Promise.all([
      rolesApi.listRealmRoles(realmName.value),
      clientsApi.list(realmName.value),
    ])
    realmRoles.value = roles

    const map = new Map<string, { client: Client; roles: Role[] }>()
    await Promise.all(
      clients.map(async (client) => {
        try {
          const clientRoles = await rolesApi.listClientRoles(realmName.value, client.id)
          if (clientRoles.length > 0) {
            map.set(client.id, { client, roles: clientRoles })
          }
        } catch {
          // skip clients we can't read roles for
        }
      }),
    )
    clientRolesMap.value = map
  } catch (err) {
    toast.error('Failed to load roles', err instanceof Error ? err.message : undefined)
  } finally {
    loading.value = false
  }
}

function toggleClient(clientId: string): void {
  if (expandedClients.value.has(clientId)) {
    expandedClients.value.delete(clientId)
  } else {
    expandedClients.value.add(clientId)
  }
}

function openCreateSlideOver(): void {
  formName.value = ''
  formDescription.value = ''
  formIsComposite.value = false
  formError.value = null
  slideOverOpen.value = true
}

async function handleCreateRole(): Promise<void> {
  if (!formName.value.trim()) {
    formError.value = 'Role name is required'
    return
  }

  formSubmitting.value = true
  formError.value = null

  const request: CreateRoleRequest = {
    name: formName.value.trim(),
    description: formDescription.value.trim() || undefined,
  }

  try {
    await rolesApi.createRealmRole(realmName.value, request)
    toast.success('Role created', `"${request.name}" has been created`)
    slideOverOpen.value = false
    await loadRoles()
  } catch (err) {
    formError.value = err instanceof Error ? err.message : 'Failed to create role'
  } finally {
    formSubmitting.value = false
  }
}
</script>

<template>
  <SidebarLayout>
    <div class="max-w-4xl mx-auto">
      <!-- Page header -->
      <div class="flex items-center justify-between mb-8">
        <div>
          <h1 class="text-2xl font-semibold text-zinc-900 dark:text-zinc-100">Roles</h1>
          <p class="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Manage realm and client roles for
            <span class="font-mono">{{ realmName }}</span>
          </p>
        </div>
        <AppButton @click="openCreateSlideOver">
          <PlusIcon class="h-4 w-4" />
          Create Role
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
          v-if="realmRoles.length === 0 && clientRolesMap.size === 0"
          title="No roles found"
          description="Create your first role to manage permissions in this realm."
          :icon="ShieldCheckIcon"
        >
          <AppButton @click="openCreateSlideOver">
            <PlusIcon class="h-4 w-4" />
            Create Role
          </AppButton>
        </AppEmptyState>

        <template v-else>
          <!-- Realm Roles section -->
          <div class="mb-8">
            <h2 class="text-sm font-semibold text-zinc-500 dark:text-zinc-400 uppercase tracking-wider mb-3">
              Realm Roles
              <span class="text-zinc-400 dark:text-zinc-500 ml-1">({{ realmRoles.length }})</span>
            </h2>
            <div
              v-if="realmRoles.length > 0"
              class="bg-white dark:bg-zinc-900 rounded-lg ring-1 ring-zinc-200 dark:ring-zinc-800 divide-y divide-zinc-200 dark:divide-zinc-800"
            >
              <div
                v-for="role in realmRoles"
                :key="role.id"
                class="flex items-center justify-between px-4 py-3 hover:bg-zinc-50 dark:hover:bg-zinc-800/50 transition-colors"
              >
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-2">
                    <span class="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                      {{ role.name }}
                    </span>
                    <AppBadge v-if="role.composite" variant="info">Composite</AppBadge>
                  </div>
                  <p
                    v-if="role.description"
                    class="mt-0.5 text-sm text-zinc-500 dark:text-zinc-400 truncate"
                  >
                    {{ role.description }}
                  </p>
                </div>
              </div>
            </div>
            <p
              v-else
              class="text-sm text-zinc-500 dark:text-zinc-400 italic"
            >
              No realm roles defined.
            </p>
          </div>

          <!-- Client Roles section -->
          <div v-if="clientRolesMap.size > 0">
            <h2 class="text-sm font-semibold text-zinc-500 dark:text-zinc-400 uppercase tracking-wider mb-3">
              Client Roles
            </h2>
            <div class="space-y-3">
              <div
                v-for="[clientId, entry] in clientRolesMap"
                :key="clientId"
                class="bg-white dark:bg-zinc-900 rounded-lg ring-1 ring-zinc-200 dark:ring-zinc-800 overflow-hidden"
              >
                <!-- Client header -->
                <button
                  type="button"
                  class="w-full flex items-center justify-between px-4 py-3 text-left hover:bg-zinc-50 dark:hover:bg-zinc-800/50 transition-colors"
                  @click="toggleClient(clientId)"
                >
                  <div class="flex items-center gap-2">
                    <component
                      :is="expandedClients.has(clientId) ? ChevronDownIcon : ChevronRightIcon"
                      class="h-4 w-4 text-zinc-400"
                    />
                    <span class="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                      {{ entry.client.name || entry.client.clientId }}
                    </span>
                    <AppBadge variant="neutral">{{ entry.roles.length }}</AppBadge>
                  </div>
                </button>

                <!-- Client roles list -->
                <div
                  v-show="expandedClients.has(clientId)"
                  class="border-t border-zinc-200 dark:border-zinc-800 divide-y divide-zinc-100 dark:divide-zinc-800"
                >
                  <div
                    v-for="role in entry.roles"
                    :key="role.id"
                    class="flex items-center justify-between px-4 py-3 pl-10 hover:bg-zinc-50 dark:hover:bg-zinc-800/50 transition-colors"
                  >
                    <div class="min-w-0 flex-1">
                      <div class="flex items-center gap-2">
                        <span class="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                          {{ role.name }}
                        </span>
                        <AppBadge v-if="role.composite" variant="info">Composite</AppBadge>
                      </div>
                      <p
                        v-if="role.description"
                        class="mt-0.5 text-sm text-zinc-500 dark:text-zinc-400 truncate"
                      >
                        {{ role.description }}
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </template>

      <!-- Create Role Slide-Over -->
      <AppSlideOver
        :open="slideOverOpen"
        title="Create Role"
        @close="slideOverOpen = false"
      >
        <form
          class="flex flex-col gap-5"
          @submit.prevent="handleCreateRole"
        >
          <!-- Error -->
          <div
            v-if="formError"
            class="rounded-lg bg-red-50 dark:bg-red-500/10 p-3 text-sm text-red-700 dark:text-red-400"
          >
            {{ formError }}
          </div>

          <AppInput
            v-model="formName"
            label="Role Name"
            placeholder="e.g. editor, viewer"
          />

          <AppInput
            v-model="formDescription"
            label="Description"
            placeholder="Optional description"
          />

          <div class="flex items-center gap-3">
            <button
              type="button"
              role="switch"
              :aria-checked="formIsComposite"
              :class="[
                formIsComposite ? 'bg-primary-600' : 'bg-zinc-200 dark:bg-zinc-700',
                'relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-600 focus:ring-offset-2 dark:focus:ring-offset-zinc-900',
              ]"
              @click="formIsComposite = !formIsComposite"
            >
              <span
                :class="[
                  formIsComposite ? 'translate-x-4' : 'translate-x-0.5',
                  'pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out mt-0.5',
                ]"
              />
            </button>
            <span class="text-sm font-medium text-zinc-900 dark:text-zinc-100">Composite role</span>
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
              :disabled="!formName.trim()"
            >
              Create Role
            </AppButton>
          </div>
        </form>
      </AppSlideOver>
    </div>
  </SidebarLayout>
</template>
