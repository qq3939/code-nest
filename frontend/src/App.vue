<script setup lang="ts">
import { watch } from 'vue'
import { useNotifyStore } from '@/stores/notify'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const notifyStore = useNotifyStore()

// 登录状态变化时建立或断开 SSE 连接
watch(
  () => userStore.loggedIn,
  (loggedIn) => {
    if (loggedIn) {
      notifyStore.refreshCount()
      notifyStore.connect()
    } else {
      notifyStore.disconnect()
      notifyStore.unread = 0
    }
  },
  { immediate: true },
)
</script>

<template>
  <RouterView />
</template>
