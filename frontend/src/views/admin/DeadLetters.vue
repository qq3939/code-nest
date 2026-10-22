<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { adminApi } from '@/api'
import type { DeadLetter } from '@/types'
import { formatTime } from '@/utils/format'

const query = reactive({ status: 'NEW', page: 1, size: 20 })
const rows = ref<DeadLetter[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await adminApi.deadLetters({ status: query.status || undefined, page: query.page, size: query.size })
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function replay(d: DeadLetter) {
  await ElMessageBox.confirm('重放会把消息重新投递到原队列，消费者是幂等的，重复投递不会产生重复数据。', '重放消息')
  await adminApi.replay(d.id)
  ElMessage.success('已重新投递')
  load()
}

async function ignore(d: DeadLetter) {
  await ElMessageBox.confirm('忽略后这条消息不再处理，确定吗？', '忽略', { type: 'warning' })
  await adminApi.ignore(d.id)
  load()
}

function pretty(json: string) {
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div class="filters">
      <el-radio-group v-model="query.status" @change="query.page = 1; load()">
        <el-radio-button value="NEW">待处理</el-radio-button>
        <el-radio-button value="REPLAYED">已重放</el-radio-button>
        <el-radio-button value="IGNORED">已忽略</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>
      <span class="pa-muted">消费失败的消息经过 1 秒、5 秒、30 秒三级重试仍失败，会进入死信队列并记录在这里。</span>
    </div>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="detail">
            <div><b>msgId：</b>{{ row.msgId }}</div>
            <div><b>错误：</b><span class="err">{{ row.error }}</span></div>
            <pre>{{ pretty(row.payload) }}</pre>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="queue" label="队列" width="160" />
      <el-table-column label="错误" min-width="260">
        <template #default="{ row }"><span class="pa-ellipsis err-line">{{ row.error }}</span></template>
      </el-table-column>
      <el-table-column label="失败时间" width="160">
        <template #default="{ row }">{{ formatTime(row.failedTime) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'NEW'">
            <el-button link type="primary" @click="replay(row)">重放</el-button>
            <el-button link @click="ignore(row)">忽略</el-button>
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
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.detail {
  padding: 0 24px;
}
.detail pre {
  background: var(--pa-bg);
  padding: 12px;
  border-radius: 6px;
  overflow: auto;
}
.err {
  color: var(--el-color-danger);
}
.err-line {
  display: block;
}
.pager {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
