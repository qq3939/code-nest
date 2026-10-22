<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { adminApi } from '@/api'
import type { AuditLog } from '@/types'
import { formatTime } from '@/utils/format'

const query = reactive({ action: '', page: 1, size: 20 })
const rows = ref<AuditLog[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await adminApi.auditLogs({ action: query.action || undefined, page: query.page, size: query.size })
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div class="filters">
      <el-input v-model="query.action" placeholder="操作类型，如 ARTICLE_APPROVE" clearable style="width: 260px"
        @keyup.enter="query.page = 1; load()" @clear="load" />
      <el-button type="primary" @click="query.page = 1; load()">查询</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="管理员" width="140">
        <template #default="{ row }">{{ row.admin?.nickname }}</template>
      </el-table-column>
      <el-table-column prop="action" label="操作" width="200" />
      <el-table-column label="对象" width="220">
        <template #default="{ row }">{{ row.targetType }} #{{ row.targetId }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" min-width="200" />
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
.pager {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
