<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import { rolesApi } from '@/api'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppInput from '@/components/ui/AppInput.vue'
import { ArrowLeftIcon } from '@heroicons/vue/24/outline'
import type { CreateRoleRequest } from '@/types'

defineOptions({ name: 'CreateRolePage' })

const route = useRoute()
const router = useRouter()
const toast = useToast()

const realmName = computed(() => route.params.name as string)
const roleName = ref('')
const description = ref('')
const loading = ref(false)
const error = ref<string | null>(null)

const isValidName = computed(
  () => /^[a-z][a-z0-9_-]*$/.test(roleName.value) && roleName.value.length >= 2,
)
const canSubmit = computed(() => isValidName.value && !loading.value)

async function handleSubmit(): Promise<void> {
  if (!canSubmit.value) return
  loading.value = true
  error.value = null
  const request: CreateRoleRequest = {
    name: roleName.value,
    description: description.value || undefined,
  }
  try {
    await rolesApi.createRealmRole(realmName.value, request)
    toast.success('Role created', `"${roleName.value}" created successfully`)
    router.push(`/realms/${realmName.value}/roles`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to create role'
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
          @click="router.push(`/realms/${realmName}/roles`)"
        >
          <ArrowLeftIcon class="h-5 w-5" />
        </button>
        <div>
          <h1 class="text-xl font-semibold text-white">Create Role</h1>
          <p class="font-mono text-sm text-zinc-400">{{ realmName }}</p>
        </div>
      </div>

      <div class="rounded-xl border border-zinc-800 bg-zinc-900 p-6">
        <div
          v-if="error"
          class="mb-5 rounded-lg border border-red-500/20 bg-red-500/10 p-3 text-sm text-red-400"
        >
          {{ error }}
        </div>

        <form class="space-y-5" @submit.prevent="handleSubmit">
          <div class="space-y-1.5">
            <label class="block text-sm font-medium text-zinc-300" for="roleName">
              Role Name <span class="text-red-400">*</span>
            </label>
            <AppInput
              id="roleName"
              v-model="roleName"
              placeholder="my-role"
              :invalid="roleName.length > 0 && !isValidName"
            />
            <p v-if="roleName.length > 0 && !isValidName" class="text-xs text-red-400">
              Lowercase letters, numbers, underscores and hyphens only. Must start with a letter.
            </p>
            <p v-else class="text-xs text-zinc-500">
              Must start with a letter, e.g. <code class="text-zinc-400">realm-admin</code>
            </p>
          </div>

          <div class="space-y-1.5">
            <label class="block text-sm font-medium text-zinc-300" for="description">
              Description
            </label>
            <textarea
              id="description"
              v-model="description"
              rows="3"
              placeholder="Optional description"
              class="w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-white placeholder-zinc-500 hover:border-white/20 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
          </div>

          <div class="flex justify-end gap-3 border-t border-zinc-800 pt-5">
            <AppButton
              outline
              type="button"
              @click="router.push(`/realms/${realmName}/roles`)"
            >
              Cancel
            </AppButton>
            <AppButton color="indigo" type="submit" :loading="loading" :disabled="!canSubmit">
              Create Role
            </AppButton>
          </div>
        </form>
      </div>
    </div>
  </SidebarLayout>
</template>
