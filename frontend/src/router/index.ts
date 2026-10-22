import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

declare module 'vue-router' {
  interface RouteMeta {
    auth?: boolean
    admin?: boolean
    title?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/views/Home.vue') },
      { path: 'article/:id', name: 'article', component: () => import('@/views/ArticleDetail.vue') },
      { path: 'editor', name: 'editor-new', component: () => import('@/views/Editor.vue'), meta: { auth: true, title: '写文章' } },
      { path: 'editor/:id', name: 'editor', component: () => import('@/views/Editor.vue'), meta: { auth: true, title: '编辑文章' } },
      { path: 'search', name: 'search', component: () => import('@/views/Search.vue'), meta: { title: '搜索' } },
      { path: 'user/:id', name: 'user', component: () => import('@/views/UserProfile.vue') },
      { path: 'notifications', name: 'notifications', component: () => import('@/views/Notifications.vue'), meta: { auth: true, title: '通知' } },
      { path: 'rank', name: 'rank', component: () => import('@/views/Rank.vue'), meta: { title: '排行榜' } },
      { path: 'settings', name: 'settings', component: () => import('@/views/Settings.vue'), meta: { auth: true, title: '设置' } },
      { path: 'login', name: 'login', component: () => import('@/views/Login.vue'), meta: { title: '登录' } },
      { path: 'register', name: 'register', component: () => import('@/views/Register.vue'), meta: { title: '注册' } },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { auth: true, admin: true },
    children: [
      { path: '', name: 'admin-dashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '数据看板' } },
      { path: 'review', name: 'admin-review', component: () => import('@/views/admin/Articles.vue'), props: { review: true }, meta: { title: '内容审核' } },
      { path: 'articles', name: 'admin-articles', component: () => import('@/views/admin/Articles.vue'), props: { review: false }, meta: { title: '文章管理' } },
      { path: 'users', name: 'admin-users', component: () => import('@/views/admin/Users.vue'), meta: { title: '用户管理' } },
      { path: 'taxonomy', name: 'admin-taxonomy', component: () => import('@/views/admin/Taxonomy.vue'), meta: { title: '分类标签' } },
      { path: 'sensitive-words', name: 'admin-words', component: () => import('@/views/admin/SensitiveWords.vue'), meta: { title: '敏感词' } },
      { path: 'dead-letters', name: 'admin-dead-letters', component: () => import('@/views/admin/DeadLetters.vue'), meta: { title: '死信消息' } },
      { path: 'audit-logs', name: 'admin-audit', component: () => import('@/views/admin/AuditLogs.vue'), meta: { title: '操作日志' } },
      { path: 'strategies', name: 'admin-strategies', component: () => import('@/views/admin/Strategies.vue'), meta: { title: '实验开关' } },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFound.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: (_to, _from, saved) => saved ?? { top: 0 },
})

router.beforeEach((to) => {
  const store = useUserStore()
  if (to.meta.auth && !store.loggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // 前端拦截只是体验优化，真正的权限校验在后端拦截器（/api/admin/** 要求 ADMIN）
  if (to.meta.admin && !store.isAdmin) {
    return { name: 'home' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - code-nest` : 'code-nest 技术社区'
})

export default router
