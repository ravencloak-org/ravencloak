<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { groupsApi } from '@/api'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Message from 'primevue/message'
import type { CreateGroupRequest } from '@/types'

defineOptions({
  name: 'CreateGroupPage'
})

const route = useRoute()
const router = useRouter()
const toast = useToast()

const realmName = computed(() => route.params.name as string)

const groupName = ref('')

const loading = ref(false)
const error = ref<string | null>(null)

const isValidName = computed(() => {
  return groupName.value.length >= 2
})

const canSubmit = computed(() => {
  return isValidName.value && !loading.value
})

async function handleSubmit(): Promise<void> {
  if (!canSubmit.value) return

  loading.value = true
  error.value = null

  const request: CreateGroupRequest = {
    name: groupName.value
  }

  try {
    await groupsApi.create(realmName.value, request)
    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: `Group "${groupName.value}" created successfully`,
      life: 3000
    })
    router.push(`/realms/${realmName.value}`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to create group'
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.value,
      life: 5000
    })
  } finally {
    loading.value = false
  }
}

function handleCancel(): void {
  router.push(`/realms/${realmName.value}`)
}
</script>

<template>
  <div class="create-group-page">
    <div class="page-header">
      <Button
        icon="pi pi-arrow-left"
        text
        rounded
        @click="handleCancel"
      />
      <div class="header-content">
        <h1>Create Group</h1>
        <p>{{ realmName }}</p>
      </div>
    </div>

    <Card class="form-card">
      <template #content>
        <Message
          v-if="error"
          severity="error"
          :closable="true"
          class="form-error"
          @close="error = null"
        >
          {{ error }}
        </Message>

        <form @submit.prevent="handleSubmit" class="form">
          <div class="form-field">
            <label for="groupName">Group Name *</label>
            <InputText
              id="groupName"
              v-model="groupName"
              placeholder="My Group"
              :invalid="groupName.length > 0 && !isValidName"
              class="w-full"
            />
            <small class="field-help">
              Must be at least 2 characters long.
            </small>
          </div>

          <div class="form-actions">
            <Button
              type="button"
              label="Cancel"
              severity="secondary"
              @click="handleCancel"
            />
            <Button
              type="submit"
              label="Create Group"
              icon="pi pi-check"
              :loading="loading"
              :disabled="!canSubmit"
            />
          </div>
        </form>
      </template>
    </Card>
  </div>
</template>

<style scoped>
.create-group-page {
  max-width: 600px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  margin-bottom: 1.5rem;
}

.header-content h1 {
  margin: 0 0 0.25rem;
  font-size: 1.5rem;
  font-weight: 600;
}

.header-content p {
  margin: 0;
  color: var(--p-text-muted-color);
  font-family: monospace;
}

.form-card {
  background-color: var(--p-surface-card);
}

.form-error {
  margin-bottom: 1.5rem;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-field label {
  font-weight: 500;
  color: var(--p-text-color);
}

.field-help {
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding-top: 1rem;
  border-top: 1px solid var(--p-surface-border);
}

.w-full {
  width: 100%;
}
</style>
