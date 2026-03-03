<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import AdminHeader from '@/components/AdminHeader.vue'

const route = useRoute()
const showHeader = computed(() => route.path !== '/login')
</script>

<template>
  <div class="min-h-screen">
    <AdminHeader v-if="showHeader" />
    <main class="mx-auto w-full max-w-7xl px-6 py-8">
      <RouterView />
    </main>
  </div>
</template>
