<script setup lang="ts">
import { useAttrs } from 'vue'

const attrs = useAttrs()
</script>

<template>
  <tbody v-bind="attrs">
    <slot />
  </tbody>
</template>
