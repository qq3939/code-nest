<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { userApi } from '@/api'
import { useUserStore } from '@/stores/user'

const props = withDefaults(defineProps<{ userId: string; followed: boolean; size?: 'small' | 'default' }>(), {
  size: 'default',
})
const emit = defineEmits<{ change: [followed: boolean] }>()

const userStore = useUserStore()
const router = useRouter()
const state = ref(props.followed)
const loading = ref(false)
watch(() => props.followed, (v) => (state.value = v))

async function toggle() {
  if (!userStore.loggedIn) {
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  loading.value = true
  try {
    if (state.value) await userApi.unfollow(props.userId)
    else await userApi.follow(props.userId)
    state.value = !state.value
    emit('change', state.value)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-button
    v-if="userStore.user?.id !== userId"
    :type="state ? 'default' : 'primary'"
    :size="size"
    :loading="loading"
    @click="toggle"
  >
    {{ state ? '已关注' : '关注' }}
  </el-button>
</template>
