<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { groupsApi } from '@/api/groups'
import { useToast } from '@/composables/useToast'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppSlideOver from '@/components/ui/AppSlideOver.vue'
import AppInput from '@/components/ui/AppInput.vue'
import AppEmptyState from '@/components/ui/AppEmptyState.vue'
import {
  UserGroupIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  PlusIcon,
} from '@heroicons/vue/24/outline'
import type { Group, CreateGroupRequest } from '@/types'

interface FlatGroup {
  group: Group
  depth: number
  hasChildren: boolean
}

const route = useRoute()
const toast = useToast()

const realmName = computed(() => route.params.name as string)

const groups = ref<Group[]>([])
const loading = ref(true)
const expandedGroups = ref<Set<string>>(new Set())

// Create group slide-over
const slideOverOpen = ref(false)
const parentGroupForCreate = ref<Group | null>(null)
const formName = ref('')
const formSubmitting = ref(false)
const formError = ref<string | null>(null)

const slideOverTitle = computed(() =>
  parentGroupForCreate.value
    ? `Add Subgroup to "${parentGroupForCreate.value.name}"`
    : 'Create Group',
)

function flattenGroups(items: Group[], depth: number): FlatGroup[] {
  const result: FlatGroup[] = []
  for (const group of items) {
    const hasChildren = !!group.subGroups && group.subGroups.length > 0
    result.push({ group, depth, hasChildren })
    if (hasChildren && expandedGroups.value.has(group.id)) {
      result.push(...flattenGroups(group.subGroups!, depth + 1))
    }
  }
  return result
}

const flatGroupList = computed(() => flattenGroups(groups.value, 0))

onMounted(async () => {
  await loadGroups()
})

async function loadGroups(): Promise<void> {
  loading.value = true
  try {
    groups.value = await groupsApi.list(realmName.value)
  } catch (err) {
    toast.error('Failed to load groups', err instanceof Error ? err.message : undefined)
  } finally {
    loading.value = false
  }
}

function toggleGroup(groupId: string): void {
  if (expandedGroups.value.has(groupId)) {
    expandedGroups.value.delete(groupId)
  } else {
    expandedGroups.value.add(groupId)
  }
}

function openCreateSlideOver(parent?: Group): void {
  parentGroupForCreate.value = parent ?? null
  formName.value = ''
  formError.value = null
  slideOverOpen.value = true
}

async function handleCreateGroup(): Promise<void> {
  if (!formName.value.trim()) {
    formError.value = 'Group name is required'
    return
  }

  formSubmitting.value = true
  formError.value = null

  const request: CreateGroupRequest = {
    name: formName.value.trim(),
  }

  try {
    if (parentGroupForCreate.value) {
      await groupsApi.createSubgroup(realmName.value, parentGroupForCreate.value.id, request)
      toast.success('Subgroup created', `"${request.name}" has been added`)
    } else {
      await groupsApi.create(realmName.value, request)
      toast.success('Group created', `"${request.name}" has been created`)
    }
    slideOverOpen.value = false
    await loadGroups()
  } catch (err) {
    formError.value = err instanceof Error ? err.message : 'Failed to create group'
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
          <h1 class="text-2xl font-semibold text-zinc-900 dark:text-zinc-100">Groups</h1>
          <p class="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Manage group hierarchy for
            <span class="font-mono">{{ realmName }}</span>
          </p>
        </div>
        <AppButton @click="openCreateSlideOver()">
          <PlusIcon class="h-4 w-4" />
          Create Group
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
          v-if="groups.length === 0"
          title="No groups found"
          description="Create groups to organize users and assign roles collectively."
          :icon="UserGroupIcon"
        >
          <AppButton @click="openCreateSlideOver()">
            <PlusIcon class="h-4 w-4" />
            Create Group
          </AppButton>
        </AppEmptyState>

        <!-- Groups tree (flattened) -->
        <div
          v-else
          class="bg-white dark:bg-zinc-900 rounded-lg ring-1 ring-zinc-200 dark:ring-zinc-800 divide-y divide-zinc-100 dark:divide-zinc-800"
        >
          <div
            v-for="({ group, depth, hasChildren }) in flatGroupList"
            :key="group.id"
            class="flex items-center gap-2 px-4 py-3 hover:bg-zinc-50 dark:hover:bg-zinc-800/50 transition-colors"
            :style="{ paddingLeft: `${depth * 24 + 16}px` }"
          >
            <!-- Expand/collapse chevron -->
            <button
              v-if="hasChildren"
              type="button"
              class="flex-shrink-0 p-0.5 rounded text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300"
              @click="toggleGroup(group.id)"
            >
              <ChevronDownIcon
                v-if="expandedGroups.has(group.id)"
                class="h-4 w-4"
              />
              <ChevronRightIcon
                v-else
                class="h-4 w-4"
              />
            </button>
            <span
              v-else
              class="flex-shrink-0 w-5"
            />

            <!-- Group info -->
            <div class="min-w-0 flex-1">
              <span class="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                {{ group.name }}
              </span>
              <span class="ml-2 text-xs text-zinc-400 dark:text-zinc-500 font-mono">
                {{ group.path }}
              </span>
            </div>

            <!-- Add subgroup button -->
            <button
              type="button"
              class="flex-shrink-0 p-1.5 rounded-lg text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
              title="Add subgroup"
              @click.stop="openCreateSlideOver(group)"
            >
              <PlusIcon class="h-4 w-4" />
            </button>
          </div>
        </div>
      </template>

      <!-- Create Group Slide-Over -->
      <AppSlideOver
        :open="slideOverOpen"
        :title="slideOverTitle"
        @close="slideOverOpen = false"
      >
        <form
          class="flex flex-col gap-5"
          @submit.prevent="handleCreateGroup"
        >
          <!-- Error -->
          <div
            v-if="formError"
            class="rounded-lg bg-red-50 dark:bg-red-500/10 p-3 text-sm text-red-700 dark:text-red-400"
          >
            {{ formError }}
          </div>

          <div v-if="parentGroupForCreate">
            <p class="text-sm text-zinc-500 dark:text-zinc-400 mb-4">
              This subgroup will be created under
              <span class="font-medium text-zinc-900 dark:text-zinc-100">{{ parentGroupForCreate.path }}</span>
            </p>
          </div>

          <AppInput
            v-model="formName"
            label="Group Name"
            placeholder="e.g. Engineering, Marketing"
          />

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
              {{ parentGroupForCreate ? 'Add Subgroup' : 'Create Group' }}
            </AppButton>
          </div>
        </form>
      </AppSlideOver>
    </div>
  </SidebarLayout>
</template>
