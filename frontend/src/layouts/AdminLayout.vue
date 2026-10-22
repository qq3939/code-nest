<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const menus = [
  { name: 'admin-dashboard', title: '数据看板', icon: 'DataLine' },
  { name: 'admin-review', title: '内容审核', icon: 'Stamp' },
  { name: 'admin-articles', title: '文章管理', icon: 'Document' },
  { name: 'admin-users', title: '用户管理', icon: 'User' },
  { name: 'admin-taxonomy', title: '分类标签', icon: 'CollectionTag' },
  { name: 'admin-words', title: '敏感词', icon: 'Warning' },
  { name: 'admin-dead-letters', title: '死信消息', icon: 'Message' },
  { name: 'admin-audit', title: '操作日志', icon: 'Tickets' },
  { name: 'admin-strategies', title: '实验开关', icon: 'Switch' },
]
</script>

<template>
  <el-container class="admin">
    <el-aside width="200px" class="aside">
      <div class="brand">project-a 管理端</div>
      <el-menu :default-active="String(route.name)" @select="(n: string) => router.push({ name: n })">
        <el-menu-item v-for="m in menus" :key="m.name" :index="m.name">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="top">
        <span class="title">{{ route.meta.title }}</span>
        <div class="actions">
          <RouterLink to="/">返回前台</RouterLink>
          <span class="pa-muted">{{ userStore.user?.nickname }}</span>
        </div>
      </el-header>
      <el-main>
        <RouterView :key="route.fullPath" />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin {
  min-height: 100vh;
}
.aside {
  background: #fff;
  border-right: 1px solid var(--pa-border);
}
.brand {
  height: 60px;
  line-height: 60px;
  padding-left: 20px;
  font-weight: 700;
  color: var(--pa-primary);
}
.aside :deep(.el-menu) {
  border-right: none;
}
.top {
  background: #fff;
  border-bottom: 1px solid var(--pa-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.actions {
  display: flex;
  gap: 16px;
  align-items: center;
}
</style>
