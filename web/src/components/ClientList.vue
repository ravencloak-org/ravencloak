<script setup lang="ts">
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import type { Client } from '@/types'

defineProps<{
  clients: Client[]
}>()
</script>

<template>
  <div class="client-list">
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
          <span class="client-id">{{ slotProps.data.clientId }}</span>
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
    </DataTable>
  </div>
</template>

<style scoped>
.client-list {
  padding: 1rem 0;
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

.flow-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.flow-tag {
  font-size: 0.7rem;
}
</style>
