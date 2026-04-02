# Tailwind Plus Migration Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace PrimeVue with Tailwind CSS v4 + Tailwind Plus Application UI blocks + Headless UI for Vue in the Ravencloak admin portal, clean up legacy code, and add Playwright e2e tests.

**Architecture:** Build a reusable `components/ui/` layer wrapping Tailwind Plus patterns + `@headlessui/vue`. All pages import from this layer. Custom composables (`useToast`, `useConfirm`, `useDarkMode`) replace PrimeVue services. Sidebar layout shell replaces the current top-bar layout.

**Tech Stack:** Vue 3, TypeScript, Vite 6, Tailwind CSS v4, @headlessui/vue, @heroicons/vue, clsx, tailwind-merge, Playwright

**Spec:** `docs/superpowers/specs/2026-03-17-tailwind-plus-migration-design.md`

---

## File Structure

### New Files
```
web/src/
├── assets/styles/app.css              # Tailwind entry + @theme
├── lib/utils.ts                       # cn() helper
├── composables/
│   ├── useToast.ts
│   ├── useConfirm.ts
│   └── useDarkMode.ts
├── components/
│   ├── ui/
│   │   ├── AppButton.vue
│   │   ├── AppInput.vue
│   │   ├── AppSelect.vue
│   │   ├── AppListbox.vue
│   │   ├── AppCombobox.vue
│   │   ├── AppCheckbox.vue
│   │   ├── AppTextarea.vue
│   │   ├── AppSwitch.vue
│   │   ├── AppDialog.vue
│   │   ├── AppConfirmDialog.vue
│   │   ├── AppDropdown.vue
│   │   ├── AppBadge.vue
│   │   ├── AppAvatar.vue
│   │   ├── AppTable.vue
│   │   ├── AppPagination.vue
│   │   ├── AppToast.vue
│   │   ├── AppSpinner.vue
│   │   ├── AppAlert.vue
│   │   ├── AppEmptyState.vue
│   │   ├── AppCard.vue
│   │   ├── AppDivider.vue
│   │   └── AppTabs.vue
│   └── layout/
│       ├── SidebarLayout.vue
│       ├── TheSidebar.vue
│       └── TheNavbar.vue
└── e2e/
    ├── playwright.config.ts
    ├── fixtures/test-setup.ts
    ├── pages/*.ts
    └── tests/*.spec.ts
```

### Modified Files
```
web/package.json                       # Swap dependencies
web/vite.config.ts                     # Add @tailwindcss/vite plugin
web/index.html                         # Add Inter font CDN, dark class
web/tsconfig.json                      # (if needed for path aliases)
web/src/main.ts                        # Remove PrimeVue, import app.css
web/src/App.vue                        # Remove PrimeVue, add Toast/Confirm containers
web/src/router/index.ts                # Update layout import path
web/src/components/*.vue               # All 9 domain components rewritten
web/src/pages/**/*.vue                 # All 20 pages rewritten
```

### Deleted Files
```
web/src/views/                         # 4 legacy view files
web/src/services/api.ts                # Duplicate API module
```

---

## Task 1: Create Git Worktree and Scaffold

**Files:**
- Worktree creation at `../auth-tailwind-plus/` on branch `tailwind-plus`

- [ ] **Step 1: Create worktree branch**

```bash
cd /Users/jobinlawrance/Project/dsj/auth
git worktree add ../auth-tailwind-plus -b tailwind-plus
```

- [ ] **Step 2: Verify worktree**

```bash
cd /Users/jobinlawrance/Project/dsj/auth-tailwind-plus
git branch --show-current
```

Expected: `tailwind-plus`

- [ ] **Step 3: Commit**

No commit needed — worktree is set up.

---

## Task 2: Swap Dependencies

**Files:**
- Modify: `web/package.json`

- [ ] **Step 1: Remove PrimeVue dependencies**

In `web/package.json`, remove from `dependencies`:
- `"primeicons": "^7.0.0"`
- `"primevue": "^4.2.5"`

Remove from `devDependencies`:
- `"@primevue/themes": "^4.2.5"`

- [ ] **Step 2: Add Tailwind Plus dependencies**

Add to `dependencies`:
```json
"@headlessui/vue": "^1.7.23",
"@heroicons/vue": "^2.2.0",
"clsx": "^2.1.1",
"tailwind-merge": "^3.0.0"
```

Add to `devDependencies`:
```json
"tailwindcss": "^4.1.0",
"@tailwindcss/vite": "^4.1.0"
```

- [ ] **Step 3: Install dependencies**

```bash
cd web && npm install
```

Expected: No errors. `node_modules` updated.

- [ ] **Step 4: Verify install**

```bash
cd web && npx tailwindcss --help | head -3
```

Expected: Tailwind CSS v4 help output.

- [ ] **Step 5: Commit**

```bash
git add web/package.json web/package-lock.json
git commit -m "build: swap PrimeVue for Tailwind CSS v4, Headless UI, Heroicons"
```

---

## Task 3: Configure Tailwind CSS v4 + Vite

**Files:**
- Create: `web/src/assets/styles/app.css`
- Modify: `web/vite.config.ts`
- Modify: `web/index.html`

- [ ] **Step 1: Create app.css with Tailwind config**

Create `web/src/assets/styles/app.css`:

```css
@import "tailwindcss";

@theme {
  --font-sans: "Inter", sans-serif;
  --font-sans--font-feature-settings: "cv11";

  /* Primary palette — indigo-based to match previous Aura theme */
  --color-primary-50: oklch(0.97 0.014 254);
  --color-primary-100: oklch(0.94 0.028 254);
  --color-primary-200: oklch(0.87 0.058 254);
  --color-primary-300: oklch(0.78 0.094 254);
  --color-primary-400: oklch(0.68 0.132 254);
  --color-primary-500: oklch(0.59 0.160 254);
  --color-primary-600: oklch(0.52 0.155 254);
  --color-primary-700: oklch(0.46 0.135 254);
  --color-primary-800: oklch(0.39 0.110 254);
  --color-primary-900: oklch(0.34 0.085 254);
  --color-primary-950: oklch(0.26 0.065 254);
}

/* Dark mode: class-based selector on <html> */
@variant dark (&:where(.dark, .dark *));
```

- [ ] **Step 2: Add @tailwindcss/vite to vite.config.ts**

Update `web/vite.config.ts` to:

```typescript
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import VueRouter from 'unplugin-vue-router/vite'

export default defineConfig({
  plugins: [
    VueRouter({
      routesFolder: 'src/pages',
      dts: 'src/typed-router.d.ts',
      extensions: ['.vue']
    }),
    vue(),
    tailwindcss()
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

- [ ] **Step 3: Update index.html**

Update `web/index.html` to:

```html
<!DOCTYPE html>
<html lang="en" class="">
  <head>
    <meta charset="UTF-8">
    <link rel="icon" type="image/svg+xml" href="/vite.svg">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="https://rsms.me/inter/inter.css">
    <title>Ravencloak Admin</title>
  </head>
  <body class="antialiased">
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

- [ ] **Step 4: Commit**

```bash
git add web/src/assets/styles/app.css web/vite.config.ts web/index.html
git commit -m "build: configure Tailwind CSS v4 with Vite plugin, Inter font, dark mode"
```

---

## Task 4: Create Utility Layer

**Files:**
- Create: `web/src/lib/utils.ts`

- [ ] **Step 1: Create cn() utility**

Create `web/src/lib/utils.ts`:

```typescript
import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/lib/utils.ts
git commit -m "feat: add cn() class merge utility"
```

---

## Task 5: Create Composables

**Files:**
- Create: `web/src/composables/useToast.ts`
- Create: `web/src/composables/useConfirm.ts`
- Create: `web/src/composables/useDarkMode.ts`

- [ ] **Step 1: Create useToast composable**

Create `web/src/composables/useToast.ts`:

```typescript
import { ref, readonly } from 'vue'

export interface Toast {
  id: number
  message: string
  type: 'success' | 'error' | 'warning' | 'info'
  duration: number
}

const toasts = ref<Toast[]>([])
let nextId = 0

function addToast(message: string, type: Toast['type'], duration = 5000) {
  const id = nextId++
  toasts.value.push({ id, message, type, duration })
  setTimeout(() => removeToast(id), duration)
}

function removeToast(id: number) {
  toasts.value = toasts.value.filter(t => t.id !== id)
}

export function useToast() {
  return {
    toasts: readonly(toasts),
    removeToast,
    success: (message: string) => addToast(message, 'success'),
    error: (message: string) => addToast(message, 'error'),
    warning: (message: string) => addToast(message, 'warning'),
    info: (message: string) => addToast(message, 'info'),
  }
}
```

