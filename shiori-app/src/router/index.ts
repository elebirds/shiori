import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/products',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/products',
      name: 'products',
      component: () => import('@/views/ProductListView.vue'),
      meta: { public: true },
    },
    {
      path: '/products/:id',
      name: 'product-detail',
      component: () => import('@/views/ProductDetailView.vue'),
      meta: { public: true },
    },
    {
      path: '/orders',
      name: 'orders',
      component: () => import('@/views/OrderListView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/cart',
      name: 'cart',
      component: () => import('@/views/CartView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/seller/orders',
      name: 'seller-orders',
      component: () => import('@/views/SellerOrderListView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/seller/orders/:orderNo',
      name: 'seller-order-detail',
      component: () => import('@/views/SellerOrderDetailView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/orders/:orderNo',
      name: 'order-detail',
      component: () => import('@/views/OrderDetailView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/checkout/:orderNo',
      name: 'checkout',
      component: () => import('@/views/CheckoutView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/wallet',
      name: 'wallet',
      component: () => import('@/views/WalletView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/sell',
      name: 'sell',
      component: () => import('@/views/SellProductView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/my-products',
      name: 'my-products',
      component: () => import('@/views/MyProductListView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/my-products/:id/edit',
      name: 'my-product-edit',
      component: () => import('@/views/MyProductEditView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/u/:userNo',
      name: 'user-center',
      component: () => import('@/views/UserCenterView.vue'),
      meta: { public: true },
    },
    {
      path: '/profile',
      name: 'profile',
      redirect: () => {
        const authStore = useAuthStore()
        const userNo = authStore.user?.userNo?.trim()
        if (!userNo) {
          return '/products'
        }
        return `/u/${encodeURIComponent(userNo)}`
      },
      meta: { requiresAuth: true },
    },
    {
      path: '/profile/edit',
      name: 'profile-edit',
      component: () => import('@/views/ProfileView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/account/security',
      name: 'account-security',
      component: () => import('@/views/AccountSecurityView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: () => import('@/views/NotificationCenterView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/ChatCenterView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/products',
    },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  const loggedIn = authStore.isAuthenticated
  const mustChangePassword = Boolean(authStore.user?.mustChangePassword)

  if (to.meta.requiresAuth && !loggedIn) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  if (loggedIn && mustChangePassword && to.path !== '/account/security') {
    return {
      path: '/account/security',
      query: {
        forceChangePassword: '1',
      },
    }
  }

  if ((to.path === '/login' || to.path === '/register') && loggedIn) {
    return { path: mustChangePassword ? '/account/security' : '/products' }
  }

  return true
})

export default router
