import { ref } from 'vue'

interface ConfirmOptions {
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  destructive?: boolean
}

const isOpen = ref(false)
const options = ref<ConfirmOptions>({ title: '', message: '' })
let resolvePromise: ((value: boolean) => void) | null = null

export function useConfirm() {
  function confirm(opts: ConfirmOptions): Promise<boolean> {
    options.value = opts
    isOpen.value = true
    return new Promise((resolve) => {
      resolvePromise = resolve
    })
  }

  function accept() {
    isOpen.value = false
    resolvePromise?.(true)
    resolvePromise = null
  }

  function cancel() {
    isOpen.value = false
    resolvePromise?.(false)
    resolvePromise = null
  }

  return { isOpen, options, confirm, accept, cancel }
}
