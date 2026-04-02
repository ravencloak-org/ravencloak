<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useRealmStore } from '@/stores/realm'
import { useToast } from '@/composables/useToast'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppInput from '@/components/ui/AppInput.vue'
import type { CreateRealmRequest } from '@/types'

const router = useRouter()
const realmStore = useRealmStore()
const toast = useToast()

const realmName = ref('')
const displayName = ref('')
const enableSpi = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)

const isValidName = computed(() => {
  return /^[a-z][a-z0-9-]*$/.test(realmName.value) && realmName.value.length >= 2
})

const nameError = computed(() => {
  if (realmName.value.length === 0) return undefined
  if (!isValidName.value) {
    return 'Must start with a letter and contain only lowercase letters, numbers, and hyphens (min 2 chars).'
  }
  return undefined
})

const canSubmit = computed(() => {
  return isValidName.value && !loading.value
})

async function handleSubmit(): Promise<void> {
  if (!canSubmit.value) return

  loading.value = true
  error.value = null

  const request: CreateRealmRequest = {
    realmName: realmName.value,
    displayName: displayName.value || undefined,
    enableUserStorageSpi: enableSpi.value,
  }

  try {
    await realmStore.createRealm(request)
    toast.success('Realm created', `"${realmName.value}" has been created successfully`)
    router.push(`/realms/${realmName.value}`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to create realm'
    toast.error('Failed to create realm', error.value)
  } finally {
    loading.value = false
  }
}

function handleCancel(): void {
  router.push('/realms')
}
</script>

<template>
  <SidebarLayout>
    <div class="max-w-lg mx-auto">
      <!-- Page header -->
      <div class="mb-8">
        <h1 class="text-2xl font-semibold text-zinc-900 dark:text-zinc-100">Create Realm</h1>
        <p class="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          Set up a new authentication realm in Keycloak
        </p>
      </div>

      <!-- Form card -->
      <div class="bg-white dark:bg-zinc-900 rounded-lg ring-1 ring-zinc-200 dark:ring-zinc-800 p-6">
        <!-- Error banner -->
        <div
          v-if="error"
          class="mb-6 rounded-lg bg-red-50 dark:bg-red-500/10 p-3 text-sm text-red-700 dark:text-red-400 flex items-start gap-2"
        >
          <svg class="h-5 w-5 flex-shrink-0 mt-0.5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z" />
          </svg>
          <span>{{ error }}</span>
        </div>

        <form
          class="flex flex-col gap-5"
          @submit.prevent="handleSubmit"
        >
          <!-- Realm name -->
          <div>
            <AppInput
              v-model="realmName"
              label="Realm Name"
              placeholder="my-realm"
              :error="nameError"
            />
            <p
              v-if="!nameError"
              class="mt-1.5 text-xs text-zinc-500 dark:text-zinc-400"
            >
              Must start with a letter and contain only lowercase letters, numbers, and hyphens.
            </p>
          </div>

          <!-- Display name -->
          <div>
            <AppInput
              v-model="displayName"
              label="Display Name"
              placeholder="My Realm"
            />
            <p class="mt-1.5 text-xs text-zinc-500 dark:text-zinc-400">
              A friendly name shown to users. Defaults to the realm name if not provided.
            </p>
          </div>

          <!-- Enable SPI toggle -->
          <div class="flex items-start gap-3 pt-2">
            <button
              type="button"
              role="switch"
              :aria-checked="enableSpi"
              :class="[
                enableSpi ? 'bg-primary-600' : 'bg-zinc-200 dark:bg-zinc-700',
                'relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-600 focus:ring-offset-2 dark:focus:ring-offset-zinc-900 mt-0.5',
              ]"
              @click="enableSpi = !enableSpi"
            >
              <span
                :class="[
                  enableSpi ? 'translate-x-4' : 'translate-x-0.5',
                  'pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out mt-0.5',
                ]"
              />
            </button>
            <div>
              <p class="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                Enable User Storage SPI
              </p>
              <p class="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
                Allow this realm to validate users against the external user database.
              </p>
            </div>
          </div>

          <!-- Actions -->
          <div class="flex justify-end gap-3 pt-4 mt-2 border-t border-zinc-200 dark:border-zinc-800">
            <AppButton
              variant="secondary"
              type="button"
              @click="handleCancel"
            >
              Cancel
            </AppButton>
            <AppButton
              type="submit"
              :loading="loading"
              :disabled="!canSubmit"
            >
              Create Realm
            </AppButton>
          </div>
        </form>
      </div>
    </div>
  </SidebarLayout>
</template>
