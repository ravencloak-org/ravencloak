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

  if (requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.name === 'login' && authStore.isAuthenticated) {
    return { name: 'realms' }
  }

  if (requiresAuth && authStore.isAuthenticated && !authStore.isSuperAdmin) {
    await authStore.logout()
    return { name: 'login', query: { error: 'insufficient_permissions' } }
  }

  return true
})

export default router
