import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

import App from '@/App.vue'
import router from '@/router'
import { setAuthFailureHandler } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import '@/style.css'

const app = createApp(App)
const pinia = createPinia()
const queryClient = new QueryClient()

app.use(pinia)
app.use(router)
app.use(VueQueryPlugin, { queryClient })

const authStore = useAuthStore()
setAuthFailureHandler(() => {
  authStore.clearAuth()
  void router.push('/login')
})

app.mount('#app')