- [ ] **Step 2: Create useConfirm composable**

Create `web/src/composables/useConfirm.ts`:

```typescript
import { ref, readonly } from 'vue'

export interface ConfirmOptions {
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  variant?: 'danger' | 'primary'
}

interface ConfirmState {
  open: boolean
  options: ConfirmOptions
  resolve: ((value: boolean) => void) | null
}

const state = ref<ConfirmState>({
  open: false,
  options: { title: '', message: '' },
  resolve: null,
})

export function useConfirm() {
  function confirm(options: ConfirmOptions): Promise<boolean> {
    return new Promise((resolve) => {
      state.value = {
        open: true,
        options,
        resolve,
      }
    })
  }

  function handleConfirm() {
    state.value.resolve?.(true)
    state.value.open = false
  }

  function handleCancel() {
    state.value.resolve?.(false)
    state.value.open = false
  }

  return {
    state: readonly(state),
    confirm,
    handleConfirm,
    handleCancel,
  }
}
```

- [ ] **Step 3: Create useDarkMode composable**

Create `web/src/composables/useDarkMode.ts`:

```typescript
import { ref, watch } from 'vue'

const STORAGE_KEY = 'ravencloak-dark-mode'

function getInitialValue(): boolean {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored !== null) return stored === 'true'
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

const isDark = ref(getInitialValue())

function applyDarkMode(dark: boolean) {
  document.documentElement.classList.toggle('dark', dark)
}

// Apply on init
applyDarkMode(isDark.value)

watch(isDark, (val) => {
  localStorage.setItem(STORAGE_KEY, String(val))
  applyDarkMode(val)
})

export function useDarkMode() {
  function toggle() {
    isDark.value = !isDark.value
  }

  return {
    isDark,
    toggle,
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add web/src/composables/
git commit -m "feat: add useToast, useConfirm, useDarkMode composables"
```

---

## Task 6: Create Base UI Components — Primitives

**Files:**
- Create: `web/src/components/ui/AppButton.vue`
- Create: `web/src/components/ui/AppInput.vue`
- Create: `web/src/components/ui/AppSelect.vue`
- Create: `web/src/components/ui/AppCheckbox.vue`
- Create: `web/src/components/ui/AppTextarea.vue`
- Create: `web/src/components/ui/AppBadge.vue`
- Create: `web/src/components/ui/AppAvatar.vue`
- Create: `web/src/components/ui/AppSpinner.vue`
- Create: `web/src/components/ui/AppDivider.vue`
- Create: `web/src/components/ui/AppCard.vue`
- Create: `web/src/components/ui/AppAlert.vue`
- Create: `web/src/components/ui/AppEmptyState.vue`

These are plain components with no Headless UI dependency — just Tailwind Plus markup patterns.

- [ ] **Step 1: Create AppButton.vue**

Create `web/src/components/ui/AppButton.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

interface Props {
  variant?: 'primary' | 'secondary' | 'outline' | 'danger' | 'ghost'
  size?: 'xs' | 'sm' | 'md' | 'lg'
  disabled?: boolean
  loading?: boolean
  type?: 'button' | 'submit' | 'reset'
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'primary',
  size: 'md',
  disabled: false,
  loading: false,
  type: 'button',
})

const classes = computed(() =>
  cn(
    'inline-flex items-center justify-center gap-2 rounded-md font-semibold shadow-xs transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600 disabled:opacity-50 disabled:cursor-not-allowed',
    {
      'bg-primary-600 text-white hover:bg-primary-500': props.variant === 'primary',
      'bg-white text-gray-900 ring-1 ring-inset ring-gray-300 hover:bg-gray-50 dark:bg-white/10 dark:text-white dark:ring-white/10 dark:hover:bg-white/20': props.variant === 'secondary',
      'bg-transparent text-primary-600 ring-1 ring-inset ring-primary-600 hover:bg-primary-50 dark:text-primary-400 dark:ring-primary-400 dark:hover:bg-primary-950': props.variant === 'outline',
      'bg-red-600 text-white hover:bg-red-500': props.variant === 'danger',
      'bg-transparent text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/10 shadow-none': props.variant === 'ghost',
    },
    {
      'px-2 py-1 text-xs': props.size === 'xs',
      'px-2.5 py-1.5 text-sm': props.size === 'sm',
      'px-3 py-2 text-sm': props.size === 'md',
      'px-3.5 py-2.5 text-sm': props.size === 'lg',
    },
    props.class,
  )
)
</script>

<template>
  <button
    :type="type"
    :class="classes"
    :disabled="disabled || loading"
  >
    <AppSpinner v-if="loading" class="h-4 w-4" />
    <slot />
  </button>
</template>
```

- [ ] **Step 2: Create AppInput.vue**

Create `web/src/components/ui/AppInput.vue`:

```vue
<script setup lang="ts">
import { cn } from '@/lib/utils'

interface Props {
  modelValue?: string
  label?: string
  placeholder?: string
  type?: string
  error?: string
  helpText?: string
  disabled?: boolean
  required?: boolean
  id?: string
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  type: 'text',
  disabled: false,
  required: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const inputId = props.id ?? `input-${Math.random().toString(36).slice(2, 9)}`
</script>

<template>
  <div :class="cn('w-full', props.class)">
    <label
      v-if="label"
      :for="inputId"
      class="block text-sm font-medium text-gray-700 dark:text-gray-300"
    >
      {{ label }}
      <span v-if="required" class="text-red-500">*</span>
    </label>
    <div :class="label ? 'mt-2' : ''">
      <input
        :id="inputId"
        :type="type"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :required="required"
        :class="cn(
          'block w-full rounded-md bg-white px-3 py-2 text-base text-gray-900 outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline-2 focus:-outline-offset-2 focus:outline-primary-600 disabled:bg-gray-50 disabled:text-gray-500 sm:text-sm/6 dark:bg-white/5 dark:text-white dark:outline-white/10 dark:placeholder:text-gray-500',
          error && 'outline-red-500 focus:outline-red-500'
        )"
        @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
      />
    </div>
    <p v-if="error" class="mt-2 text-sm text-red-600 dark:text-red-400">{{ error }}</p>
    <p v-else-if="helpText" class="mt-2 text-sm text-gray-500 dark:text-gray-400">{{ helpText }}</p>
  </div>
</template>
```

- [ ] **Step 3: Create AppSelect.vue**

Create `web/src/components/ui/AppSelect.vue`:

```vue
<script setup lang="ts">
import { cn } from '@/lib/utils'

interface Props {
  modelValue?: string
  label?: string
  error?: string
  disabled?: boolean
  required?: boolean
  id?: string
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  required: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const selectId = props.id ?? `select-${Math.random().toString(36).slice(2, 9)}`
</script>

<template>
  <div :class="cn('w-full', props.class)">
    <label
      v-if="label"
      :for="selectId"
      class="block text-sm font-medium text-gray-700 dark:text-gray-300"
    >
      {{ label }}
      <span v-if="required" class="text-red-500">*</span>
    </label>
    <div :class="label ? 'mt-2' : ''">
      <select
        :id="selectId"
        :value="modelValue"
        :disabled="disabled"
        :required="required"
        :class="cn(
          'block w-full rounded-md bg-white py-2 pl-3 pr-10 text-base text-gray-900 outline-1 -outline-offset-1 outline-gray-300 focus:outline-2 focus:-outline-offset-2 focus:outline-primary-600 disabled:bg-gray-50 disabled:text-gray-500 sm:text-sm/6 dark:bg-white/5 dark:text-white dark:outline-white/10',
          error && 'outline-red-500 focus:outline-red-500'
        )"
        @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
      >
        <slot />
      </select>
    </div>
    <p v-if="error" class="mt-2 text-sm text-red-600 dark:text-red-400">{{ error }}</p>
  </div>
</template>
```

- [ ] **Step 4: Create AppCheckbox.vue**

Create `web/src/components/ui/AppCheckbox.vue`:

