<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { articleApi } from '@/api'
import { ApiError } from '@/api/http'
import CommentSection from '@/components/CommentSection.vue'
import FollowButton from '@/components/FollowButton.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useUserStore } from '@/stores/user'
import type { ArticleDetail } from '@/types'
import { formatTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const id = route.params.id as string

const article = ref<ArticleDetail | null>(null)
const notFound = ref(false)
const busy = ref(false)

onMounted(async () => {
  try {
    article.value = await articleApi.detail(id)
    document.title = `${article.value.title} - project-a`
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound.value = true
  }
})

function requireLogin(): boolean {
  if (userStore.loggedIn) return true
  router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

// 先改界面再发请求（乐观更新），失败时回滚
async function toggle(kind: 'like' | 'collect') {
  const a = article.value
  if (!a || busy.value || !requireLogin()) return
  busy.value = true
  const on = kind === 'like' ? a.liked : a.collected
  const apply = (v: boolean) => {
    if (kind === 'like') {
      a.liked = v
      a.counts.like += v ? 1 : -1
    } else {
      a.collected = v
      a.counts.collect += v ? 1 : -1
    }
  }
  apply(!on)
  try {
    if (kind === 'like') await (on ? articleApi.unlike(id) : articleApi.like(id))
    else await (on ? articleApi.uncollect(id) : articleApi.collect(id))
  } catch {
    apply(on)
  } finally {
    busy.value = false
  }
}

async function remove() {
  await ElMessageBox.confirm('删除后不可恢复，确定删除吗？', '删除文章', { type: 'warning' })
  await articleApi.remove(id)
  ElMessage.success('已删除')
  router.replace({ name: 'home' })
}

function share() {
  navigator.clipboard?.writeText(location.href).then(() => ElMessage.success('链接已复制'))
}
</script>

<template>
  <div class="pa-page">
    <el-result v-if="notFound" icon="warning" title="文章不存在" sub-title="可能已被删除或下架">
      <template #extra><el-button type="primary" @click="router.push('/')">回首页</el-button></template>
    </el-result>
    <el-skeleton v-else-if="!article" :rows="10" animated class="pa-card skeleton" />
    <div v-else class="pa-two-col">
      <div class="main">
        <article class="pa-card body">
          <h1>{{ article.title }}</h1>
          <div class="meta">
            <RouterLink :to="`/user/${article.author.id}`" class="author">
              <UserAvatar :name="article.author.nickname" :src="article.author.avatarUrl" :size="28" />
              {{ article.author.nickname }}
            </RouterLink>
            <span class="pa-muted">{{ formatTime(article.publishTime) }}</span>
            <span class="pa-muted">阅读 {{ article.counts.read }}</span>
            <el-tag v-if="article.status !== 'PUBLISHED'" type="warning" size="small">{{ article.status }}</el-tag>
            <template v-if="userStore.user?.id === article.author.id">
              <el-button link type="primary" @click="router.push(`/editor/${article.id}`)">编辑</el-button>
              <el-button link type="danger" @click="remove">删除</el-button>
            </template>
          </div>
          <img v-if="article.coverUrl" :src="article.coverUrl" class="cover" alt="" />
          <MarkdownView :id="`article-${article.id}`" :content="article.content" show-catalog />
          <div class="tags">
            <el-tag v-if="article.category" type="primary" effect="plain">{{ article.category.name }}</el-tag>
            <el-tag v-for="t in article.tags" :key="t.id" effect="plain" round>{{ t.name }}</el-tag>
          </div>
          <div class="actions">
            <el-button :type="article.liked ? 'primary' : 'default'" round @click="toggle('like')">
              <el-icon><Pointer /></el-icon>&nbsp;{{ article.liked ? '已赞' : '点赞' }} {{ article.counts.like }}
            </el-button>
            <el-button :type="article.collected ? 'warning' : 'default'" round @click="toggle('collect')">
              <el-icon><Star /></el-icon>&nbsp;{{ article.collected ? '已收藏' : '收藏' }} {{ article.counts.collect }}
            </el-button>
            <el-button round @click="share"><el-icon><Share /></el-icon>&nbsp;分享</el-button>
          </div>
        </article>
        <div class="pa-card body">
          <CommentSection :article-id="article.id" :author-id="article.author.id" @changed="(d) => article && (article.counts.comment += d)" />
        </div>
      </div>
      <aside class="side">
        <div class="pa-card author-card">
          <RouterLink :to="`/user/${article.author.id}`" class="author-head">
            <UserAvatar :name="article.author.nickname" :src="article.author.avatarUrl" :size="48" />
            <div>
              <div class="name">{{ article.author.nickname }}</div>
              <div class="pa-muted pa-ellipsis">{{ article.author.bio || '这个人很懒，什么都没写' }}</div>
            </div>
          </RouterLink>
          <FollowButton :user-id="article.author.id" :followed="article.authorFollowed" class="follow" />
        </div>
        <div class="pa-card catalog pa-hide-mobile">
          <div class="catalog-title">目录</div>
          <div id="pa-catalog" />
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.skeleton {
  padding: 24px;
}
.main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.body {
  padding: 24px 28px;
}
h1 {
  margin: 0 0 12px;
  font-size: 28px;
  line-height: 1.4;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}
.author {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}
.cover {
  width: 100%;
  max-height: 360px;
  object-fit: cover;
  border-radius: 6px;
  margin-bottom: 12px;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0;
}
.actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--pa-border);
}
.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: 76px;
}
.author-card {
  padding: 16px;
}
.author-head {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
}
.author-head > div {
  min-width: 0;
}
.name {
  font-weight: 600;
  font-size: 15px;
}
.follow {
  width: 100%;
  margin-top: 12px;
}
.catalog {
  padding: 12px 16px;
  max-height: calc(100vh - 260px);
  overflow: auto;
}
.catalog-title {
  font-weight: 600;
  margin-bottom: 8px;
}
</style>
