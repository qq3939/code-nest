<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchApi } from '@/api'
import type { SearchHit } from '@/types'
import { fromNow, safeHighlight } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const hits = ref<SearchHit[]>([])
const total = ref(0)
const loading = ref(false)
const q = ref('')
const page = ref(1)

async function run() {
  q.value = typeof route.query.q === 'string' ? route.query.q.trim() : ''
  page.value = Number(route.query.page) || 1
  if (!q.value) {
    hits.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const res = await searchApi.search(q.value, page.value, 10)
    hits.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

watch(() => route.query, run, { immediate: true })

function go(p: number) {
  router.push({ name: 'search', query: { q: q.value, page: p } })
}
</script>

<template>
  <div class="pa-page">
    <div v-loading="loading" class="pa-card wrap">
      <div class="summary pa-muted">
        <template v-if="q">"{{ q }}" 共找到 {{ total }} 条结果</template>
        <template v-else>在顶部输入关键词搜索文章</template>
      </div>
      <div v-for="h in hits" :key="h.id" class="hit">
        <!-- 标题和片段里的 <em> 由后端高亮生成，渲染前用 DOMPurify 只保留 <em> -->
        <RouterLink :to="`/article/${h.id}`">
          <h3 class="pa-highlight" v-html="safeHighlight(h.title)" />
        </RouterLink>
        <p class="pa-highlight snippet" v-html="safeHighlight(h.contentSnippet || h.summary)" />
        <div class="meta pa-muted">
          <RouterLink :to="`/user/${h.authorId}`">{{ h.authorName }}</RouterLink>
          <span>{{ fromNow(h.publishTime) }}</span>
          <el-tag v-for="t in h.tags" :key="t" size="small" effect="plain" round>{{ t }}</el-tag>
        </div>
      </div>
      <el-empty v-if="q && !loading && !hits.length" description="没有找到相关文章，换个关键词试试" />
      <el-pagination v-if="total > 10" class="pager" layout="prev, pager, next" :total="total" :page-size="10"
        :current-page="page" @current-change="go" />
    </div>
  </div>
</template>

<style scoped>
.wrap {
  padding: 8px 24px 20px;
  min-height: 300px;
}
.summary {
  padding: 12px 0;
  border-bottom: 1px solid var(--pa-border);
}
.hit {
  padding: 16px 0;
  border-bottom: 1px solid var(--pa-border);
}
h3 {
  margin: 0 0 6px;
  font-size: 17px;
}
h3:hover {
  color: var(--pa-primary);
}
.snippet {
  margin: 0 0 8px;
  color: var(--pa-text-2);
  line-height: 1.6;
}
.meta {
  display: flex;
  gap: 12px;
  align-items: center;
}
.pager {
  justify-content: center;
  margin-top: 16px;
}
</style>