```vue
<script setup lang="ts">
interface Props {
  modelValue?: boolean
  label?: string
  description?: string
  disabled?: boolean
  id?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const checkboxId = props.id ?? `checkbox-${Math.random().toString(36).slice(2, 9)}`
</script>

<template>
  <div class="flex gap-3">
    <div class="flex h-6 shrink-0 items-center">
      <div class="group grid size-4 grid-cols-[1fr] items-center justify-items-center">
        <input
          :id="checkboxId"
          type="checkbox"
          :checked="modelValue"
          :disabled="disabled"
          class="col-start-1 row-start-1 appearance-none rounded border border-gray-300 bg-white checked:border-primary-600 checked:bg-primary-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600 disabled:border-gray-300 disabled:bg-gray-100 dark:border-white/10 dark:bg-white/5 dark:checked:border-primary-500 dark:checked:bg-primary-500"
          @change="emit('update:modelValue', ($event.target as HTMLInputElement).checked)"
        />
        <svg
          class="pointer-events-none col-start-1 row-start-1 size-3.5 self-center justify-self-center stroke-white opacity-0 [input:checked+&]:opacity-100"
          viewBox="0 0 14 14"
          fill="none"
        >
          <path d="M3 8L6 11L11 3.5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>
    </div>
    <div v-if="label" class="text-sm/6">
      <label :for="checkboxId" class="font-medium text-gray-900 dark:text-white">{{ label }}</label>
      <p v-if="description" class="text-gray-500 dark:text-gray-400">{{ description }}</p>
    </div>
  </div>
</template>
```

- [ ] **Step 5: Create AppTextarea.vue**

Create `web/src/components/ui/AppTextarea.vue`:

```vue
<script setup lang="ts">
import { cn } from '@/lib/utils'

interface Props {
  modelValue?: string
  label?: string
  placeholder?: string
  error?: string
  rows?: number
  disabled?: boolean
  required?: boolean
  id?: string
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  rows: 4,
  disabled: false,
  required: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const textareaId = props.id ?? `textarea-${Math.random().toString(36).slice(2, 9)}`
</script>

<template>
  <div :class="cn('w-full', props.class)">
    <label
      v-if="label"
      :for="textareaId"
      class="block text-sm font-medium text-gray-700 dark:text-gray-300"
    >
      {{ label }}
      <span v-if="required" class="text-red-500">*</span>
    </label>
    <div :class="label ? 'mt-2' : ''">
      <textarea
        :id="textareaId"
        :value="modelValue"
        :placeholder="placeholder"
        :rows="rows"
        :disabled="disabled"
        :required="required"
        :class="cn(
          'block w-full rounded-md bg-white px-3 py-2 text-base text-gray-900 outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline-2 focus:-outline-offset-2 focus:outline-primary-600 disabled:bg-gray-50 disabled:text-gray-500 sm:text-sm/6 dark:bg-white/5 dark:text-white dark:outline-white/10',
          error && 'outline-red-500 focus:outline-red-500'
        )"
        @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
      />
    </div>
    <p v-if="error" class="mt-2 text-sm text-red-600 dark:text-red-400">{{ error }}</p>
  </div>
</template>
```

- [ ] **Step 6: Create AppBadge.vue**

Create `web/src/components/ui/AppBadge.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

interface Props {
  variant?: 'gray' | 'red' | 'yellow' | 'green' | 'blue' | 'indigo' | 'purple' | 'pink'
  size?: 'sm' | 'md'
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'gray',
  size: 'sm',
})

const classes = computed(() =>
  cn(
    'inline-flex items-center rounded-md font-medium ring-1 ring-inset',
    {
      'bg-gray-50 text-gray-600 ring-gray-500/10 dark:bg-gray-400/10 dark:text-gray-400 dark:ring-gray-400/20': props.variant === 'gray',
      'bg-red-50 text-red-700 ring-red-600/10 dark:bg-red-400/10 dark:text-red-400 dark:ring-red-400/20': props.variant === 'red',
      'bg-yellow-50 text-yellow-800 ring-yellow-600/20 dark:bg-yellow-400/10 dark:text-yellow-500 dark:ring-yellow-400/20': props.variant === 'yellow',
      'bg-green-50 text-green-700 ring-green-600/20 dark:bg-green-500/10 dark:text-green-400 dark:ring-green-500/20': props.variant === 'green',
      'bg-blue-50 text-blue-700 ring-blue-700/10 dark:bg-blue-400/10 dark:text-blue-400 dark:ring-blue-400/30': props.variant === 'blue',
      'bg-indigo-50 text-indigo-700 ring-indigo-700/10 dark:bg-indigo-400/10 dark:text-indigo-400 dark:ring-indigo-400/30': props.variant === 'indigo',
      'bg-purple-50 text-purple-700 ring-purple-700/10 dark:bg-purple-400/10 dark:text-purple-400 dark:ring-purple-400/30': props.variant === 'purple',
      'bg-pink-50 text-pink-700 ring-pink-700/10 dark:bg-pink-400/10 dark:text-pink-400 dark:ring-pink-400/20': props.variant === 'pink',
    },
    {
      'px-1.5 py-0.5 text-xs': props.size === 'sm',
      'px-2 py-1 text-xs': props.size === 'md',
    },
    props.class,
  )
)
</script>

<template>
  <span :class="classes">
    <slot />
  </span>
</template>
```

- [ ] **Step 7: Create AppAvatar.vue**

Create `web/src/components/ui/AppAvatar.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

interface Props {
  src?: string | null
  alt?: string
  initials?: string
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl'
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  size: 'md',
})

const sizeClasses = computed(() => ({
  'size-6 text-xs': props.size === 'xs',
  'size-8 text-sm': props.size === 'sm',
  'size-10 text-sm': props.size === 'md',
  'size-12 text-base': props.size === 'lg',
  'size-14 text-lg': props.size === 'xl',
}))
</script>

<template>
  <img
    v-if="src"
    :src="src"
    :alt="alt ?? ''"
    :class="cn('inline-block rounded-full object-cover', sizeClasses, props.class)"
  />
  <span
    v-else-if="initials"
    :class="cn('inline-flex items-center justify-center rounded-full bg-primary-600 font-medium text-white', sizeClasses, props.class)"
  >
    {{ initials }}
  </span>
  <span
    v-else
    :class="cn('inline-block overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700', sizeClasses, props.class)"
  >
    <svg class="h-full w-full text-gray-300 dark:text-gray-500" fill="currentColor" viewBox="0 0 24 24">
      <path d="M24 20.993V24H0v-2.996A14.977 14.977 0 0112.004 15c4.904 0 9.26 2.354 11.996 5.993zM16.002 8.999a4 4 0 11-8 0 4 4 0 018 0z" />
    </svg>
  </span>
</template>
```

- [ ] **Step 8: Create AppSpinner.vue**

Create `web/src/components/ui/AppSpinner.vue`:

```vue
<script setup lang="ts">
import { cn } from '@/lib/utils'

interface Props {
  class?: string
}

const props = defineProps<Props>()
</script>

<template>
  <svg
    :class="cn('animate-spin text-current', props.class)"
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
  >
    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
  </svg>
</template>
```

- [ ] **Step 9: Create AppDivider.vue**

Create `web/src/components/ui/AppDivider.vue`:

```vue
<script setup lang="ts">
import { cn } from '@/lib/utils'

interface Props {
  label?: string
  class?: string
}

const props = defineProps<Props>()
</script>

<template>
  <div :class="cn('relative', props.class)">
    <div class="absolute inset-0 flex items-center" aria-hidden="true">
      <div class="w-full border-t border-gray-300 dark:border-gray-700" />
    </div>
    <div v-if="label" class="relative flex justify-center">
      <span class="bg-white px-2 text-sm text-gray-500 dark:bg-gray-900 dark:text-gray-400">{{ label }}</span>
    </div>
  </div>
</template>
```

- [ ] **Step 10: Create AppCard.vue**

Create `web/src/components/ui/AppCard.vue`:

```vue
<script setup lang="ts">
import { cn } from '@/lib/utils'

interface Props {
  class?: string
  padding?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  padding: true,
})
</script>

<template>
  <div :class="cn('overflow-hidden rounded-lg bg-white shadow-sm ring-1 ring-gray-900/5 dark:bg-gray-800 dark:ring-white/10', padding && 'px-4 py-5 sm:p-6', props.class)">
    <slot />
  </div>
</template>
```

- [ ] **Step 11: Create AppAlert.vue**

