<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { articleApi, feedApi, metaApi } from '@/api'
import ArticleCard from '@/components/ArticleCard.vue'
import RankSidebar from '@/components/RankSidebar.vue'
import { useUserStore } from '@/stores/user'
import type { ArticleListItem, Category } from '@/types'

type Tab = 'latest' | 'hot' | 'follow'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const categories = ref<Category[]>([])
const tab = ref<Tab>((route.query.tab as Tab) || 'latest')
const categoryId = ref<string | undefined>(route.query.categoryId as string | undefined)
const items = ref<ArticleListItem[]>([])
const loading = ref(false)
const finished = ref(false)
// 最新、热门用页码；关注流用游标（SPEC 第 10 节）
let page = 1
let cursor: string | null = null
// 切换标签时旧请求可能晚到，用序号丢弃过期结果
let seq = 0

const disabled = computed(() => loading.value || finished.value)

function reset() {
  items.value = []
  finished.value = false
  page = 1
  cursor = null
  seq++
  load()
}

async function load() {
  if (tab.value === 'follow' && !userStore.loggedIn) {
    finished.value = true
    return
  }
  loading.value = true
  const mySeq = seq
  try {
    if (tab.value === 'follow') {
      const res = await feedApi.read(cursor, 10)
      if (mySeq !== seq) return
      items.value.push(...res.items)
      cursor = res.nextCursor
      finished.value = !res.hasMore
    } else {
      const res = await articleApi.list({ sort: tab.value, categoryId: categoryId.value, page, size: 10 })
      if (mySeq !== seq) return
      items.value.push(...res.records)
      page++
      finished.value = items.value.length >= res.total || res.records.length === 0
    }
  } catch {
    finished.value = true
  } finally {
    if (mySeq === seq) loading.value = false
  }
}

watch([tab, categoryId], () => {
  router.replace({ query: { ...(tab.value !== 'latest' && { tab: tab.value }), ...(categoryId.value && { categoryId: categoryId.value }) } })
  reset()
})

onMounted(async () => {
  load()
  categories.value = await metaApi.categories().catch(() => [])
})
</script>

<template>
  <div class="pa-page pa-two-col">
    <section class="pa-card main">
      <div class="tabs">
        <el-tabs v-model="tab">
          <el-tab-pane label="最新" name="latest" />
          <el-tab-pane label="热门" name="hot" />
          <el-tab-pane label="关注" name="follow" />
        </el-tabs>
        <div v-if="tab !== 'follow'" class="cats">
          <el-check-tag :checked="!categoryId" @change="categoryId = undefined">全部</el-check-tag>
          <el-check-tag v-for="c in categories" :key="c.id" :checked="categoryId === c.id" @change="categoryId = c.id">
            {{ c.name }}
          </el-check-tag>
        </div>
      </div>
      <div v-infinite-scroll="load" :infinite-scroll-disabled="disabled" :infinite-scroll-distance="200">
        <ArticleCard v-for="a in items" :key="a.id" :article="a" />
      </div>
      <div class="status">
        <el-skeleton v-if="loading && !items.length" :rows="5" animated />
        <span v-else-if="loading" class="pa-muted">加载中…</span>
        <el-empty v-else-if="!items.length && tab === 'follow' && !userStore.loggedIn" description="登录后查看关注的作者动态">
          <el-button type="primary" @click="router.push({ name: 'login', query: { redirect: '/?tab=follow' } })">去登录</el-button>
        </el-empty>
        <el-empty v-else-if="!items.length" :description="tab === 'follow' ? '还没有关注的作者发文，去发现一些作者吧' : '暂无文章'" />
        <span v-else-if="finished" class="pa-muted">没有更多了</span>
      </div>
    </section>
    <RankSidebar class="pa-hide-mobile" />
  </div>
</template>

<style scoped>
.main {
  min-height: 400px;
}
.tabs {
  padding: 0 20px;
  border-bottom: 1px solid var(--pa-border);
}
.tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}
.cats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 0 12px;
}
.status {
  padding: 20px;
  text-align: center;
}
</style>
