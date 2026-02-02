import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { routes } from 'vue-router/auto-routes'
import { useAuthStore } from '@/stores/auth'

// Wrap routes in MainLayout
const wrappedRoutes = [
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: routes.filter(r => r.path !== '/login')
  },
  routes.find(r => r.path === '/login') ?? {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: wrappedRoutes
})

router.beforeEach(async (to: RouteLocationNormalized) => {
  const authStore = useAuthStore()

  if (!authStore.initialized) {
    await authStore.init()
  }

  const requiresAuth = to.meta.requiresAuth !== false

  // Not authenticated - redirect to login
  if (requiresAuth && !authStore.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // Already authenticated and going to login - redirect to realms
  if (to.path === '/login' && authStore.isAuthenticated) {
    return { path: '/realms' }
  }

  // Authenticated but missing SUPER_ADMIN role - logout and stop
  if (requiresAuth && authStore.isAuthenticated && !authStore.isSuperAdmin) {
    authStore.logout()
    return false
  }

  return true
})

export default router