Create `web/src/components/ui/AppAlert.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'
import {
  CheckCircleIcon,
  ExclamationTriangleIcon,
  InformationCircleIcon,
  XCircleIcon,
} from '@heroicons/vue/20/solid'

interface Props {
  variant?: 'success' | 'error' | 'warning' | 'info'
  title?: string
  dismissible?: boolean
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'info',
  dismissible: false,
})

const emit = defineEmits<{
  dismiss: []
}>()

const iconComponent = computed(() => {
  switch (props.variant) {
    case 'success': return CheckCircleIcon
    case 'error': return XCircleIcon
    case 'warning': return ExclamationTriangleIcon
    case 'info': return InformationCircleIcon
  }
})

const colorClasses = computed(() => {
  switch (props.variant) {
    case 'success': return 'bg-green-50 text-green-800 dark:bg-green-500/10 dark:text-green-400'
    case 'error': return 'bg-red-50 text-red-800 dark:bg-red-500/10 dark:text-red-400'
    case 'warning': return 'bg-yellow-50 text-yellow-800 dark:bg-yellow-500/10 dark:text-yellow-400'
    case 'info': return 'bg-blue-50 text-blue-800 dark:bg-blue-500/10 dark:text-blue-400'
  }
})

const iconClasses = computed(() => {
  switch (props.variant) {
    case 'success': return 'text-green-400 dark:text-green-500'
    case 'error': return 'text-red-400 dark:text-red-500'
    case 'warning': return 'text-yellow-400 dark:text-yellow-500'
    case 'info': return 'text-blue-400 dark:text-blue-500'
  }
})
</script>

<template>
  <div :class="cn('rounded-md p-4', colorClasses, props.class)">
    <div class="flex">
      <div class="shrink-0">
        <component :is="iconComponent" :class="cn('size-5', iconClasses)" />
      </div>
      <div class="ml-3">
        <h3 v-if="title" class="text-sm font-medium">{{ title }}</h3>
        <div :class="cn('text-sm', title && 'mt-2')">
          <slot />
        </div>
      </div>
      <div v-if="dismissible" class="ml-auto pl-3">
        <button
          type="button"
          class="-m-1.5 inline-flex rounded-md p-1.5 focus:outline-none focus:ring-2 focus:ring-offset-2"
          @click="emit('dismiss')"
        >
          <span class="sr-only">Dismiss</span>
          <svg class="size-5" viewBox="0 0 20 20" fill="currentColor"><path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" /></svg>
        </button>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 12: Create AppEmptyState.vue**

Create `web/src/components/ui/AppEmptyState.vue`:

```vue
<script setup lang="ts">
import { cn } from '@/lib/utils'

interface Props {
  title: string
  description?: string
  icon?: object
  class?: string
}

const props = defineProps<Props>()
</script>

<template>
  <div :class="cn('text-center py-12', props.class)">
    <component
      :is="icon"
      v-if="icon"
      class="mx-auto size-12 text-gray-400 dark:text-gray-500"
    />
    <h3 class="mt-2 text-sm font-semibold text-gray-900 dark:text-white">{{ title }}</h3>
    <p v-if="description" class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ description }}</p>
    <div v-if="$slots.action" class="mt-6">
      <slot name="action" />
    </div>
  </div>
</template>
```

- [ ] **Step 13: Commit primitives**

```bash
git add web/src/components/ui/
git commit -m "feat: add primitive UI components (Button, Input, Select, Checkbox, Textarea, Badge, Avatar, Spinner, Divider, Card, Alert, EmptyState)"
```

---

## Task 7: Create Base UI Components — Headless UI

**Files:**
- Create: `web/src/components/ui/AppDialog.vue`
- Create: `web/src/components/ui/AppConfirmDialog.vue`
- Create: `web/src/components/ui/AppDropdown.vue`
- Create: `web/src/components/ui/AppListbox.vue`
- Create: `web/src/components/ui/AppCombobox.vue`
- Create: `web/src/components/ui/AppSwitch.vue`
- Create: `web/src/components/ui/AppTabs.vue`
- Create: `web/src/components/ui/AppToast.vue`
- Create: `web/src/components/ui/AppTable.vue`
- Create: `web/src/components/ui/AppPagination.vue`

- [ ] **Step 1: Create AppDialog.vue**

Create `web/src/components/ui/AppDialog.vue`:

```vue
<script setup lang="ts">
import {
  Dialog,
  DialogPanel,
  DialogTitle,
  TransitionChild,
  TransitionRoot,
} from '@headlessui/vue'
import { XMarkIcon } from '@heroicons/vue/24/outline'
import { cn } from '@/lib/utils'

interface Props {
  open: boolean
  title?: string
  size?: 'sm' | 'md' | 'lg' | 'xl'
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  size: 'md',
})

const emit = defineEmits<{
  close: []
}>()

const sizeClass = {
  sm: 'sm:max-w-sm',
  md: 'sm:max-w-lg',
  lg: 'sm:max-w-2xl',
  xl: 'sm:max-w-4xl',
}
</script>

<template>
  <TransitionRoot :show="open" as="template">
    <Dialog class="relative z-50" @close="emit('close')">
      <TransitionChild
        as="template"
        enter="ease-out duration-300" enter-from="opacity-0" enter-to="opacity-100"
        leave="ease-in duration-200" leave-from="opacity-100" leave-to="opacity-0"
      >
        <div class="fixed inset-0 bg-gray-500/75 dark:bg-gray-900/80 transition-opacity" />
      </TransitionChild>

      <div class="fixed inset-0 z-10 w-screen overflow-y-auto">
        <div class="flex min-h-full items-end justify-center p-4 text-center sm:items-center sm:p-0">
          <TransitionChild
            as="template"
            enter="ease-out duration-300"
            enter-from="opacity-0 translate-y-4 sm:translate-y-0 sm:scale-95"
            enter-to="opacity-100 translate-y-0 sm:scale-100"
            leave="ease-in duration-200"
            leave-from="opacity-100 translate-y-0 sm:scale-100"
            leave-to="opacity-0 translate-y-4 sm:translate-y-0 sm:scale-95"
          >
            <DialogPanel
              :class="cn(
                'relative transform overflow-hidden rounded-lg bg-white dark:bg-gray-800 px-4 pb-4 pt-5 text-left shadow-xl transition-all sm:my-8 sm:w-full sm:p-6',
                sizeClass[size],
                props.class
              )"
            >
              <div v-if="title" class="flex items-center justify-between mb-4">
                <DialogTitle class="text-lg font-semibold text-gray-900 dark:text-white">
                  {{ title }}
                </DialogTitle>
                <button
                  type="button"
                  class="rounded-md text-gray-400 hover:text-gray-500 dark:hover:text-gray-300 focus:outline-none"
                  @click="emit('close')"
                >
                  <XMarkIcon class="size-6" />
                </button>
              </div>
              <slot />
              <div v-if="$slots.footer" class="mt-5 sm:mt-6 flex justify-end gap-3">
                <slot name="footer" />
              </div>
            </DialogPanel>
          </TransitionChild>
        </div>
      </div>
    </Dialog>
  </TransitionRoot>
</template>
```

- [ ] **Step 2: Create AppConfirmDialog.vue**

Create `web/src/components/ui/AppConfirmDialog.vue`:

```vue
<script setup lang="ts">
import AppDialog from './AppDialog.vue'
import AppButton from './AppButton.vue'
import { ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import { useConfirm } from '@/composables/useConfirm'

const { state, handleConfirm, handleCancel } = useConfirm()
</script>

<template>
  <AppDialog
    :open="state.open"
    size="sm"
    @close="handleCancel"
  >
    <div class="sm:flex sm:items-start">
      <div
        :class="[
          'mx-auto flex size-12 shrink-0 items-center justify-center rounded-full sm:mx-0 sm:size-10',
          state.options.variant === 'danger' ? 'bg-red-100 dark:bg-red-500/10' : 'bg-primary-100 dark:bg-primary-500/10'
        ]"
      >
        <ExclamationTriangleIcon
          :class="[
            'size-6',
            state.options.variant === 'danger' ? 'text-red-600 dark:text-red-400' : 'text-primary-600 dark:text-primary-400'
          ]"
        />
      </div>
      <div class="mt-3 text-center sm:ml-4 sm:mt-0 sm:text-left">
        <h3 class="text-base font-semibold text-gray-900 dark:text-white">
          {{ state.options.title }}
        </h3>
        <div class="mt-2">
          <p class="text-sm text-gray-500 dark:text-gray-400">
            {{ state.options.message }}
          </p>
        </div>
      </div>
    </div>
    <div class="mt-5 sm:mt-4 sm:flex sm:flex-row-reverse gap-3">
      <AppButton
        :variant="state.options.variant === 'danger' ? 'danger' : 'primary'"
        size="sm"
        @click="handleConfirm"
      >
        {{ state.options.confirmLabel ?? 'Confirm' }}
      </AppButton>
      <AppButton variant="secondary" size="sm" @click="handleCancel">
        {{ state.options.cancelLabel ?? 'Cancel' }}
      </AppButton>
    </div>
  </AppDialog>
</template>
```

- [ ] **Step 3: Create AppDropdown.vue**

Create `web/src/components/ui/AppDropdown.vue`:

```vue
<script setup lang="ts">
import { Menu, MenuButton, MenuItem, MenuItems } from '@headlessui/vue'
import { cn } from '@/lib/utils'

export interface DropdownItem {
  label: string
  icon?: object
  disabled?: boolean
  danger?: boolean
  onClick?: () => void
}

