<script setup lang="ts">
import { useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import type { Role } from '@/types'

const props = defineProps<{
  roles: Role[]
  realmName: string
}>()

const router = useRouter()

function navigateToCreateRole(): void {
  router.push(`/realms/${props.realmName}/roles/create`)
}
</script>

<template>
  <div class="role-list">
    <div class="list-header">
      <Button
        label="Add Role"
        icon="pi pi-plus"
        size="small"
        @click="navigateToCreateRole"
      />
    </div>

    <div v-if="roles.length === 0" class="empty-state">
      <i class="pi pi-shield empty-icon" />
      <p>No roles configured</p>
    </div>

    <DataTable
      v-else
      :value="roles"
      striped-rows
      scrollable
      scroll-height="400px"
      class="role-table"
    >
      <Column field="name" header="Role Name" sortable>
        <template #body="slotProps">
          <span class="role-name">{{ slotProps.data.name }}</span>
        </template>
      </Column>

      <Column field="description" header="Description">
        <template #body="slotProps">
          {{ slotProps.data.description || '-' }}
        </template>
      </Column>

      <Column field="composite" header="Type" sortable>
        <template #body="slotProps">
          <Tag
            :value="slotProps.data.composite ? 'Composite' : 'Simple'"
            :severity="slotProps.data.composite ? 'info' : 'secondary'"
          />
        </template>
      </Column>

      <Column field="clientRole" header="Scope" sortable>
        <template #body="slotProps">
          <Tag
            :value="slotProps.data.clientRole ? 'Client' : 'Realm'"
            :severity="slotProps.data.clientRole ? 'warn' : 'success'"
          />
        </template>
      </Column>
    </DataTable>
  </div>
</template>

<style scoped>
.role-list {
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

.role-name {
  font-weight: 500;
}
</style>
