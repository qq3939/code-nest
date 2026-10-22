<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import UserAvatar from '@/components/UserAvatar.vue'
import { useNotifyStore } from '@/stores/notify'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const notifyStore = useNotifyStore()
const router = useRouter()
const route = useRoute()

const keyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
watch(() => route.query.q, (q) => (keyword.value = typeof q === 'string' ? q : ''))

function search() {
  const q = keyword.value.trim()
  if (q) router.push({ name: 'search', query: { q } })
}

async function onCommand(cmd: string) {
  if (cmd === 'logout') {
    await userStore.logout()
    router.push({ name: 'home' })
  } else if (cmd === 'profile') {
    router.push(`/user/${userStore.user?.id}`)
  } else {
    router.push({ name: cmd })
  }
}
</script>

<template>
  <div class="layout">
    <header class="header">
      <div class="pa-container bar">
        <RouterLink to="/" class="logo">code-nest</RouterLink>
        <nav class="nav pa-hide-mobile">
          <RouterLink to="/" exact-active-class="active">首页</RouterLink>
          <RouterLink to="/rank" active-class="active">排行榜</RouterLink>
        </nav>
        <el-input v-model="keyword" class="search" placeholder="搜索文章" clearable @keyup.enter="search">
          <template #suffix>
            <el-icon class="search-icon" @click="search"><Search /></el-icon>
          </template>
        </el-input>
        <div class="right">
          <template v-if="userStore.loggedIn">
            <el-button type="primary" @click="router.push({ name: 'editor-new' })">
              <el-icon><EditPen /></el-icon><span class="pa-hide-mobile">&nbsp;写文章</span>
            </el-button>
            <RouterLink to="/notifications" class="bell">
              <el-badge :value="notifyStore.unread" :hidden="notifyStore.unread === 0" :max="99">
                <el-icon :size="20"><Bell /></el-icon>
              </el-badge>
            </RouterLink>
            <el-dropdown trigger="click" @command="onCommand">
              <span class="me">
                <UserAvatar :name="userStore.user?.nickname" :src="userStore.user?.avatarUrl" />
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">我的主页</el-dropdown-item>
                  <el-dropdown-item command="settings">设置</el-dropdown-item>
                  <el-dropdown-item v-if="userStore.isAdmin" command="admin-dashboard">管理后台</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button @click="router.push({ name: 'login', query: { redirect: route.fullPath } })">登录</el-button>
            <el-button type="primary" @click="router.push({ name: 'register' })">注册</el-button>
          </template>
        </div>
      </div>
    </header>
    <main class="pa-container">
      <RouterView :key="route.path" />
    </main>
  </div>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  border-bottom: 1px solid var(--pa-border);
}
.bar {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 24px;
}
.logo {
  font-size: 20px;
  font-weight: 700;
  color: var(--pa-primary);
}
.nav {
  display: flex;
  gap: 20px;
  color: var(--pa-text-2);
}
.nav a.active,
.nav a:hover {
  color: var(--pa-primary);
}
.search {
  max-width: 280px;
}
.search-icon {
  cursor: pointer;
}
.right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 16px;
}
.bell {
  display: flex;
  color: var(--pa-text-2);
}
.me {
  cursor: pointer;
  display: flex;
}
@media (max-width: 600px) {
  .bar {
    gap: 10px;
  }
  .right {
    gap: 10px;
  }
}
</style>
