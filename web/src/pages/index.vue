<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onMounted(() => {
  router.replace('/realms')
})
</script>

<template>
  <div class="flex justify-center items-center h-screen">
    <i class="pi pi-spin pi-spinner text-4xl"></i>
  </div>
</template>