interface Props {
  items: DropdownItem[]
  align?: 'left' | 'right'
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  align: 'right',
})
</script>

<template>
  <Menu as="div" :class="cn('relative inline-block text-left', props.class)">
    <MenuButton as="template">
      <slot name="trigger" />
    </MenuButton>

    <transition
      enter-active-class="transition ease-out duration-100"
      enter-from-class="transform opacity-0 scale-95"
      enter-to-class="transform opacity-100 scale-100"
      leave-active-class="transition ease-in duration-75"
      leave-from-class="transform opacity-100 scale-100"
      leave-to-class="transform opacity-0 scale-95"
    >
      <MenuItems
        :class="cn(
          'absolute z-10 mt-2 w-56 origin-top-right rounded-md bg-white dark:bg-gray-800 shadow-lg ring-1 ring-black/5 dark:ring-white/10 focus:outline-none',
          align === 'right' ? 'right-0' : 'left-0'
        )"
      >
        <div class="py-1">
          <MenuItem
            v-for="(item, idx) in items"
            :key="idx"
            :disabled="item.disabled"
            v-slot="{ active }"
          >
            <button
              type="button"
              :class="cn(
                'flex w-full items-center gap-2 px-4 py-2 text-sm',
                active ? 'bg-gray-100 dark:bg-white/10' : '',
                item.danger ? 'text-red-700 dark:text-red-400' : 'text-gray-700 dark:text-gray-300',
                item.disabled && 'opacity-50 cursor-not-allowed'
              )"
              @click="item.onClick?.()"
            >
              <component :is="item.icon" v-if="item.icon" class="size-4" />
              {{ item.label }}
            </button>
          </MenuItem>
        </div>
      </MenuItems>
    </transition>
  </Menu>
</template>
```

- [ ] **Step 4: Create AppListbox.vue**

Create `web/src/components/ui/AppListbox.vue`:

```vue
<script setup lang="ts">
import {
  Listbox,
  ListboxButton,
  ListboxOption,
  ListboxOptions,
} from '@headlessui/vue'
import { CheckIcon, ChevronUpDownIcon } from '@heroicons/vue/20/solid'
import { cn } from '@/lib/utils'

export interface ListboxItem {
  value: string
  label: string
  description?: string
  disabled?: boolean
}

interface Props {
  modelValue?: string | string[]
  items: ListboxItem[]
  label?: string
  placeholder?: string
  multiple?: boolean
  error?: string
  disabled?: boolean
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: 'Select...',
  multiple: false,
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string | string[]]
}>()

function displayValue(): string {
  if (!props.modelValue) return ''
  if (Array.isArray(props.modelValue)) {
    return props.modelValue
      .map(v => props.items.find(i => i.value === v)?.label ?? v)
      .join(', ')
  }
  return props.items.find(i => i.value === props.modelValue)?.label ?? ''
}
</script>

<template>
  <div :class="cn('w-full', props.class)">
    <label v-if="label" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
      {{ label }}
    </label>
    <Listbox
      :model-value="modelValue"
      :multiple="multiple"
      :disabled="disabled"
      @update:model-value="emit('update:modelValue', $event)"
    >
      <div class="relative">
        <ListboxButton
          :class="cn(
            'grid w-full cursor-default grid-cols-1 rounded-md bg-white py-2 pl-3 pr-10 text-left text-gray-900 outline-1 -outline-offset-1 outline-gray-300 focus:outline-2 focus:-outline-offset-2 focus:outline-primary-600 sm:text-sm/6 dark:bg-white/5 dark:text-white dark:outline-white/10',
            error && 'outline-red-500'
          )"
        >
          <span class="col-start-1 row-start-1 truncate">
            {{ displayValue() || placeholder }}
          </span>
          <ChevronUpDownIcon class="col-start-1 row-start-1 size-5 self-center justify-self-end text-gray-500" />
        </ListboxButton>

        <transition
          leave-active-class="transition ease-in duration-100"
          leave-from-class="opacity-100"
          leave-to-class="opacity-0"
        >
          <ListboxOptions class="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-md bg-white dark:bg-gray-800 py-1 text-base shadow-lg ring-1 ring-black/5 dark:ring-white/10 focus:outline-none sm:text-sm">
            <ListboxOption
              v-for="item in items"
              :key="item.value"
              :value="item.value"
              :disabled="item.disabled"
              v-slot="{ active, selected }"
              as="template"
            >
              <li
                :class="cn(
                  'relative cursor-default select-none py-2 pl-3 pr-9',
                  active ? 'bg-primary-600 text-white' : 'text-gray-900 dark:text-gray-300',
                  item.disabled && 'opacity-50'
                )"
              >
                <span :class="cn('block truncate', selected && 'font-semibold')">{{ item.label }}</span>
                <span v-if="item.description" :class="cn('block truncate text-xs', active ? 'text-primary-200' : 'text-gray-500')">
                  {{ item.description }}
                </span>
                <span
                  v-if="selected"
                  :class="cn('absolute inset-y-0 right-0 flex items-center pr-4', active ? 'text-white' : 'text-primary-600')"
                >
                  <CheckIcon class="size-5" />
                </span>
              </li>
            </ListboxOption>
          </ListboxOptions>
        </transition>
      </div>
    </Listbox>
    <p v-if="error" class="mt-2 text-sm text-red-600 dark:text-red-400">{{ error }}</p>
  </div>
</template>
```

- [ ] **Step 5: Create AppCombobox.vue**

Create `web/src/components/ui/AppCombobox.vue`:

```vue
<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  Combobox,
  ComboboxButton,
  ComboboxInput,
  ComboboxOption,
  ComboboxOptions,
} from '@headlessui/vue'
import { CheckIcon, ChevronUpDownIcon } from '@heroicons/vue/20/solid'
import { cn } from '@/lib/utils'

export interface ComboboxItem {
  value: string
  label: string
}

interface Props {
  modelValue?: string
  items: ComboboxItem[]
  label?: string
  placeholder?: string
  error?: string
  disabled?: boolean
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: 'Search...',
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const query = ref('')

const filteredItems = computed(() =>
  query.value === ''
    ? props.items
    : props.items.filter(item =>
        item.label.toLowerCase().includes(query.value.toLowerCase())
      )
)

function displayValue(value: unknown): string {
  if (typeof value !== 'string') return ''
  return props.items.find(i => i.value === value)?.label ?? ''
}
</script>

<template>
  <div :class="cn('w-full', props.class)">
    <label v-if="label" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
      {{ label }}
    </label>
    <Combobox
      :model-value="modelValue"
      :disabled="disabled"
      @update:model-value="emit('update:modelValue', $event as string)"
    >
      <div class="relative">
        <ComboboxInput
          :class="cn(
            'w-full rounded-md border-0 bg-white py-2 pl-3 pr-10 text-gray-900 shadow-xs outline-1 -outline-offset-1 outline-gray-300 focus:outline-2 focus:-outline-offset-2 focus:outline-primary-600 sm:text-sm/6 dark:bg-white/5 dark:text-white dark:outline-white/10',
            error && 'outline-red-500'
          )"
          :display-value="displayValue"
          :placeholder="placeholder"
          @change="query = $event.target.value"
        />
        <ComboboxButton class="absolute inset-y-0 right-0 flex items-center pr-2">
          <ChevronUpDownIcon class="size-5 text-gray-400" />
        </ComboboxButton>

        <ComboboxOptions
          v-if="filteredItems.length > 0"
          class="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-md bg-white dark:bg-gray-800 py-1 text-base shadow-lg ring-1 ring-black/5 dark:ring-white/10 focus:outline-none sm:text-sm"
        >
          <ComboboxOption
            v-for="item in filteredItems"
            :key="item.value"
            :value="item.value"
            v-slot="{ active, selected }"
            as="template"
          >
            <li
              :class="cn(
                'relative cursor-default select-none py-2 pl-3 pr-9',
                active ? 'bg-primary-600 text-white' : 'text-gray-900 dark:text-gray-300'
              )"
            >
              <span :class="cn('block truncate', selected && 'font-semibold')">{{ item.label }}</span>
              <span
                v-if="selected"
                :class="cn('absolute inset-y-0 right-0 flex items-center pr-4', active ? 'text-white' : 'text-primary-600')"
              >
                <CheckIcon class="size-5" />
              </span>
            </li>
          </ComboboxOption>
        </ComboboxOptions>
      </div>
    </Combobox>
    <p v-if="error" class="mt-2 text-sm text-red-600 dark:text-red-400">{{ error }}</p>
  </div>
