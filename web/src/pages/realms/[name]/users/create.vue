<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import { usersApi } from '@/api/users'
import { clientsApi } from '@/api'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppInput from '@/components/ui/AppInput.vue'
import { ArrowLeftIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import type { CreateRealmUserRequest, Client } from '@/types'

defineOptions({ name: 'CreateUserPage' })

const route = useRoute()
const router = useRouter()
const toast = useToast()

const realmName = computed(() => route.params.name as string)

const email = ref('')
const displayName = ref('')
const firstName = ref('')
const lastName = ref('')
const phone = ref('')
const jobTitle = ref('')
const department = ref('')
const selectedClientIds = ref<string[]>([])

const clients = ref<Client[]>([])
const loadingClients = ref(true)
const loading = ref(false)
const error = ref<string | null>(null)

const isValidEmail = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value))
const canSubmit = computed(() => isValidEmail.value && !loading.value)

onMounted(async () => {
  try {
    const all = await clientsApi.list(realmName.value)
    clients.value = all.filter((c) => c.publicClient)
  } catch {
    // non-critical
  } finally {
    loadingClients.value = false
  }
})

function toggleClient(id: string) {
  const idx = selectedClientIds.value.indexOf(id)
  if (idx >= 0) selectedClientIds.value.splice(idx, 1)
  else selectedClientIds.value.push(id)
}

async function handleSubmit(): Promise<void> {
  if (!canSubmit.value) return
  loading.value = true
  error.value = null

  const request: CreateRealmUserRequest = {
    email: email.value,
    displayName: displayName.value || undefined,
    firstName: firstName.value || undefined,
    lastName: lastName.value || undefined,
    phone: phone.value || undefined,
    jobTitle: jobTitle.value || undefined,
    department: department.value || undefined,
    clientIds: selectedClientIds.value.length > 0 ? selectedClientIds.value : undefined,
  }

  try {
    await usersApi.create(realmName.value, request)
    toast.success('User created', `"${email.value}" created successfully`)
    router.push(`/realms/${realmName.value}/users`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to create user'
    toast.error('Error', error.value ?? undefined)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <SidebarLayout>
    <div class="mx-auto max-w-2xl px-4 py-8 sm:px-6">
      <div class="mb-6 flex items-center gap-3">
        <button
          class="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-800 hover:text-white"
          @click="router.push(`/realms/${realmName}/users`)"
        >
          <ArrowLeftIcon class="h-5 w-5" />
        </button>
        <div>
          <h1 class="text-xl font-semibold text-white">Add User</h1>
          <p class="font-mono text-sm text-zinc-400">{{ realmName }}</p>
        </div>
      </div>

      <form class="space-y-6" @submit.prevent="handleSubmit">
        <div
          v-if="error"
          class="rounded-lg border border-red-500/20 bg-red-500/10 p-3 text-sm text-red-400"
        >
          {{ error }}
        </div>

        <!-- Account -->
        <div class="rounded-xl border border-zinc-800 bg-zinc-900 p-6">
          <h2 class="mb-4 text-sm font-semibold uppercase tracking-wider text-zinc-500">Account</h2>
          <div class="space-y-1.5">
            <label class="block text-sm font-medium text-zinc-300" for="email">
              Email <span class="text-red-400">*</span>
            </label>
            <AppInput
              id="email"
              v-model="email"
              type="email"
              placeholder="user@example.com"
              :invalid="email.length > 0 && !isValidEmail"
            />
            <p v-if="email.length > 0 && !isValidEmail" class="text-xs text-red-400">
              Please enter a valid email address.
            </p>
          </div>
        </div>

        <!-- Profile -->
        <div class="rounded-xl border border-zinc-800 bg-zinc-900 p-6">
          <h2 class="mb-4 text-sm font-semibold uppercase tracking-wider text-zinc-500">Profile</h2>
          <div class="space-y-4">
            <div class="grid grid-cols-2 gap-4">
              <div class="space-y-1.5">
                <label class="block text-sm font-medium text-zinc-300" for="firstName">First Name</label>
                <AppInput id="firstName" v-model="firstName" placeholder="John" />
              </div>
              <div class="space-y-1.5">
                <label class="block text-sm font-medium text-zinc-300" for="lastName">Last Name</label>
                <AppInput id="lastName" v-model="lastName" placeholder="Doe" />
              </div>
            </div>
            <div class="space-y-1.5">
              <label class="block text-sm font-medium text-zinc-300" for="displayName">Display Name</label>
              <AppInput id="displayName" v-model="displayName" placeholder="Johnny" />
            </div>
            <div class="space-y-1.5">
              <label class="block text-sm font-medium text-zinc-300" for="phone">Phone</label>
              <AppInput id="phone" v-model="phone" placeholder="+1 234 567 8900" />
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div class="space-y-1.5">
                <label class="block text-sm font-medium text-zinc-300" for="jobTitle">Job Title</label>
                <AppInput id="jobTitle" v-model="jobTitle" placeholder="Software Engineer" />
              </div>
              <div class="space-y-1.5">
                <label class="block text-sm font-medium text-zinc-300" for="department">Department</label>
                <AppInput id="department" v-model="department" placeholder="Engineering" />
              </div>
            </div>
          </div>
        </div>

        <!-- Authorized Clients -->
        <div class="rounded-xl border border-zinc-800 bg-zinc-900 p-6">
          <h2 class="mb-1 text-sm font-semibold uppercase tracking-wider text-zinc-500">
            Authorized Clients
          </h2>
          <p class="mb-4 text-xs text-zinc-500">
            Select which applications this user can access. Leave empty for realm-wide access.
          </p>

          <div v-if="loadingClients" class="text-sm text-zinc-500">Loading clients…</div>
          <div v-else-if="clients.length === 0" class="text-sm text-zinc-500">
            No public clients found in this realm.
          </div>
          <div v-else class="space-y-2">
            <label
              v-for="client in clients"
              :key="client.id"
              class="flex cursor-pointer items-center gap-3 rounded-lg border border-zinc-700 px-3 py-2.5 hover:border-zinc-600"
              :class="selectedClientIds.includes(client.id) ? 'border-indigo-500/50 bg-indigo-500/5' : ''"
            >
              <input
                type="checkbox"
                :value="client.id"
                :checked="selectedClientIds.includes(client.id)"
                class="h-4 w-4 rounded border-zinc-600 bg-zinc-800 text-indigo-500 focus:ring-indigo-500"
                @change="toggleClient(client.id)"
              />
              <div class="min-w-0">
                <p class="truncate text-sm font-medium text-white">
                  {{ client.name || client.clientId }}
                </p>
                <p class="truncate font-mono text-xs text-zinc-400">{{ client.clientId }}</p>
              </div>
              <XMarkIcon
                v-if="selectedClientIds.includes(client.id)"
                class="ml-auto h-4 w-4 shrink-0 text-indigo-400"
              />
            </label>
          </div>
        </div>

        <div class="flex justify-end gap-3">
          <AppButton outline type="button" @click="router.push(`/realms/${realmName}/users`)">
            Cancel
          </AppButton>
          <AppButton color="indigo" type="submit" :loading="loading" :disabled="!canSubmit">
            Create User
          </AppButton>
        </div>
      </form>
    </div>
  </SidebarLayout>
</template>
