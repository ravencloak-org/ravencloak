<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { usersApi } from '@/api/users'
import { clientsApi } from '@/api'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import MultiSelect from 'primevue/multiselect'
import Message from 'primevue/message'
import type { CreateRealmUserRequest, Client } from '@/types'

defineOptions({
  name: 'CreateUserPage'
})

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
const selectedClients = ref<string[]>([])

const clients = ref<Client[]>([])
const loading = ref(false)
const loadingClients = ref(true)
const error = ref<string | null>(null)

const isValidEmail = computed(() => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email.value)
})

const canSubmit = computed(() => {
  return isValidEmail.value && !loading.value
})

onMounted(async () => {
  await loadClients()
})

async function loadClients(): Promise<void> {
  loadingClients.value = true
  try {
    const allClients = await clientsApi.list(realmName.value)
    // Only show public (frontend) clients - backend clients are for service-to-service auth
    clients.value = allClients.filter(c => c.publicClient)
  } catch (err) {
    console.error('Failed to load clients:', err)
  } finally {
    loadingClients.value = false
  }
}

async function handleSubmit(): Promise<void> {
  if (!canSubmit.value) return

  // Capture realm name before async operation
  const currentRealm = realmName.value
  if (!currentRealm) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Realm name not found',
      life: 5000
    })
    return
  }

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
    clientIds: selectedClients.value.length > 0 ? selectedClients.value : undefined
  }

  try {
    await usersApi.create(currentRealm, request)
    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: `User "${email.value}" created successfully`,
      life: 3000
    })
    // Navigate back to realm detail page (same as client create does)
    router.push(`/realms/${currentRealm}`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to create user'
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
  const currentRealm = realmName.value
  if (currentRealm) {
    router.push(`/realms/${currentRealm}`)
  } else {
    router.push('/realms')
  }
}
</script>

<template>
  <div class="create-user-page">
    <div class="page-header">
      <Button
        icon="pi pi-arrow-left"
        text
        rounded
        @click="handleCancel"
      />
      <div class="header-content">
        <h1>Add User</h1>
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
          <div class="form-section">
            <h3>Account</h3>

            <div class="form-field">
              <label for="email">Email *</label>
              <InputText
                id="email"
                v-model="email"
                placeholder="user@example.com"
                :invalid="email.length > 0 && !isValidEmail"
                class="w-full"
              />
              <small v-if="email.length > 0 && !isValidEmail" class="field-error">
                Please enter a valid email address
              </small>
            </div>
          </div>

          <div class="form-section">
            <h3>Profile</h3>

            <div class="form-row">
              <div class="form-field">
                <label for="firstName">First Name</label>
                <InputText
                  id="firstName"
                  v-model="firstName"
                  placeholder="John"
                  class="w-full"
                />
              </div>

              <div class="form-field">
                <label for="lastName">Last Name</label>
                <InputText
                  id="lastName"
                  v-model="lastName"
                  placeholder="Doe"
                  class="w-full"
                />
              </div>
            </div>

            <div class="form-field">
              <label for="displayName">Display Name</label>
              <InputText
                id="displayName"
                v-model="displayName"
                placeholder="Johnny"
                class="w-full"
              />
            </div>

            <div class="form-field">
              <label for="phone">Phone</label>
              <InputText
                id="phone"
                v-model="phone"
                placeholder="+1 234 567 8900"
                class="w-full"
              />
            </div>

            <div class="form-row">
              <div class="form-field">
                <label for="jobTitle">Job Title</label>
                <InputText
                  id="jobTitle"
                  v-model="jobTitle"
                  placeholder="Software Engineer"
                  class="w-full"
                />
              </div>

              <div class="form-field">
                <label for="department">Department</label>
                <InputText
                  id="department"
                  v-model="department"
                  placeholder="Engineering"
                  class="w-full"
                />
              </div>
            </div>
          </div>

          <div class="form-section">
            <h3>Authorization</h3>

            <div class="form-field">
              <label for="clients">Authorized Clients</label>
              <MultiSelect
                id="clients"
                v-model="selectedClients"
                :options="clients"
                optionLabel="name"
                optionValue="id"
                placeholder="Select clients"
                :loading="loadingClients"
                class="w-full"
                display="chip"
              >
                <template #option="{ option }">
                  <div class="client-option">
                    <span>{{ option.name || option.clientId }}</span>
                    <small>{{ option.clientId }}</small>
                  </div>
                </template>
              </MultiSelect>
              <small class="field-help">
                Select which applications this user can access. Leave empty for realm-wide access.
              </small>
            </div>
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
              label="Create User"
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
.create-user-page {
  max-width: 700px;
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
  gap: 2rem;
}

.form-section h3 {
  margin: 0 0 1rem;
  font-size: 1rem;
  font-weight: 600;
  color: var(--p-text-color);
  border-bottom: 1px solid var(--p-surface-border);
  padding-bottom: 0.5rem;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.form-field:last-child {
  margin-bottom: 0;
}

.form-field label {
  font-weight: 500;
  color: var(--p-text-color);
}

.field-help {
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}

.field-error {
  color: var(--p-red-500);
  font-size: 0.875rem;
}

.client-option {
  display: flex;
  flex-direction: column;
}

.client-option small {
  color: var(--p-text-muted-color);
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
