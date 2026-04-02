<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppToast from '@/components/ui/AppToast.vue'
import AppConfirmDialog from '@/components/ui/AppConfirmDialog.vue'
</script>

<template>
  <AppToast />
  <AppConfirmDialog />
  <RouterView />
</template>
