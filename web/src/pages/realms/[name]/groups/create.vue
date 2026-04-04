<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import { groupsApi } from '@/api'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppInput from '@/components/ui/AppInput.vue'
import { ArrowLeftIcon } from '@heroicons/vue/24/outline'
import type { CreateGroupRequest } from '@/types'

defineOptions({ name: 'CreateGroupPage' })

const route = useRoute()
const router = useRouter()
const toast = useToast()

const realmName = computed(() => route.params.name as string)
const groupName = ref('')
const loading = ref(false)
const error = ref<string | null>(null)

const isValidName = computed(() => groupName.value.trim().length >= 2)
const canSubmit = computed(() => isValidName.value && !loading.value)

async function handleSubmit(): Promise<void> {
  if (!canSubmit.value) return
  loading.value = true
  error.value = null
  const request: CreateGroupRequest = { name: groupName.value.trim() }
  try {
    await groupsApi.create(realmName.value, request)
    toast.success('Group created', `"${groupName.value}" created successfully`)
    router.push(`/realms/${realmName.value}/groups`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to create group'
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
          @click="router.push(`/realms/${realmName}/groups`)"
        >
          <ArrowLeftIcon class="h-5 w-5" />
        </button>
        <div>
          <h1 class="text-xl font-semibold text-white">Create Group</h1>
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
            <label class="block text-sm font-medium text-zinc-300" for="groupName">
              Group Name <span class="text-red-400">*</span>
            </label>
            <AppInput
              id="groupName"
              v-model="groupName"
              placeholder="My Group"
              :invalid="groupName.length > 0 && !isValidName"
            />
            <p v-if="groupName.length > 0 && !isValidName" class="text-xs text-red-400">
              Must be at least 2 characters.
            </p>
          </div>

          <div class="flex justify-end gap-3 border-t border-zinc-800 pt-5">
            <AppButton
              outline
              type="button"
              @click="router.push(`/realms/${realmName}/groups`)"
            >
              Cancel
            </AppButton>
            <AppButton color="indigo" type="submit" :loading="loading" :disabled="!canSubmit">
              Create Group
            </AppButton>
          </div>
        </form>
      </div>
    </div>
  </SidebarLayout>
</template>
