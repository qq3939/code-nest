<script setup lang="ts">
import { initial } from '@/utils/format'

withDefaults(defineProps<{ name?: string | null; src?: string | null; size?: number }>(), { size: 32 })
</script>

<template>
  <el-avatar :size="size" :src="src || undefined" class="pa-avatar">{{ initial(name) }}</el-avatar>
</template>

<style scoped>
.pa-avatar {
  flex-shrink: 0;
  background: var(--el-color-primary-light-5);
}
</style>
