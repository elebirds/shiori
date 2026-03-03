import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { createApp, watch } from 'vue'

import App from '@/App.vue'
import { setAuthFailureHandler } from '@/api/http'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import { useNotifyStore } from '@/stores/notify'
import '@/style.css'

const app = createApp(App)
const pinia = createPinia()
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

app.use(pinia)
app.use(router)
app.use(VueQueryPlugin, { queryClient })

const authStore = useAuthStore(pinia)
const notifyStore = useNotifyStore(pinia)

setAuthFailureHandler(() => {
  authStore.clearAuth()
  notifyStore.disconnect()
  notifyStore.clearMessages()

  const currentRoute = router.currentRoute.value
  if (currentRoute.path !== '/login') {
    void router.push({
      path: '/login',
      query: {
        redirect: currentRoute.fullPath,
      },
    })
  }
})

watch(
  () => authStore.user?.userId,
  (userId) => {
    if (userId) {
      notifyStore.connect(String(userId))
    } else {
      notifyStore.disconnect()
      notifyStore.clearMessages()
    }
  },
  { immediate: true },
)

app.mount('#app')