</template>
```

- [ ] **Step 6: Create AppSwitch.vue**

Create `web/src/components/ui/AppSwitch.vue`:

```vue
<script setup lang="ts">
import { Switch, SwitchGroup, SwitchLabel } from '@headlessui/vue'
import { cn } from '@/lib/utils'

interface Props {
  modelValue: boolean
  label?: string
  description?: string
  disabled?: boolean
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()
</script>

<template>
  <SwitchGroup as="div" :class="cn('flex items-center', props.class)">
    <Switch
      :model-value="modelValue"
      :disabled="disabled"
      :class="cn(
        'relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-600 focus:ring-offset-2 dark:focus:ring-offset-gray-900',
        modelValue ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-700',
        disabled && 'opacity-50 cursor-not-allowed'
      )"
      @update:model-value="emit('update:modelValue', $event)"
    >
      <span
        :class="cn(
          'pointer-events-none inline-block size-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out',
          modelValue ? 'translate-x-5' : 'translate-x-0'
        )"
      />
    </Switch>
    <SwitchLabel v-if="label" as="span" class="ml-3 text-sm" passive>
      <span class="font-medium text-gray-900 dark:text-white">{{ label }}</span>
      <span v-if="description" class="text-gray-500 dark:text-gray-400"> {{ description }}</span>
    </SwitchLabel>
  </SwitchGroup>
</template>
```

- [ ] **Step 7: Create AppTabs.vue**

Create `web/src/components/ui/AppTabs.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

export interface TabItem {
  key: string
  label: string
  icon?: object
}

interface Props {
  tabs: TabItem[]
  modelValue: string
  class?: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const currentIndex = computed(() =>
  props.tabs.findIndex(t => t.key === props.modelValue)
)
</script>

<template>
  <div :class="cn('w-full', props.class)">
    <!-- Mobile select -->
    <div class="sm:hidden">
      <select
        :value="modelValue"
        class="block w-full rounded-md bg-white py-2 pl-3 pr-10 text-base text-gray-900 outline-1 -outline-offset-1 outline-gray-300 focus:outline-2 focus:-outline-offset-2 focus:outline-primary-600 dark:bg-white/5 dark:text-white dark:outline-white/10"
        @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
      >
        <option v-for="tab in tabs" :key="tab.key" :value="tab.key">{{ tab.label }}</option>
      </select>
    </div>

    <!-- Desktop tabs -->
    <div class="hidden sm:block">
      <nav class="-mb-px flex gap-x-8 border-b border-gray-200 dark:border-gray-700" aria-label="Tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          :class="cn(
            'flex items-center gap-2 whitespace-nowrap border-b-2 px-1 py-4 text-sm font-medium transition-colors',
            tab.key === modelValue
              ? 'border-primary-500 text-primary-600 dark:text-primary-400'
              : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'
          )"
          @click="emit('update:modelValue', tab.key)"
        >
          <component :is="tab.icon" v-if="tab.icon" class="size-5" />
          {{ tab.label }}
        </button>
      </nav>
    </div>
  </div>
</template>
```

- [ ] **Step 8: Create AppToast.vue**

Create `web/src/components/ui/AppToast.vue`:

```vue
<script setup lang="ts">
import { useToast } from '@/composables/useToast'
import {
  CheckCircleIcon,
  ExclamationTriangleIcon,
  InformationCircleIcon,
  XCircleIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'

const { toasts, removeToast } = useToast()

function iconFor(type: string) {
  switch (type) {
    case 'success': return CheckCircleIcon
    case 'error': return XCircleIcon
    case 'warning': return ExclamationTriangleIcon
    default: return InformationCircleIcon
  }
}

function iconColor(type: string) {
  switch (type) {
    case 'success': return 'text-green-400'
    case 'error': return 'text-red-400'
    case 'warning': return 'text-yellow-400'
    default: return 'text-blue-400'
  }
}
</script>

<template>
  <div aria-live="assertive" class="pointer-events-none fixed inset-0 z-50 flex items-end px-4 py-6 sm:items-start sm:p-6">
    <div class="flex w-full flex-col items-center gap-4 sm:items-end">
      <TransitionGroup
        enter-active-class="transform ease-out duration-300 transition"
        enter-from-class="translate-y-2 opacity-0 sm:translate-y-0 sm:translate-x-2"
        enter-to-class="translate-y-0 opacity-100 sm:translate-x-0"
        leave-active-class="transition ease-in duration-100"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-for="toast in toasts"
          :key="toast.id"
          class="pointer-events-auto w-full max-w-sm overflow-hidden rounded-lg bg-white dark:bg-gray-800 shadow-lg ring-1 ring-black/5 dark:ring-white/10"
        >
          <div class="p-4">
            <div class="flex items-start">
              <div class="shrink-0">
                <component :is="iconFor(toast.type)" :class="['size-6', iconColor(toast.type)]" />
              </div>
              <div class="ml-3 w-0 flex-1 pt-0.5">
                <p class="text-sm font-medium text-gray-900 dark:text-white">
                  {{ toast.message }}
                </p>
              </div>
              <div class="ml-4 flex shrink-0">
                <button
                  type="button"
                  class="inline-flex rounded-md text-gray-400 hover:text-gray-500 dark:hover:text-gray-300 focus:outline-none"
                  @click="removeToast(toast.id)"
                >
                  <XMarkIcon class="size-5" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </TransitionGroup>
    </div>
  </div>
</template>
```

- [ ] **Step 9: Create AppTable.vue**

Create `web/src/components/ui/AppTable.vue`:

```vue
<script setup lang="ts" generic="T">
import { cn } from '@/lib/utils'

export interface TableColumn<T> {
  key: string
  label: string
  sortable?: boolean
  class?: string
}

interface Props {
  columns: TableColumn<T>[]
  data: T[]
  sortKey?: string
  sortDirection?: 'asc' | 'desc'
  loading?: boolean
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  sortDirection: 'asc',
})

const emit = defineEmits<{
  sort: [key: string]
  'row-click': [item: T]
}>()
</script>

<template>
  <div :class="cn('overflow-hidden shadow-sm ring-1 ring-gray-900/5 dark:ring-white/10 sm:rounded-lg', props.class)">
    <table class="min-w-full divide-y divide-gray-300 dark:divide-gray-700">
      <thead class="bg-gray-50 dark:bg-gray-800">
        <tr>
          <th
            v-for="col in columns"
            :key="col.key"
            scope="col"
            :class="cn(
              'px-3 py-3.5 text-left text-sm font-semibold text-gray-900 dark:text-white',
              col.sortable && 'cursor-pointer select-none hover:bg-gray-100 dark:hover:bg-gray-700',
              col.class
            )"
            @click="col.sortable ? emit('sort', col.key) : undefined"
          >
            <div class="flex items-center gap-2">
              {{ col.label }}
              <template v-if="col.sortable && sortKey === col.key">
                <svg v-if="sortDirection === 'asc'" class="size-4" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 17a.75.75 0 01-.75-.75V5.612L5.29 9.77a.75.75 0 01-1.08-1.04l5.25-5.5a.75.75 0 011.08 0l5.25 5.5a.75.75 0 11-1.08 1.04l-3.96-4.158V16.25A.75.75 0 0110 17z" clip-rule="evenodd" /></svg>
                <svg v-else class="size-4" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 3a.75.75 0 01.75.75v10.638l3.96-4.158a.75.75 0 111.08 1.04l-5.25 5.5a.75.75 0 01-1.08 0l-5.25-5.5a.75.75 0 111.08-1.04l3.96 4.158V3.75A.75.75 0 0110 3z" clip-rule="evenodd" /></svg>
              </template>
            </div>
          </th>
        </tr>
      </thead>
      <tbody class="divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-900">
        <tr v-if="loading">
          <td :colspan="columns.length" class="px-3 py-8 text-center text-sm text-gray-500">
            <div class="flex justify-center">
              <svg class="animate-spin h-6 w-6 text-gray-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
            </div>
          </td>
        </tr>
        <tr v-else-if="data.length === 0">
          <td :colspan="columns.length" class="px-3 py-8 text-center text-sm text-gray-500 dark:text-gray-400">
            <slot name="empty">No data available</slot>
          </td>
        </tr>
        <tr
          v-else
          v-for="(item, idx) in data"
          :key="idx"
          class="hover:bg-gray-50 dark:hover:bg-gray-800/50 cursor-pointer"
          @click="emit('row-click', item)"
        >
          <td
            v-for="col in columns"
            :key="col.key"
            :class="cn('whitespace-nowrap px-3 py-4 text-sm text-gray-500 dark:text-gray-300', col.class)"
          >
            <slot :name="`cell-${col.key}`" :item="item" :value="(item as any)[col.key]">
              {{ (item as any)[col.key] }}
            </slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
