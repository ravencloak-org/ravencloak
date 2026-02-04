<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useRealmStore } from '@/stores/realm'
import { useToast } from 'primevue/usetoast'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Checkbox from 'primevue/checkbox'
import Button from 'primevue/button'
import Message from 'primevue/message'
import type { CreateRealmRequest } from '@/types'

defineOptions({
  name: 'CreateRealmPage'
})

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
    enableUserStorageSpi: enableSpi.value
  }

  try {
    await realmStore.createRealm(request)
    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: `Realm "${realmName.value}" created successfully`,
      life: 3000
    })
    router.push(`/realms/${realmName.value}`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to create realm'
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
  router.push('/realms')
}
</script>

<template>
  <div class="create-realm-view">
    <div class="page-header">
      <Button
        icon="pi pi-arrow-left"
        text
        rounded
        @click="handleCancel"
      />
      <div class="header-content">
        <h1>Create Realm</h1>
        <p>Set up a new authentication realm</p>
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
            <label for="realmName">Realm Name *</label>
            <InputText
              id="realmName"
              v-model="realmName"
              placeholder="my-realm"
              :invalid="realmName.length > 0 && !isValidName"
              class="w-full"
            />
            <small class="field-help">
              Must start with a letter and contain only lowercase letters, numbers, and hyphens.
            </small>
          </div>

          <div class="form-field">
            <label for="displayName">Display Name</label>
            <InputText
              id="displayName"
              v-model="displayName"
              placeholder="My Realm"
              class="w-full"
            />
            <small class="field-help">
              A friendly name shown to users. Defaults to the realm name if not provided.
            </small>
          </div>

          <div class="form-field checkbox-field">
            <Checkbox
              id="enableSpi"
              v-model="enableSpi"
              :binary="true"
            />
            <label for="enableSpi" class="checkbox-label">
              <span>Enable User Storage SPI</span>
              <small>Allow this realm to validate users against the external user database.</small>
            </label>
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
              label="Create Realm"
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
.create-realm-view {
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
  color: var(--p-text-color);
}

.header-content p {
  margin: 0;
  color: var(--p-text-muted-color);
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

.checkbox-field {
  flex-direction: row;
  align-items: flex-start;
  gap: 0.75rem;
}

.checkbox-label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  cursor: pointer;
}

.checkbox-label span {
  font-weight: 500;
  color: var(--p-text-color);
}

.checkbox-label small {
  color: var(--p-text-muted-color);
  font-weight: 400;
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
