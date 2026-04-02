# Tailwind Plus Migration Design Spec

**Date:** 2026-03-17
**Status:** Approved
**Author:** AI-assisted design session

## Goal

Replace PrimeVue with Tailwind CSS v4 + Tailwind Plus Application UI blocks + Headless UI for Vue in the Ravencloak admin portal (`web/`). Full rip-and-replace in a dedicated git worktree branch. Clean up legacy code. Add Playwright end-to-end tests.

## Context

The Ravencloak admin portal is a Vue 3 + TypeScript + Vite application currently using PrimeVue 4 (Aura preset) as its component library. The UI is deeply coupled to PrimeVue:

- **30 active `.vue` files** importing PrimeVue components
- **27 distinct PrimeVue components** used across the app
- **All styling** uses PrimeVue CSS variables (`--p-*` tokens)
- **PrimeVue services** (`useToast`, `useConfirm`) used in 20 files
- **PrimeIcons** (`pi pi-*`) for all iconography
- **Zero native HTML form elements** -- everything is PrimeVue

The owner has a Tailwind Plus subscription and wants to use the Application UI blocks (pre-built HTML/Tailwind markup patterns) adapted into Vue components, with `@headlessui/vue` for interactive behavior.

## Approach

**Full rip-and-replace** in a separate git worktree branch (`tailwind-plus`). Remove all PrimeVue dependencies, build a reusable component library layer (`components/ui/`), rewrite all 20 pages against it, clean up legacy code, and add Playwright tests.

This approach was chosen over incremental migration because:
1. The worktree provides safety -- `main` stays intact
2. No mixed styling periods (PrimeVue CSS vars + Tailwind coexisting)
3. Consistent design language from day one

## Tech Stack Changes

### Remove
| Package | Reason |
|---------|--------|
| `primevue` | Replaced by Tailwind Plus UI blocks |
| `@primevue/themes` | No longer needed |
| `primeicons` | Replaced by Heroicons |

### Add
| Package | Version | Purpose |
|---------|---------|---------|
| `tailwindcss` | v4 (latest) | Utility-first CSS framework |
| `@tailwindcss/vite` | latest | Vite integration for Tailwind v4 |
| `@headlessui/vue` | latest | Accessible interactive components (Dialog, Menu, Listbox, Combobox, Switch, Transition) |
| `@heroicons/vue` | latest | Icon library (replaces PrimeIcons) |
| `clsx` | latest | Conditional class joining |
| `tailwind-merge` | latest | Merge conflicting Tailwind classes (used in `cn()` utility) |
| `@playwright/test` | latest | End-to-end testing (devDependency) |

### Keep Unchanged
- Vue 3, Vite 6, TypeScript 5.7
- vue-router 4, unplugin-vue-router (file-based routing)
- Pinia + pinia-plugin-persistedstate
- Axios, keycloak-js
- All OpenTelemetry packages
- ESLint + vue-tsc

## Project Structure

```
web/src/
├── api/                          # UNCHANGED (8 API modules + client.ts + index.ts)
├── assets/
│   └── styles/
│       └── app.css               # NEW — Tailwind entry + @theme config + dark mode
├── components/
│   ├── ui/                       # NEW — reusable base component library
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
│   ├── layout/
│   │   ├── SidebarLayout.vue     # App shell with sidebar nav
│   │   ├── TheSidebar.vue        # Sidebar with realm navigation
│   │   └── TheNavbar.vue         # Top bar (breadcrumbs, dark mode, user menu)
│   ├── AuditTimeline.vue         # REWRITE template
│   ├── AuditDiffViewer.vue       # REWRITE template
│   ├── ClientCard.vue            # REWRITE template
│   ├── ClientList.vue            # REWRITE template
│   ├── UserList.vue              # REWRITE template
│   ├── RoleList.vue              # REWRITE template
│   ├── GroupList.vue             # REWRITE template
│   ├── RealmCard.vue             # REWRITE template
│   └── IntegrationSnippets.vue   # REWRITE template
├── composables/                  # NEW
│   ├── useToast.ts               # Replaces PrimeVue useToast
│   ├── useConfirm.ts             # Replaces PrimeVue useConfirm
│   └── useDarkMode.ts            # Dark mode toggle with localStorage
├── pages/                        # KEEP structure, REWRITE all templates
│   ├── index.vue
│   ├── login.vue
│   ├── access-denied.vue
│   ├── my-actions.vue
│   └── realms/
│       ├── index.vue
│       ├── create.vue
│       └── [name]/
│           ├── index.vue
│           ├── users/ (index, create, [userId]/index)
│           ├── clients/ (index, create, [clientId]/index)
│           ├── roles/ (index, create)
│           ├── groups/ (index, create)
│           ├── idp/ (index, create)
│           └── audit/ (index)
├── stores/                       # UNCHANGED (auth.ts, realm.ts)
├── router/                       # MINOR UPDATE — layout import reference
├── telemetry/                    # UNCHANGED
├── App.vue                       # REWRITE — remove PrimeVue, add toast/confirm containers
└── main.ts                       # REWRITE — remove PrimeVue setup, import app.css

DELETED:
├── views/                        # 4 legacy files (RealmListView, RealmDashboardView, etc.)
└── services/api.ts               # Duplicate older API module
```

