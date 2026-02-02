<script setup lang="ts">
import { useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import type { Client } from '@/types'

const props = defineProps<{
  clients: Client[]
  realmName: string
}>()

const router = useRouter()

function navigateToCreateClient(): void {
  router.push(`/realms/${props.realmName}/clients/create`)
}

function navigateToClient(clientId: string): void {
  router.push(`/realms/${props.realmName}/clients/${clientId}`)
}
</script>

<template>
  <div class="client-list">
    <div class="list-header">
      <Button
        label="Add Client"
        icon="pi pi-plus"
        size="small"
        @click="navigateToCreateClient"
      />
    </div>

    <div v-if="clients.length === 0" class="empty-state">
      <i class="pi pi-desktop empty-icon" />
      <p>No clients configured</p>
    </div>

    <DataTable
      v-else
      :value="clients"
      striped-rows
      scrollable
      scroll-height="400px"
      class="client-table"
    >
      <Column field="clientId" header="Client ID" sortable>
        <template #body="slotProps">
          <span class="client-id clickable" @click="navigateToClient(slotProps.data.clientId)">
            {{ slotProps.data.clientId }}
          </span>
        </template>
      </Column>

      <Column field="name" header="Name">
        <template #body="slotProps">
          {{ slotProps.data.name || '-' }}
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

      <Column field="publicClient" header="Type">
        <template #body="slotProps">
          <Tag
            :value="slotProps.data.publicClient ? 'Public' : 'Confidential'"
            :severity="slotProps.data.publicClient ? 'info' : 'warn'"
          />
        </template>
      </Column>

      <Column header="Flows">
        <template #body="slotProps">
          <div class="flow-tags">
            <Tag
              v-if="slotProps.data.standardFlowEnabled"
              value="Standard"
              severity="secondary"
              class="flow-tag"
            />
            <Tag
              v-if="slotProps.data.directAccessGrantsEnabled"
              value="Direct"
              severity="secondary"
              class="flow-tag"
            />
            <Tag
              v-if="slotProps.data.serviceAccountsEnabled"
              value="Service"
              severity="secondary"
              class="flow-tag"
            />
          </div>
        </template>
      </Column>

      <Column header="Actions" style="width: 100px">
        <template #body="slotProps">
          <Button
            icon="pi pi-eye"
            text
            rounded
            size="small"
            @click="navigateToClient(slotProps.data.clientId)"
          />
        </template>
      </Column>
    </DataTable>
  </div>
</template>

<style scoped>
.client-list {
  padding: 1rem 0;
}

.list-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 1rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem;
  color: var(--p-text-muted-color);
}

.empty-icon {
  font-size: 2.5rem;
  margin-bottom: 1rem;
  opacity: 0.5;
}

.client-id {
  font-family: monospace;
  font-weight: 500;
}

.client-id.clickable {
  cursor: pointer;
  color: var(--p-primary-color);
}

.client-id.clickable:hover {
  text-decoration: underline;
}

.flow-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.flow-tag {
  font-size: 0.7rem;
}
</style>