```

- [ ] **Step 10: Create AppPagination.vue**

Create `web/src/components/ui/AppPagination.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'
import { ChevronLeftIcon, ChevronRightIcon } from '@heroicons/vue/20/solid'

interface Props {
  currentPage: number
  totalPages: number
  class?: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:currentPage': [page: number]
}>()

const pages = computed(() => {
  const p: (number | '...')[] = []
  const total = props.totalPages
  const current = props.currentPage

  if (total <= 7) {
    for (let i = 1; i <= total; i++) p.push(i)
  } else {
    p.push(1)
    if (current > 3) p.push('...')
    const start = Math.max(2, current - 1)
    const end = Math.min(total - 1, current + 1)
    for (let i = start; i <= end; i++) p.push(i)
    if (current < total - 2) p.push('...')
    p.push(total)
  }
  return p
})
</script>

<template>
  <nav :class="cn('flex items-center justify-between border-t border-gray-200 dark:border-gray-700 px-4 sm:px-0', props.class)">
    <div class="-mt-px flex w-0 flex-1">
      <button
        :disabled="currentPage <= 1"
        class="inline-flex items-center border-t-2 border-transparent pr-1 pt-4 text-sm font-medium text-gray-500 hover:border-gray-300 hover:text-gray-700 disabled:opacity-50 disabled:cursor-not-allowed dark:text-gray-400 dark:hover:text-gray-300"
        @click="emit('update:currentPage', currentPage - 1)"
      >
        <ChevronLeftIcon class="mr-3 size-5" />
        Previous
      </button>
    </div>
    <div class="hidden md:-mt-px md:flex">
      <template v-for="(page, idx) in pages" :key="idx">
        <span v-if="page === '...'" class="inline-flex items-center border-t-2 border-transparent px-4 pt-4 text-sm font-medium text-gray-500">...</span>
        <button
          v-else
          :class="cn(
            'inline-flex items-center border-t-2 px-4 pt-4 text-sm font-medium',
            page === currentPage
              ? 'border-primary-500 text-primary-600 dark:text-primary-400'
              : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400'
          )"
          @click="emit('update:currentPage', page)"
        >
          {{ page }}
        </button>
      </template>
    </div>
    <div class="-mt-px flex w-0 flex-1 justify-end">
      <button
        :disabled="currentPage >= totalPages"
        class="inline-flex items-center border-t-2 border-transparent pl-1 pt-4 text-sm font-medium text-gray-500 hover:border-gray-300 hover:text-gray-700 disabled:opacity-50 disabled:cursor-not-allowed dark:text-gray-400 dark:hover:text-gray-300"
        @click="emit('update:currentPage', currentPage + 1)"
      >
        Next
        <ChevronRightIcon class="ml-3 size-5" />
      </button>
    </div>
  </nav>
</template>
```

- [ ] **Step 11: Commit Headless UI components**

```bash
git add web/src/components/ui/
git commit -m "feat: add Headless UI components (Dialog, ConfirmDialog, Dropdown, Listbox, Combobox, Switch, Tabs, Toast, Table, Pagination)"
```

---

## Task 8: Create Layout Shell

**Files:**
- Create: `web/src/components/layout/TheSidebar.vue`
- Create: `web/src/components/layout/TheNavbar.vue`
- Create: `web/src/components/layout/SidebarLayout.vue`

This task creates the sidebar-based app shell replacing the current top-bar `MainLayout.vue`. The implementer should:

1. Build `TheSidebar.vue` — Ravencloak logo, realm navigation links (Users, Clients, Roles, Groups, IdP, Audit), mobile slide-over via Headless UI Dialog
2. Build `TheNavbar.vue` — breadcrumbs, dark mode toggle (using `useDarkMode`), user avatar dropdown (using `AppDropdown`) with logout
3. Build `SidebarLayout.vue` — assembles sidebar + navbar + `<router-view>` content area
4. Reference the Tailwind Plus Sidebar Layout block patterns for markup structure

The sidebar should highlight the current route. Navigation links should use `router-link`. Mobile hamburger opens sidebar as overlay.

- [ ] **Step 1: Create TheSidebar.vue, TheNavbar.vue, SidebarLayout.vue**

Build these three components following the Tailwind Plus sidebar layout patterns. Reuse auth store for user info, useDarkMode for theme toggle, AppDropdown/AppAvatar for user menu.

- [ ] **Step 2: Update router to use SidebarLayout**

Modify `web/src/router/index.ts`: change the layout import from `@/layouts/MainLayout.vue` to `@/components/layout/SidebarLayout.vue`.

- [ ] **Step 3: Commit**

```bash
git add web/src/components/layout/ web/src/router/index.ts
git commit -m "feat: add sidebar layout shell with dark mode toggle and user menu"
```

---

## Task 9: Update App Entry Points

**Files:**
- Modify: `web/src/main.ts`
- Modify: `web/src/App.vue`
- Delete: `web/src/layouts/MainLayout.vue` (replaced by SidebarLayout)

- [ ] **Step 1: Rewrite main.ts**

Replace `web/src/main.ts` with:

```typescript
import { initTelemetry } from './telemetry'

if (import.meta.env.VITE_OTEL_ENABLED !== 'false') {
  initTelemetry()
}

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'

import App from './App.vue'
import router from './router'

import './assets/styles/app.css'

const app = createApp(App)

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

app.use(pinia)
app.use(router)

app.mount('#app')
```

- [ ] **Step 2: Rewrite App.vue**

Replace `web/src/App.vue` with:

```vue
<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppToast from '@/components/ui/AppToast.vue'
import AppConfirmDialog from '@/components/ui/AppConfirmDialog.vue'
</script>

<template>
  <RouterView />
  <AppToast />
  <AppConfirmDialog />
</template>
```

- [ ] **Step 3: Delete old MainLayout**

```bash
rm web/src/layouts/MainLayout.vue
```

- [ ] **Step 4: Commit**

```bash
git add web/src/main.ts web/src/App.vue
git rm web/src/layouts/MainLayout.vue
git commit -m "feat: update app entry points, remove PrimeVue setup"
```

---

## Task 10: Delete Legacy Files

**Files:**
- Delete: `web/src/views/` (entire directory)
- Delete: `web/src/services/api.ts`

- [ ] **Step 1: Delete legacy views and duplicate API**

```bash
rm -rf web/src/views/
rm web/src/services/api.ts
```

- [ ] **Step 2: Verify no imports reference deleted files**

```bash
cd web && grep -r "from.*views/" src/ || echo "No references"
cd web && grep -r "from.*services/api" src/ || echo "No references"
```

Fix any remaining imports if found.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: remove legacy views/ and duplicate services/api.ts"
```

---

## Task 11: Rewrite Domain Components

**Files:**
- Rewrite: `web/src/components/RealmCard.vue`
- Rewrite: `web/src/components/ClientCard.vue`
- Rewrite: `web/src/components/ClientList.vue`
- Rewrite: `web/src/components/UserList.vue`
- Rewrite: `web/src/components/RoleList.vue`
- Rewrite: `web/src/components/GroupList.vue`
- Rewrite: `web/src/components/AuditTimeline.vue`
- Rewrite: `web/src/components/AuditDiffViewer.vue`
- Rewrite: `web/src/components/IntegrationSnippets.vue`

For each component:
1. Keep the `<script setup>` logic (props, emits, computed, API calls) intact
2. Replace PrimeVue component imports with `components/ui/` imports
3. Replace PrimeVue template markup with Tailwind Plus patterns using our `ui/` components
4. Remove all `<style scoped>` blocks — use Tailwind utility classes only
5. Replace `pi pi-*` icons with Heroicon imports from `@heroicons/vue`
6. Replace `useToast()` from `primevue/usetoast` with `useToast()` from `@/composables/useToast`

- [ ] **Step 1: Rewrite all 9 domain components**

Rewrite each file preserving business logic but replacing all PrimeVue markup with Tailwind Plus patterns and our `ui/` components.

