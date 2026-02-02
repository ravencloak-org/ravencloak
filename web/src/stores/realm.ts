import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { realmApi } from '@/services/api'
import type { Realm, RealmDetails, CreateRealmRequest } from '@/types'

export const useRealmStore = defineStore('realm', () => {
  const realms = ref<Realm[]>([])
  const currentRealm = ref<RealmDetails | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const hasRealms = computed(() => realms.value.length > 0)

  async function fetchRealms(): Promise<void> {
    loading.value = true
    error.value = null

    try {
      realms.value = await realmApi.list()
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to fetch realms'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function fetchRealm(name: string): Promise<void> {
    loading.value = true
    error.value = null

    try {
      currentRealm.value = await realmApi.get(name)
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to fetch realm'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function createRealm(request: CreateRealmRequest): Promise<Realm> {
    loading.value = true
    error.value = null

    try {
      const realm = await realmApi.create(request)
      realms.value.push(realm)
      return realm
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to create realm'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function updateRealm(name: string, request: Partial<CreateRealmRequest>): Promise<void> {
    loading.value = true
    error.value = null

    try {
      const updated = await realmApi.update(name, request)
      const index = realms.value.findIndex(r => r.realmName === name)
      if (index >= 0) {
        realms.value[index] = updated
      }
      if (currentRealm.value?.realmName === name) {
        currentRealm.value = { ...currentRealm.value, ...updated }
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to update realm'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function deleteRealm(name: string): Promise<void> {
    loading.value = true
    error.value = null

    try {
      await realmApi.delete(name)
      realms.value = realms.value.filter(r => r.realmName !== name)
      if (currentRealm.value?.realmName === name) {
        currentRealm.value = null
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to delete realm'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function enableSpi(name: string): Promise<void> {
    loading.value = true
    error.value = null

    try {
      await realmApi.enableSpi(name)
      await fetchRealm(name)
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to enable SPI'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function syncRealm(name: string): Promise<void> {
    loading.value = true
    error.value = null

    try {
      await realmApi.sync(name)
      await fetchRealm(name)
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to sync realm'
      throw err
    } finally {
      loading.value = false
    }
  }

  function clearCurrentRealm(): void {
    currentRealm.value = null
  }

  return {
    realms,
    currentRealm,
    loading,
    error,
    hasRealms,
    fetchRealms,
    fetchRealm,
    createRealm,
    updateRealm,
    deleteRealm,
    enableSpi,
    syncRealm,
    clearCurrentRealm
  }
})