## Component Library Design (`components/ui/`)

Each component wraps Tailwind Plus Application UI block patterns into reusable Vue components with typed props, slots, and emits.

### Component Mapping

| New Component | Replaces (PrimeVue) | Interactive Behavior |
|---------------|---------------------|---------------------|
| `AppButton` | `Button` | Native `<button>` |
| `AppInput` | `InputText` | Native `<input>` |
| `AppSelect` | `Select` | Native `<select>` |
| `AppListbox` | `MultiSelect`, `SelectButton` | `@headlessui/vue` Listbox |
| `AppCombobox` | `Chips` (search+select) | `@headlessui/vue` Combobox |
| `AppCheckbox` | `Checkbox` | Native `<input type="checkbox">` |
| `AppTextarea` | `Textarea` | Native `<textarea>` |
| `AppSwitch` | (new) | `@headlessui/vue` Switch |
| `AppDialog` | `Dialog` | `@headlessui/vue` Dialog |
| `AppConfirmDialog` | `ConfirmDialog` | Wraps `AppDialog` |
| `AppDropdown` | `Menu` | `@headlessui/vue` Menu |
| `AppBadge` | `Tag` | Plain `<span>` |
| `AppAvatar` | `Avatar` | Plain `<span>/<img>` |
| `AppTable` | `DataTable` + `Column` | Native `<table>` with slots |
| `AppPagination` | `Paginator` | Plain `<nav>` |
| `AppToast` | `Toast` (global) | `@headlessui/vue` Transition |
| `AppSpinner` | `ProgressSpinner` | SVG animation |
| `AppAlert` | `Message` | Plain `<div>` |
| `AppEmptyState` | (new) | Plain `<div>` |
| `AppCard` | `Card` | Plain `<div>` |
| `AppDivider` | `Divider` | Plain `<hr>` |
| `AppTabs` | `TabView`/`TabPanel`/`Tabs` | `@headlessui/vue` TabGroup |

### Component API Conventions

- All components use `App` prefix to avoid collisions with HTML elements
- Props use TypeScript interfaces defined inline or in component file
- Two-way binding via `v-model` where appropriate (inputs, selects, switches)
- Slots for composability (`default`, `icon`, `header`, `footer`, etc.)
- Emit typed events
- All components support a `class` prop for additional Tailwind overrides via `cn()` helper (`clsx` + `tailwind-merge`)
- A shared `cn()` utility in `src/lib/utils.ts` merges class names and resolves Tailwind conflicts

## Composables

### `useToast()`

Manages a reactive toast notification queue. Provides methods: `success(message)`, `error(message)`, `warning(message)`, `info(message)`. Each toast auto-dismisses after 5 seconds. The `AppToast` component in `App.vue` renders the queue.

```typescript
// Usage in any component:
const toast = useToast()
toast.success('Realm created successfully')
toast.error('Failed to delete user')
```

### `useConfirm()`

Returns a `confirm()` function that opens a dialog and returns a Promise. The `AppConfirmDialog` component in `App.vue` renders the dialog.

```typescript
// Usage in any component:
const { confirm } = useConfirm()
const confirmed = await confirm({
  title: 'Delete User',
  message: 'Are you sure? This cannot be undone.',
  confirmLabel: 'Delete',
  variant: 'danger'
})
if (confirmed) { /* proceed */ }
```

### `useDarkMode()`

Toggles `.dark` class on `<html>` element. Persists preference to `localStorage`. Initializes from stored preference or falls back to system preference.

```typescript
const { isDark, toggle } = useDarkMode()
```

## Layout Shell

### SidebarLayout.vue

The main app shell, using the Tailwind Plus Sidebar Layout pattern:

