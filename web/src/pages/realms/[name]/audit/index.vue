<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { auditApi, type AuditLog } from '@/api/audit'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Paginator from 'primevue/paginator'
import ProgressSpinner from 'primevue/progressspinner'
import Message from 'primevue/message'
import AuditTimeline from '@/components/AuditTimeline.vue'
import AuditDiffViewer from '@/components/AuditDiffViewer.vue'

defineOptions({
  name: 'RealmAuditPage'
})

const route = useRoute()
const toast = useToast()

const realmName = computed(() => route.params.name as string)

const logs = ref<AuditLog[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

const page = ref(0)
const size = ref(20)
const totalElements = ref(0)
const first = ref(0)

const selectedLog = ref<AuditLog | null>(null)
const detailsDialogVisible = ref(false)

const revertDialogVisible = ref(false)
const revertLog = ref<AuditLog | null>(null)
const revertReason = ref('')
const reverting = ref(false)

onMounted(async () => {
  await loadAuditLogs()
})

watch(() => route.params.name, async () => {
  if (route.params.name) {
    page.value = 0
    first.value = 0
    await loadAuditLogs()
  }
})

async function loadAuditLogs(): Promise<void> {
  loading.value = true
  error.value = null

  try {
    const response = await auditApi.getRealmAudit(realmName.value, page.value, size.value)
    logs.value = response.content
    totalElements.value = response.totalElements
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load audit logs'
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
  loadAuditLogs()
}

function viewDetails(log: AuditLog): void {
  selectedLog.value = log
  detailsDialogVisible.value = true
}

function startRevert(log: AuditLog): void {
  revertLog.value = log
  revertReason.value = ''
  revertDialogVisible.value = true
}

async function confirmRevert(): Promise<void> {
  if (!revertLog.value || !revertReason.value.trim()) {
    toast.add({
      severity: 'warn',
      summary: 'Required',
      detail: 'Please provide a reason for reverting',
      life: 3000
    })
    return
  }

  reverting.value = true

  try {
    const response = await auditApi.revertAction(
      realmName.value,
      revertLog.value.id,
      revertReason.value.trim()
    )

    if (response.success) {
      toast.add({
        severity: 'success',
        summary: 'Reverted',
        detail: response.message,
        life: 5000
      })
      revertDialogVisible.value = false
      await loadAuditLogs()
    } else {
      toast.add({
        severity: 'error',
        summary: 'Failed',
        detail: response.message,
        life: 5000
      })
    }
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: err instanceof Error ? err.message : 'Failed to revert action',
      life: 5000
    })
  } finally {
    reverting.value = false
  }
}
</script>

<template>
  <div class="audit-page">
    <div class="page-header">
      <div class="header-content">
        <h1>Audit Trail</h1>
        <p>{{ realmName }}</p>
      </div>
      <Button
        icon="pi pi-refresh"
        text
        rounded
        @click="loadAuditLogs"
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
        @view-details="viewDetails"
        @revert="startRevert"
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
            <label>Actor</label>
            <span>{{ selectedLog.actorDisplayName || selectedLog.actorEmail || 'Unknown' }}</span>
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
          <div class="detail-item">
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

    <!-- Revert Dialog -->
    <Dialog
      v-model:visible="revertDialogVisible"
      header="Revert Action"
      :style="{ width: '500px' }"
      :modal="true"
    >
      <template v-if="revertLog">
        <p class="revert-warning">
          <i class="pi pi-exclamation-triangle" />
          This will revert the {{ revertLog.actionType.toLowerCase() }} of
          <strong>{{ revertLog.entityName }}</strong> ({{ revertLog.entityType }}).
        </p>

        <div class="revert-form">
          <label for="revertReason">Reason for reverting *</label>
          <InputText
            id="revertReason"
            v-model="revertReason"
            placeholder="Enter reason..."
            class="w-full"
          />
        </div>

        <div class="revert-actions">
          <Button
            label="Cancel"
            severity="secondary"
            @click="revertDialogVisible = false"
            :disabled="reverting"
          />
          <Button
            label="Revert"
            icon="pi pi-undo"
            severity="danger"
            @click="confirmRevert"
            :loading="reverting"
          />
        </div>
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.audit-page {
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
  font-family: monospace;
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

.revert-warning {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1rem;
  background-color: var(--p-yellow-50);
  border-radius: 8px;
  margin-bottom: 1rem;
}

.revert-warning i {
  color: var(--p-yellow-600);
  font-size: 1.25rem;
}

.revert-form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.revert-form label {
  font-weight: 500;
}

.w-full {
  width: 100%;
}

.revert-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}
</style>
