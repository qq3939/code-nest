<script setup lang="ts">
import type { ArticleListItem } from '@/types'
import { compact, fromNow } from '@/utils/format'

defineProps<{ article: ArticleListItem; showStatus?: boolean }>()

const statusText: Record<string, string> = { DRAFT: '草稿', PENDING: '审核中', PUBLISHED: '已发布', OFFLINE: '已下架' }
</script>

<template>
  <article class="card">
    <div class="meta">
      <RouterLink :to="`/user/${article.author.id}`" class="author">{{ article.author.nickname }}</RouterLink>
      <span class="pa-muted">{{ fromNow(article.publishTime || article.updateTime) }}</span>
      <RouterLink v-if="article.category" :to="{ name: 'home', query: { categoryId: article.category.id } }" class="pa-muted">
        {{ article.category.name }}
      </RouterLink>
      <el-tag v-if="showStatus" size="small" :type="article.status === 'PUBLISHED' ? 'success' : 'info'">
        {{ statusText[article.status] }}
      </el-tag>
    </div>
    <div class="body">
      <div class="text">
        <RouterLink :to="article.status === 'PUBLISHED' ? `/article/${article.id}` : `/editor/${article.id}`">
          <h3 class="title">{{ article.title || '（无标题）' }}</h3>
          <p class="summary">{{ article.summary }}</p>
        </RouterLink>
        <div class="foot">
          <span><el-icon><View /></el-icon>{{ compact(article.counts.read) }}</span>
          <span><el-icon><Pointer /></el-icon>{{ compact(article.counts.like) }}</span>
          <span><el-icon><ChatDotRound /></el-icon>{{ compact(article.counts.comment) }}</span>
          <el-tag v-for="t in article.tags.slice(0, 3)" :key="t.id" size="small" effect="plain" round>{{ t.name }}</el-tag>
        </div>
      </div>
      <img v-if="article.coverUrl" :src="article.coverUrl" class="cover" alt="" loading="lazy" />
    </div>
  </article>
</template>

<style scoped>
.card {
  padding: 14px 20px;
  border-bottom: 1px solid var(--pa-border);
}
.meta {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 13px;
}
.author {
  color: var(--pa-text-2);
}
.body {
  display: flex;
  gap: 16px;
  margin-top: 6px;
}
.text {
  flex: 1;
  min-width: 0;
}
.title {
  margin: 0 0 6px;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.4;
}
.title:hover {
  color: var(--pa-primary);
}
.summary {
  margin: 0 0 8px;
  color: var(--pa-text-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.6;
}
.foot {
  display: flex;
  gap: 14px;
  align-items: center;
  color: var(--pa-text-3);
  font-size: 13px;
}
.foot span {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}
.cover {
  width: 120px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
}
</style>