- **Left sidebar:** Ravencloak logo at top, realm selector (dropdown to switch between realms), navigation links grouped by entity (Users, Clients, Roles, Groups, Identity Providers, Audit). Collapsible on mobile via hamburger button.
- **Top bar:** Breadcrumbs showing current location, dark mode toggle switch, user avatar with dropdown menu (profile info, logout).
- **Content area:** `<router-view>` slot with padding.
- **Mobile:** Sidebar slides in as an overlay, dismissed by clicking outside or pressing Escape.

### Route Integration

Login (`/login`) and access-denied (`/access-denied`) pages bypass the sidebar layout entirely -- they render full-screen. All other routes are wrapped in `SidebarLayout`. This matches the current behavior.

## Styling Architecture

### Tailwind CSS v4 Configuration

No `tailwind.config.js` -- v4 uses CSS-based configuration:

```css
/* app.css */
@import "tailwindcss";

@theme {
  --font-sans: "Inter", sans-serif;
  /* Primary palette — match PrimeVue Aura's indigo-based primary */
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
```

### Dark Mode

Strategy: `selector` (`.dark` class on `<html>`). Tailwind's `dark:` variant applies dark styles:

```html
<div class="bg-white dark:bg-gray-900 text-gray-900 dark:text-white">
```

### No Scoped Styles

Pages and components use Tailwind utility classes exclusively. No `<style scoped>` blocks needed. This eliminates the PrimeVue CSS variable dependency entirely.

### Inter Font

Loaded via CDN `<link>` tag in `index.html`, configured as the default sans font in `@theme`.

## Cleanup

### Files to Delete
- `src/views/RealmListView.vue` -- legacy, unused
- `src/views/RealmDashboardView.vue` -- legacy, unused
- `src/views/CreateRealmView.vue` -- legacy, unused
- `src/views/LoginView.vue` -- legacy, unused
- `src/services/api.ts` -- duplicate older API module

### Dependencies to Remove from package.json
- `primevue`
- `@primevue/themes`
- `primeicons`

## Playwright End-to-End Tests

### Setup
- Install `@playwright/test` as devDependency
- Tests live in `web/e2e/`
- Use Page Object Model pattern for maintainability
- Configure against local dev server (`http://localhost:5173`)

### Test Directory Structure
```
web/e2e/
├── fixtures/
│   └── test-setup.ts           # Base test fixture with auth setup
├── pages/
│   ├── LoginPage.ts            # Page object for login
│   ├── RealmListPage.ts        # Page object for realm list
│   ├── RealmDashboardPage.ts   # Page object for realm dashboard
│   ├── UserListPage.ts         # Page object for user list
│   └── ...                     # Page objects for each major page
├── tests/
│   ├── auth.spec.ts            # Login/logout/access-denied flows
│   ├── realms.spec.ts          # Realm CRUD flows
│   ├── users.spec.ts           # User CRUD flows
│   ├── clients.spec.ts         # Client CRUD flows
│   ├── roles-groups.spec.ts    # Role and group management
│   ├── audit.spec.ts           # Audit trail viewing
│   └── dark-mode.spec.ts       # Dark mode toggle
└── playwright.config.ts        # Playwright configuration
```

### Test Flows
1. **Auth:** Login via Keycloak, verify redirect, verify super admin access, logout, access-denied for non-admin
2. **Realms:** List realms, create realm, view realm dashboard, navigate between tabs
3. **Users:** List users in realm, create user, view user detail, search/filter users
4. **Clients:** List clients, create client, view client detail, view integration snippets
5. **Roles & Groups:** List roles/groups, create role/group
6. **Audit:** View audit timeline, view diff viewer
7. **Dark Mode:** Toggle dark mode, verify persistence across page navigation

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Missing PrimeVue feature with no Tailwind Plus equivalent | `AppTable` supports sort via `sortKey`/`sortDirection` props + `@sort` emit; groups tree is a recursive `GroupTreeItem` component. Column definitions passed as slot-based `<AppTableColumn>` children. |
| Keycloak auth in Playwright tests | Mock auth in test fixtures: intercept Keycloak init and inject a fake token. Real Keycloak e2e tests are out of scope for this migration. |
| Large PR size | Worktree isolation means main is safe; review section by section |
| Headless UI Vue missing components vs React version | Check `@headlessui/vue` API -- it covers Dialog, Menu, Listbox, Combobox, Switch, Tabs, Transition. This covers all our needs. |

## Success Criteria

1. All 20 pages render correctly with Tailwind Plus styling
2. Dark mode toggle works and persists
3. No PrimeVue imports, CSS variables, or dependencies remain
4. All interactive patterns (dropdowns, dialogs, select menus) are accessible via Headless UI
5. Playwright tests cover all major user flows
6. `npm run build` succeeds with zero errors
7. `npm run type-check` passes
