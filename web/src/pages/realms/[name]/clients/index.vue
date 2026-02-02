<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRealmStore } from '@/stores/realm'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'
import ClientList from '@/components/ClientList.vue'

defineOptions({
  name: 'RealmClientsPage'
})

const route = useRoute()
const router = useRouter()
const realmStore = useRealmStore()
const toast = useToast()

const realmName = computed(() => route.params.name as string)
const loading = ref(true)

onMounted(async () => {
  if (!realmStore.currentRealm || realmStore.currentRealm.realmName !== realmName.value) {
    try {
      await realmStore.fetchRealm(realmName.value)
    } catch (err) {
      toast.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to load realm',
        life: 5000
      })
    }
  }
  loading.value = false
})

function navigateBack(): void {
  router.push(`/realms/${realmName.value}`)
}
</script>

<template>
  <div class="clients-page">
    <div class="page-header">
      <Button
        icon="pi pi-arrow-left"
        text
        rounded
        @click="navigateBack"
      />
      <div class="header-content">
        <h1>Clients</h1>
        <p>{{ realmName }}</p>
      </div>
    </div>

    <div v-if="loading" class="loading-container">
      <ProgressSpinner />
    </div>

    <ClientList
      v-else-if="realmStore.currentRealm"
      :clients="realmStore.currentRealm.clients || []"
      :realm-name="realmName"
    />
  </div>
</template>

<style scoped>
.clients-page {
  max-width: 1200px;
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

.loading-container {
  display: flex;
  justify-content: center;
  padding: 3rem;
}
</style>
