import { ref } from 'vue'

export interface Toast {
  id: number
  type: 'success' | 'error' | 'warning' | 'info'
  title: string
  message?: string
}

const toasts = ref<Toast[]>([])
let nextId = 0

export function useToast() {
  function add(type: Toast['type'], title: string, message?: string) {
    const id = nextId++
    toasts.value.push({ id, type, title, message })
    setTimeout(() => remove(id), 5000)
  }

  function remove(id: number) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  return {
    toasts,
    success: (title: string, message?: string) => add('success', title, message),
    error: (title: string, message?: string) => add('error', title, message),
    warning: (title: string, message?: string) => add('warning', title, message),
    info: (title: string, message?: string) => add('info', title, message),
    remove,
  }
}
