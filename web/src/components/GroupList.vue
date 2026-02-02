<script setup lang="ts">
import { computed } from 'vue'
import Tree from 'primevue/tree'
import type { TreeNode } from 'primevue/treenode'
import type { Group } from '@/types'

const props = defineProps<{
  groups: Group[]
}>()

interface TreeGroup extends TreeNode {
  key: string
  label: string
  data: Group
  children?: TreeGroup[]
}

const treeNodes = computed<TreeGroup[]>(() => {
  return buildTree(props.groups)
})

function buildTree(groups: Group[]): TreeGroup[] {
  const groupMap = new Map<string, TreeGroup>()
  const roots: TreeGroup[] = []

  // First pass: create all nodes
  for (const group of groups) {
    groupMap.set(group.id, {
      key: group.id,
      label: group.name,
      data: group,
      icon: 'pi pi-users',
      children: []
    })
  }

  // Second pass: build hierarchy
  for (const group of groups) {
    const node = groupMap.get(group.id)
    if (!node) continue

    if (group.parentId) {
      const parent = groupMap.get(group.parentId)
      if (parent) {
        parent.children = parent.children || []
        parent.children.push(node)
      } else {
        roots.push(node)
      }
    } else {
      roots.push(node)
    }
  }

  // Handle subGroups if they exist
  for (const group of groups) {
    if (group.subGroups && group.subGroups.length > 0) {
      const node = groupMap.get(group.id)
      if (node) {
        node.children = buildTree(group.subGroups)
      }
    }
  }

  return roots
}
</script>

<template>
  <div class="group-list">
    <div v-if="groups.length === 0" class="empty-state">
      <i class="pi pi-users empty-icon" />
      <p>No groups configured</p>
    </div>

    <Tree
      v-else
      :value="treeNodes"
      class="group-tree"
    >
      <template #default="slotProps">
        <div class="tree-node">
          <span class="group-name">{{ slotProps.node.label }}</span>
          <span class="group-path">{{ slotProps.node.data.path }}</span>
        </div>
      </template>
    </Tree>
  </div>
</template>

<style scoped>
.group-list {
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

.group-tree {
  border: none;
  padding: 0;
}

.tree-node {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.group-name {
  font-weight: 500;
  color: var(--p-text-color);
}

.group-path {
  font-size: 0.75rem;
  color: var(--p-text-muted-color);
  font-family: monospace;
}
</style>