- [ ] **Step 2: Verify no PrimeVue imports remain in components/**

```bash
cd web && grep -r "primevue" src/components/ || echo "Clean"
cd web && grep -r "primeicons" src/components/ || echo "Clean"
cd web && grep -r "\-\-p\-" src/components/ || echo "Clean"
```

- [ ] **Step 3: Commit**

```bash
git add web/src/components/
git commit -m "feat: rewrite domain components with Tailwind Plus UI patterns"
```

---

## Task 12: Rewrite Pages — Auth & Navigation

**Files:**
- Rewrite: `web/src/pages/login.vue`
- Rewrite: `web/src/pages/access-denied.vue`
- Rewrite: `web/src/pages/index.vue`
- Rewrite: `web/src/pages/my-actions.vue`

For each page:
1. Keep route params, API calls, reactive state, computed properties intact
2. Replace PrimeVue components with `ui/` components
3. Replace all `<style scoped>` with Tailwind utility classes
4. Replace PrimeIcons with Heroicons

- [ ] **Step 1: Rewrite login.vue, access-denied.vue, index.vue, my-actions.vue**
- [ ] **Step 2: Commit**

```bash
git add web/src/pages/login.vue web/src/pages/access-denied.vue web/src/pages/index.vue web/src/pages/my-actions.vue
git commit -m "feat: rewrite auth and navigation pages with Tailwind Plus"
```

---

## Task 13: Rewrite Pages — Realms

**Files:**
- Rewrite: `web/src/pages/realms/index.vue`
- Rewrite: `web/src/pages/realms/create.vue`
- Rewrite: `web/src/pages/realms/[name]/index.vue`

- [ ] **Step 1: Rewrite realm pages preserving all business logic**
- [ ] **Step 2: Commit**

```bash
git add web/src/pages/realms/
git commit -m "feat: rewrite realm pages with Tailwind Plus"
```

---

## Task 14: Rewrite Pages — Users

**Files:**
- Rewrite: `web/src/pages/realms/[name]/users/index.vue`
- Rewrite: `web/src/pages/realms/[name]/users/create.vue`
- Rewrite: `web/src/pages/realms/[name]/users/[userId]/index.vue`

- [ ] **Step 1: Rewrite user pages preserving all business logic**
- [ ] **Step 2: Commit**

```bash
git add web/src/pages/realms/\[name\]/users/
git commit -m "feat: rewrite user pages with Tailwind Plus"
```

---

## Task 15: Rewrite Pages — Clients

**Files:**
- Rewrite: `web/src/pages/realms/[name]/clients/index.vue`
- Rewrite: `web/src/pages/realms/[name]/clients/create.vue`
- Rewrite: `web/src/pages/realms/[name]/clients/[clientId]/index.vue`

- [ ] **Step 1: Rewrite client pages preserving all business logic**
- [ ] **Step 2: Commit**

```bash
git add web/src/pages/realms/\[name\]/clients/
git commit -m "feat: rewrite client pages with Tailwind Plus"
```

---

## Task 16: Rewrite Pages — Roles, Groups, IdP, Audit

**Files:**
- Rewrite: `web/src/pages/realms/[name]/roles/index.vue`
- Rewrite: `web/src/pages/realms/[name]/roles/create.vue`
- Rewrite: `web/src/pages/realms/[name]/groups/index.vue`
- Rewrite: `web/src/pages/realms/[name]/groups/create.vue`
- Rewrite: `web/src/pages/realms/[name]/idp/index.vue`
- Rewrite: `web/src/pages/realms/[name]/idp/create.vue`
- Rewrite: `web/src/pages/realms/[name]/audit/index.vue`

- [ ] **Step 1: Rewrite all remaining entity pages**
- [ ] **Step 2: Commit**

```bash
git add web/src/pages/realms/\[name\]/roles/ web/src/pages/realms/\[name\]/groups/ web/src/pages/realms/\[name\]/idp/ web/src/pages/realms/\[name\]/audit/
git commit -m "feat: rewrite roles, groups, idp, audit pages with Tailwind Plus"
```

---

## Task 17: Final Cleanup and Verification

**Files:**
- Verify: entire `web/src/` directory

- [ ] **Step 1: Verify zero PrimeVue references remain**

```bash
cd web && grep -r "primevue" src/ || echo "Clean"
cd web && grep -r "primeicons" src/ || echo "Clean"
cd web && grep -r "\-\-p\-" src/ || echo "No PrimeVue CSS vars"
cd web && grep -r "pi pi-" src/ || echo "No PrimeIcons"
```

Fix any remaining references.

- [ ] **Step 2: Run type check**

```bash
cd web && npm run type-check
```

Expected: No errors.

- [ ] **Step 3: Run build**

```bash
cd web && npm run build
```

Expected: Build succeeds.

- [ ] **Step 4: Run dev server and smoke test**

```bash
cd web && npm run dev
```

Verify the app loads at `http://localhost:5173`.

- [ ] **Step 5: Commit any fixes**

```bash
git add -A
git commit -m "fix: resolve remaining type errors and build issues"
```

---

## Task 18: Install and Configure Playwright

**Files:**
- Modify: `web/package.json` (add @playwright/test)
- Create: `web/e2e/playwright.config.ts`
- Create: `web/e2e/fixtures/test-setup.ts`

- [ ] **Step 1: Install Playwright**

```bash
cd web && npm install -D @playwright/test
cd web && npx playwright install chromium
```

- [ ] **Step 2: Create playwright.config.ts**

Create `web/e2e/playwright.config.ts`:

```typescript
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    cwd: '..',
  },
})
```

- [ ] **Step 3: Create test fixture with mock auth**

Create `web/e2e/fixtures/test-setup.ts`:

```typescript
import { test as base } from '@playwright/test'

export const test = base.extend({
  page: async ({ page }, use) => {
    // Mock Keycloak init — inject a fake authenticated state
    await page.addInitScript(() => {
      (window as any).__MOCK_AUTH__ = {
        authenticated: true,
        token: 'mock-jwt-token',
        tokenParsed: {
          sub: 'test-user-id',
          email: 'admin@ravencloak.org',
          name: 'Test Admin',
          given_name: 'Test',
          family_name: 'Admin',
          preferred_username: 'admin',
          realm_access: { roles: ['SUPER_ADMIN'] },
        },
      }
    })
    await use(page)
  },
})

export { expect } from '@playwright/test'
```

- [ ] **Step 4: Add test script to package.json**

Add to `web/package.json` scripts:
```json
"test:e2e": "playwright test --config=e2e/playwright.config.ts"
```

- [ ] **Step 5: Commit**

```bash
git add web/e2e/ web/package.json web/package-lock.json
git commit -m "feat: install Playwright and configure e2e test infrastructure"
```

---

## Task 19: Write Playwright Page Objects

**Files:**
- Create: `web/e2e/pages/LoginPage.ts`
- Create: `web/e2e/pages/RealmListPage.ts`
- Create: `web/e2e/pages/RealmDashboardPage.ts`
- Create: `web/e2e/pages/UserListPage.ts`
- Create: `web/e2e/pages/SidebarNav.ts`

Build Page Object Model classes for each major page. Each class encapsulates selectors and common actions (navigate, click, fill form, assert element visible).

- [ ] **Step 1: Create page objects for all major pages**
- [ ] **Step 2: Commit**

```bash
git add web/e2e/pages/
git commit -m "feat: add Playwright page object models"
```

---

## Task 20: Write Playwright E2E Tests

**Files:**
- Create: `web/e2e/tests/auth.spec.ts`
- Create: `web/e2e/tests/realms.spec.ts`
- Create: `web/e2e/tests/users.spec.ts`
- Create: `web/e2e/tests/clients.spec.ts`
- Create: `web/e2e/tests/roles-groups.spec.ts`
- Create: `web/e2e/tests/audit.spec.ts`
- Create: `web/e2e/tests/dark-mode.spec.ts`

- [ ] **Step 1: Write auth flow tests** (login redirect, access denied)
- [ ] **Step 2: Write realm CRUD tests** (list, create, dashboard navigation)
- [ ] **Step 3: Write user CRUD tests** (list, create, detail view)
- [ ] **Step 4: Write client CRUD tests** (list, create, detail view, integration snippets)
- [ ] **Step 5: Write roles & groups tests** (list, create)
- [ ] **Step 6: Write audit trail tests** (timeline view)
- [ ] **Step 7: Write dark mode tests** (toggle, persistence)
- [ ] **Step 8: Run all Playwright tests**

```bash
cd web && npm run test:e2e
```

- [ ] **Step 9: Fix any failures and re-run**
- [ ] **Step 10: Commit**

```bash
git add web/e2e/tests/
git commit -m "feat: add Playwright e2e tests for all major flows"
```

---

## Task 21: Final Build Verification

- [ ] **Step 1: Run full type check**

```bash
cd web && npm run type-check
```

- [ ] **Step 2: Run production build**

```bash
cd web && npm run build
```

- [ ] **Step 3: Run all e2e tests**

```bash
cd web && npm run test:e2e
```

- [ ] **Step 4: Verify no PrimeVue remnants**

```bash
cd web && grep -r "primevue\|primeicons\|--p-\|pi pi-" src/ || echo "All clean"
```

- [ ] **Step 5: Final commit if needed**

```bash
git add -A
git commit -m "chore: final cleanup and verification"
```
