<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { adminApi } from '@/api'
import type { ArticleListItem } from '@/types'
import { formatTime } from '@/utils/format'

// 内容审核和文章管理共用这个页面：审核页固定只看 PENDING
const props = defineProps<{ review: boolean }>()

const query = reactive({ status: props.review ? 'PENDING' : '', keyword: '', page: 1, size: 20 })
const rows = ref<ArticleListItem[]>([])
const total = ref(0)
const loading = ref(false)

const statusText: Record<string, string> = { DRAFT: '草稿', PENDING: '待审核', PUBLISHED: '已发布', OFFLINE: '已下架' }
const statusType: Record<string, 'info' | 'warning' | 'success' | 'danger'> = {
  DRAFT: 'info',
  PENDING: 'warning',
  PUBLISHED: 'success',
  OFFLINE: 'danger',
}

async function load() {
  loading.value = true
  try {
    const res = await adminApi.articles({
      status: query.status || undefined,
      keyword: query.keyword || undefined,
      page: query.page,
      size: query.size,
    })
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const actionText = { approve: '通过', reject: '驳回', offline: '下架', restore: '恢复' } as const

async function act(row: ArticleListItem, action: keyof typeof actionText) {
  let reason = ''
  if (action === 'reject' || action === 'offline') {
    const { value } = await ElMessageBox.prompt(`请输入${actionText[action]}原因（会通知作者）`, actionText[action], {
      inputValidator: (v) => (!!v && v.trim().length > 0) || '请填写原因',
    })
    reason = value
  } else {
    await ElMessageBox.confirm(`确定${actionText[action]}《${row.title}》吗？`, '确认')
  }
  await adminApi.articleAction(row.id, action, reason)
  ElMessage.success('操作成功')
  load()
}

function search() {
  query.page = 1
  load()
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div class="filters">
      <el-select v-if="!review" v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="search">
        <el-option v-for="(t, k) in statusText" :key="k" :label="t" :value="k" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="标题关键词" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
      <el-button type="primary" @click="search">查询</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column label="标题" min-width="240">
        <template #default="{ row }">
          <a :href="`/article/${row.id}`" target="_blank" class="title">{{ row.title || '（无标题）' }}</a>
        </template>
      </el-table-column>
      <el-table-column label="作者" width="140">
        <template #default="{ row }">{{ row.author.nickname }}</template>
      </el-table-column>
      <el-table-column label="分类" width="100">
        <template #default="{ row }">{{ row.category?.name }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType[row.status]" size="small">{{ statusText[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="160">
        <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button link type="success" @click="act(row, 'approve')">通过</el-button>
            <el-button link type="danger" @click="act(row, 'reject')">驳回</el-button>
          </template>
          <el-button v-if="row.status === 'PUBLISHED'" link type="danger" @click="act(row, 'offline')">下架</el-button>
          <el-button v-if="row.status === 'OFFLINE'" link type="primary" @click="act(row, 'restore')">恢复</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" class="pager" layout="total, prev, pager, next" :total="total"
      :page-size="query.size" @current-change="load" />
  </el-card>
</template>

<style scoped>
.filters {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.title {
  color: var(--pa-primary);
}
.pager {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
