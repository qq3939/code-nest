<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { rankApi } from '@/api'
import type { ActiveRank, HotArticleRank } from '@/types'
import UserAvatar from './UserAvatar.vue'

const active = ref<ActiveRank[]>([])
const hot = ref<HotArticleRank[]>([])

onMounted(async () => {
  const [a, h] = await Promise.allSettled([rankApi.active('week', 8), rankApi.hotArticles(8)])
  if (a.status === 'fulfilled') active.value = a.value
  if (h.status === 'fulfilled') hot.value = h.value
})
</script>

<template>
  <aside class="side">
    <section class="pa-card block">
      <header>
        <span>活跃作者 · 本周</span>
        <RouterLink to="/rank" class="pa-muted">完整榜单</RouterLink>
      </header>
      <el-empty v-if="!active.length" :image-size="60" description="暂无数据" />
      <RouterLink v-for="r in active" :key="r.user.id" :to="`/user/${r.user.id}`" class="row">
        <span class="no" :class="{ top: r.rank <= 3 }">{{ r.rank }}</span>
        <UserAvatar :name="r.user.nickname" :src="r.user.avatarUrl" :size="24" />
        <span class="pa-ellipsis name">{{ r.user.nickname }}</span>
        <span class="pa-muted">{{ Math.round(r.score) }}</span>
      </RouterLink>
    </section>
    <section class="pa-card block">
      <header>
        <span>热门文章</span>
      </header>
      <el-empty v-if="!hot.length" :image-size="60" description="暂无数据" />
      <RouterLink v-for="r in hot" :key="r.article.id" :to="`/article/${r.article.id}`" class="row">
        <span class="no" :class="{ top: r.rank <= 3 }">{{ r.rank }}</span>
        <span class="pa-ellipsis name">{{ r.article.title }}</span>
      </RouterLink>
    </section>
  </aside>
</template>

<style scoped>
.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: 76px;
}
.block {
  padding: 12px 16px;
}
header {
  display: flex;
  justify-content: space-between;
  font-weight: 600;
  margin-bottom: 8px;
}
.row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
}
.row:hover .name {
  color: var(--pa-primary);
}
.name {
  flex: 1;
  min-width: 0;
}
.no {
  width: 18px;
  text-align: center;
  color: var(--pa-text-3);
  font-weight: 600;
}
.no.top {
  color: var(--el-color-danger);
}
</style>
