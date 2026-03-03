import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/users',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('@/views/UserManageView.vue'),
      meta: { requiresAdmin: true },
    },
    {
      path: '/products',
      name: 'products',
      component: () => import('@/views/ProductManageView.vue'),
      meta: { requiresAdmin: true },
    },
    {
      path: '/orders',
      name: 'orders',
      component: () => import('@/views/OrderManageView.vue'),
      meta: { requiresAdmin: true },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/users',
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  authStore.restore()

  if (to.meta.requiresAdmin) {
    if (!authStore.isAuthenticated || !authStore.isAdmin) {
      return {
        path: '/login',
        query: {
          redirect: to.fullPath,
        },
      }
    }
  }

  if (to.path === '/login' && authStore.isAuthenticated && authStore.isAdmin) {
    return { path: '/users' }
  }

  return true
})

export default router
