import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { routes } from 'vue-router/auto-routes'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      component: () => import('@/pages/login.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/access-denied',
      component: () => import('@/pages/access-denied.vue'),
      meta: { requiresAuth: false },
    },
    ...routes.filter((r) => r.path !== '/login' && r.path !== '/access-denied'),
    { path: '/:pathMatch(.*)*', redirect: '/login' },
  ],
})

router.beforeEach(async (to: RouteLocationNormalized) => {
  const authStore = useAuthStore()

  if (!authStore.initialized) {
    await authStore.init()
  }

  const requiresAuth = to.meta.requiresAuth !== false

  if (requiresAuth && !authStore.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.path === '/login' && authStore.isAuthenticated) {
    return { path: '/realms' }
  }

  if (requiresAuth && authStore.isAuthenticated && !authStore.isSuperAdmin) {
    return { path: '/access-denied' }
  }

  return true
})

export default router
