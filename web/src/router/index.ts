import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/realms'
        },
        {
          path: 'realms',
          name: 'realms',
          component: () => import('@/views/RealmListView.vue')
        },
        {
          path: 'realms/create',
          name: 'create-realm',
          component: () => import('@/views/CreateRealmView.vue')
        },
        {
          path: 'realms/:name',
          name: 'realm-dashboard',
          component: () => import('@/views/RealmDashboardView.vue'),
          props: true
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/login'
    }
  ]
})

router.beforeEach(async (to: RouteLocationNormalized) => {
  const authStore = useAuthStore()

  if (!authStore.initialized) {
    await authStore.init()
  }

  const requiresAuth = to.meta.requiresAuth !== false

  // Not authenticated - redirect to login
  if (requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // Already authenticated and going to login - redirect to realms
  if (to.name === 'login' && authStore.isAuthenticated) {
    return { name: 'realms' }
  }

  // Authenticated but missing SUPER_ADMIN role - logout and stop
  // Don't return a redirect here since logout() handles the redirect
  if (requiresAuth && authStore.isAuthenticated && !authStore.isSuperAdmin) {
    authStore.logout() // Don't await - let it redirect
    return false // Stop navigation
  }

  return true
})

export default router
