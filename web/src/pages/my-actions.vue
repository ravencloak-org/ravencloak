<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import { auditApi, type AuditLog } from '@/api/audit'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Paginator from 'primevue/paginator'
import ProgressSpinner from 'primevue/progressspinner'
import Message from 'primevue/message'
import AuditTimeline from '@/components/AuditTimeline.vue'
import AuditDiffViewer from '@/components/AuditDiffViewer.vue'

defineOptions({
  name: 'MyActionsPage'
})

const toast = useToast()

const logs = ref<AuditLog[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

const page = ref(0)
const size = ref(20)
const totalElements = ref(0)
const first = ref(0)

const selectedLog = ref<AuditLog | null>(null)
const detailsDialogVisible = ref(false)

onMounted(async () => {
  await loadMyActions()
})

async function loadMyActions(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    const response = await auditApi.getMyActions(page.value, size.value)
    logs.value = response.content
    totalElements.value = response.totalElements
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load action history'
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

function onPageChange(event: { page: number; first: number; rows: number }): void {
  page.value = event.page
  first.value = event.first
  size.value = event.rows
  loadMyActions()
}

function viewDetails(log: AuditLog): void {
  selectedLog.value = log
  detailsDialogVisible.value = true
}
</script>

<template>
  <div class="my-actions-page">
    <div class="page-header">
      <div class="header-content">
        <h1>My Actions</h1>
        <p>Your recent activity across all realms</p>
      </div>
      <Button
        icon="pi pi-refresh"
        text
        rounded
        @click="loadMyActions"
        :disabled="loading"
      />
    </div>

    <div v-if="loading && logs.length === 0" class="loading-container">
      <ProgressSpinner />
    </div>

    <Message v-else-if="error" severity="error" :closable="false">
      {{ error }}
    </Message>

    <template v-else>
      <AuditTimeline
        :logs="logs"
        :show-realm-name="true"
        @view-details="viewDetails"
      />

      <Paginator
        v-if="totalElements > size"
        :first="first"
        :rows="size"
        :total-records="totalElements"
        :rows-per-page-options="[10, 20, 50]"
        @page="onPageChange"
      />
    </template>

    <!-- Details Dialog -->
    <Dialog
      v-model:visible="detailsDialogVisible"
      header="Action Details"
      :style="{ width: '700px' }"
      :modal="true"
    >
      <template v-if="selectedLog">
        <div class="details-grid">
          <div class="detail-item">
            <label>Realm</label>
            <span>{{ selectedLog.realmName }}</span>
          </div>
          <div class="detail-item">
            <label>Action</label>
            <span>{{ selectedLog.actionType }}</span>
          </div>
          <div class="detail-item">
            <label>Entity Type</label>
            <span>{{ selectedLog.entityType }}</span>
          </div>
          <div class="detail-item">
            <label>Entity Name</label>
            <span>{{ selectedLog.entityName }}</span>
          </div>
          <div class="detail-item full-width">
            <label>Timestamp</label>
            <span>{{ new Date(selectedLog.createdAt).toLocaleString() }}</span>
          </div>
          <div v-if="selectedLog.reverted" class="detail-item full-width">
            <label>Reverted</label>
            <span>
              {{ new Date(selectedLog.revertedAt!).toLocaleString() }}
              - {{ selectedLog.revertReason }}
            </span>
          </div>
        </div>

        <h4>Changes</h4>
        <AuditDiffViewer :log="selectedLog" />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.my-actions-page {
  max-width: 1000px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
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
}

.loading-container {
  display: flex;
  justify-content: center;
  padding: 4rem;
}

.details-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.detail-item.full-width {
  grid-column: 1 / -1;
}

.detail-item label {
  font-size: 0.75rem;
  color: var(--p-text-muted-color);
  text-transform: uppercase;
}

.detail-item span {
  font-weight: 500;
}

h4 {
  margin: 1rem 0 0.5rem;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--p-text-muted-color);
  text-transform: uppercase;
}
</style>
