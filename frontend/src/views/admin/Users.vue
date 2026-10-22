<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { adminApi } from '@/api'
import type { AdminUser } from '@/types'
import { formatTime } from '@/utils/format'

const query = reactive({ keyword: '', page: 1, size: 20 })
const rows = ref<AdminUser[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await adminApi.users({ keyword: query.keyword || undefined, page: query.page, size: query.size })
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function ban(u: AdminUser) {
  const { value } = await ElMessageBox.prompt(`封禁「${u.nickname}」后其登录状态立即失效，请输入原因`, '封禁用户', {
    inputValidator: (v) => (!!v && v.trim().length > 0) || '请填写原因',
  })
  await adminApi.ban(u.id, value)
  ElMessage.success('已封禁')
  load()
}

async function unban(u: AdminUser) {
  await ElMessageBox.confirm(`确定解封「${u.nickname}」吗？`, '解封')
  await adminApi.unban(u.id)
  ElMessage.success('已解封')
  load()
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div class="filters">
      <el-input v-model="query.keyword" placeholder="用户名或昵称" clearable style="width: 240px"
        @keyup.enter="query.page = 1; load()" @clear="load" />
      <el-button type="primary" @click="query.page = 1; load()">查询</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="id" label="ID" width="190" />
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column label="昵称" min-width="140">
        <template #default="{ row }"><a :href="`/user/${row.id}`" target="_blank">{{ row.nickname }}</a></template>
      </el-table-column>
      <el-table-column label="角色" width="90">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'" size="small">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'BANNED' ? 'danger' : 'success'" size="small">{{ row.status === 'BANNED' ? '已封禁' : '正常' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <template v-if="row.role !== 'ADMIN'">
            <el-button v-if="row.status === 'BANNED'" link type="primary" @click="unban(row)">解封</el-button>
            <el-button v-else link type="danger" @click="ban(row)">封禁</el-button>
          </template>
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
.pager {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
