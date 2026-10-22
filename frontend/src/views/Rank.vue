<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { rankApi } from '@/api'
import UserAvatar from '@/components/UserAvatar.vue'
import type { ActiveRank, HotArticleRank } from '@/types'

const period = ref<'day' | 'week' | 'month'>('week')
const active = ref<ActiveRank[]>([])
const hot = ref<HotArticleRank[]>([])
const loadingActive = ref(false)

async function loadActive() {
  loadingActive.value = true
  try {
    active.value = await rankApi.active(period.value, 50)
  } finally {
    loadingActive.value = false
  }
}

watch(period, loadActive)
onMounted(async () => {
  loadActive()
  hot.value = await rankApi.hotArticles(50).catch(() => [])
})
</script>

<template>
  <div class="pa-page grid">
    <section v-loading="loadingActive" class="pa-card block">
      <header>
        <h3>活跃作者榜</h3>
        <el-radio-group v-model="period" size="small">
          <el-radio-button value="day">日榜</el-radio-button>
          <el-radio-button value="week">周榜</el-radio-button>
          <el-radio-button value="month">月榜</el-radio-button>
        </el-radio-group>
      </header>
      <p class="pa-muted tip">发文、被点赞、被收藏、被评论都会加分；同一条消息只计一次分。</p>
      <RouterLink v-for="r in active" :key="r.user.id" :to="`/user/${r.user.id}`" class="row">
        <span class="no" :class="{ top: r.rank <= 3 }">{{ r.rank }}</span>
        <UserAvatar :name="r.user.nickname" :src="r.user.avatarUrl" :size="32" />
        <span class="pa-ellipsis name">{{ r.user.nickname }}</span>
        <span class="score">{{ Math.round(r.score) }} 分</span>
      </RouterLink>
      <el-empty v-if="!active.length && !loadingActive" description="暂无数据" />
    </section>
    <section class="pa-card block">
      <header>
        <h3>热门文章</h3>
      </header>
      <p class="pa-muted tip">热度综合阅读、点赞、收藏、评论，并随发布时间衰减，每 10 分钟刷新。</p>
      <RouterLink v-for="r in hot" :key="r.article.id" :to="`/article/${r.article.id}`" class="row">
        <span class="no" :class="{ top: r.rank <= 3 }">{{ r.rank }}</span>
        <span class="pa-ellipsis name">{{ r.article.title }}</span>
        <span class="pa-muted">{{ r.article.author.nickname }}</span>
      </RouterLink>
      <el-empty v-if="!hot.length" description="暂无数据" />
    </section>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
  align-items: start;
}
.block {
  padding: 16px 20px;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
h3 {
  margin: 0;
}
.tip {
  margin: 8px 0 12px;
}
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--pa-border);
}
.row:hover .name {
  color: var(--pa-primary);
}
.name {
  flex: 1;
  min-width: 0;
}
.no {
  width: 24px;
  text-align: center;
  font-weight: 700;
  color: var(--pa-text-3);
}
.no.top {
  color: var(--el-color-danger);
}
.score {
  color: var(--el-color-warning);
}
</style>
