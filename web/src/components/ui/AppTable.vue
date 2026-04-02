<script setup lang="ts">
</script>

<template>
  <div class="overflow-hidden rounded-lg ring-1 ring-zinc-200 dark:ring-zinc-800">
    <table class="min-w-full divide-y divide-zinc-200 dark:divide-zinc-800">
      <thead class="bg-zinc-50 dark:bg-zinc-800/50">
        <tr>
          <slot name="header" />
        </tr>
      </thead>
      <tbody class="divide-y divide-zinc-200 bg-white dark:divide-zinc-800 dark:bg-zinc-900">
        <slot />
      </tbody>
    </table>
  </div>
</template>
