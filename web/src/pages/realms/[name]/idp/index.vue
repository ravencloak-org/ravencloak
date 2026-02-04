<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { idpApi } from '@/api'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import ProgressSpinner from 'primevue/progressspinner'
import type { IdentityProvider } from '@/types'

defineOptions({
  name: 'RealmIdpPage'
})

const route = useRoute()
const router = useRouter()
const toast = useToast()

const realmName = computed(() => route.params.name as string)
const providers = ref<IdentityProvider[]>([])
const loading = ref(true)

onMounted(async () => {
  await loadProviders()
})

async function loadProviders(): Promise<void> {
  loading.value = true
  try {
    providers.value = await idpApi.list(realmName.value)
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load identity providers',
      life: 5000
    })
  } finally {
    loading.value = false
  }
}

function navigateBack(): void {
  router.push(`/realms/${realmName.value}`)
}

function navigateToCreate(): void {
  router.push(`/realms/${realmName.value}/idp/create`)
}

function getProviderIcon(providerId: string): string {
  switch (providerId) {
    case 'google': return 'pi pi-google'
    case 'github': return 'pi pi-github'
    case 'facebook': return 'pi pi-facebook'
    case 'microsoft': return 'pi pi-microsoft'
    case 'saml': return 'pi pi-key'
    case 'oidc': return 'pi pi-lock'
    default: return 'pi pi-link'
  }
}
</script>

<template>
  <div class="idp-page">
    <div class="page-header">
      <Button
        icon="pi pi-arrow-left"
        text
        rounded
        @click="navigateBack"
      />
      <div class="header-content">
        <h1>Identity Providers</h1>
        <p>{{ realmName }}</p>
      </div>
      <Button
        label="Add Provider"
        icon="pi pi-plus"
        @click="navigateToCreate"
      />
    </div>

    <div v-if="loading" class="loading-container">
      <ProgressSpinner />
    </div>

    <div v-else-if="providers.length === 0" class="empty-state">
      <i class="pi pi-link empty-icon" />
      <h2>No Identity Providers</h2>
      <p>Add identity providers to enable SSO with external systems.</p>
      <Button
        label="Add Your First Provider"
        icon="pi pi-plus"
        @click="navigateToCreate"
      />
    </div>

    <DataTable
      v-else
      :value="providers"
      striped-rows
      class="idp-table"
    >
      <Column field="alias" header="Alias" sortable>
        <template #body="slotProps">
          <div class="provider-alias">
            <i :class="getProviderIcon(slotProps.data.providerId)" />
            <span>{{ slotProps.data.alias }}</span>
          </div>
        </template>
      </Column>

      <Column field="displayName" header="Display Name">
        <template #body="slotProps">
          {{ slotProps.data.displayName || '-' }}
        </template>
      </Column>

      <Column field="providerId" header="Type" sortable>
        <template #body="slotProps">
          <Tag
            :value="slotProps.data.providerId.toUpperCase()"
            severity="info"
          />
        </template>
      </Column>

      <Column field="enabled" header="Status" sortable>
        <template #body="slotProps">
          <Tag
            :value="slotProps.data.enabled ? 'Enabled' : 'Disabled'"
            :severity="slotProps.data.enabled ? 'success' : 'danger'"
          />
        </template>
      </Column>

      <Column field="trustEmail" header="Trust Email">
        <template #body="slotProps">
          <Tag
            :value="slotProps.data.trustEmail ? 'Yes' : 'No'"
            :severity="slotProps.data.trustEmail ? 'success' : 'secondary'"
          />
        </template>
      </Column>
    </DataTable>
  </div>
</template>

<style scoped>
.idp-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  margin-bottom: 1.5rem;
}

.header-content {
  flex: 1;
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

.loading-container {
  display: flex;
  justify-content: center;
  padding: 3rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem;
  text-align: center;
  background-color: var(--p-surface-card);
  border-radius: var(--p-border-radius);
  border: 1px dashed var(--p-surface-border);
}

.empty-icon {
  font-size: 4rem;
  color: var(--p-text-muted-color);
  margin-bottom: 1rem;
}

.empty-state h2 {
  margin: 0 0 0.5rem;
  font-size: 1.25rem;
  font-weight: 600;
}

.empty-state p {
  margin: 0 0 1.5rem;
  color: var(--p-text-muted-color);
}

.provider-alias {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 500;
}
</style>
