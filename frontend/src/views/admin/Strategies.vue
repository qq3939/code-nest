<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { adminApi } from '@/api'
import type { Strategy } from '@/types'

// 每组第一个是默认值；naive / pull / none 只作压测对照组
const options = {
  countStrategy: {
    title: '计数策略',
    items: [
      { value: 'redis-batch', label: 'redis-batch', desc: 'Redis 原子累加 + 定时批量写回（默认）' },
      { value: 'atomic-sql', label: 'atomic-sql', desc: '每次 UPDATE x = x + 1' },
      { value: 'naive', label: 'naive', desc: '先读后写，对照组，会丢更新' },
    ],
  },
  feedMode: {
    title: '关注流模式',
    items: [
      { value: 'hybrid', label: 'hybrid', desc: '普通作者推、大 V 拉（默认）' },
      { value: 'push', label: 'push', desc: '写扩散：发文时推到所有粉丝收件箱' },
      { value: 'pull', label: 'pull', desc: '读扩散：读时合并关注者发件箱' },
    ],
  },
  cacheMode: {
    title: '缓存模式',
    items: [
      { value: 'two-level', label: 'two-level', desc: 'Caffeine + Redis（默认）' },
      { value: 'redis', label: 'redis', desc: '只用 Redis' },
      { value: 'none', label: 'none', desc: '不用缓存，对照组' },
    ],
  },
} as const

const current = ref<Strategy | null>(null)
const disabled = ref(false)
const saving = ref(false)
const reindexing = ref(false)

onMounted(async () => {
  try {
    current.value = await adminApi.strategies()
  } catch {
    disabled.value = true
  }
})

async function change(key: keyof Strategy, value: string) {
  saving.value = true
  try {
    current.value = await adminApi.updateStrategies({ [key]: value })
    ElMessage.success('已切换，已广播到所有实例')
  } finally {
    saving.value = false
  }
}

async function reindex() {
  reindexing.value = true
  try {
    const res = await adminApi.reindex()
    ElMessage.success(`索引重建完成：${JSON.stringify(res)}`)
  } finally {
    reindexing.value = false
  }
}
</script>

<template>
  <div>
    <el-alert v-if="disabled" type="info" :closable="false" show-icon title="实验开关只在 dev 环境开启"
      description="生产环境（prod profile）关闭了这个接口，策略以配置文件为准。" class="mb" />
    <el-alert v-else type="warning" :closable="false" show-icon class="mb"
      title="运行时切换，用于压测对比"
      description="切换通过 Redis 广播到所有实例。切换计数策略前会先把 Redis 里未写回的计数刷进数据库；切换 Feed 模式会清空收件箱，按新模式冷启动重建。" />
    <div v-if="current" v-loading="saving" class="grid">
      <el-card v-for="(group, key) in options" :key="key" shadow="never" :header="group.title">
        <el-radio-group :model-value="current[key]" class="radios" @change="(v: string | number | boolean | undefined) => change(key, String(v))">
          <el-radio v-for="o in group.items" :key="o.value" :value="o.value" class="radio">
            <b>{{ o.label }}</b>
            <span class="pa-muted">{{ o.desc }}</span>
          </el-radio>
        </el-radio-group>
      </el-card>
    </div>
    <el-card shadow="never" header="搜索索引" class="mt">
      <p class="pa-muted">从 MySQL 全量重建 Elasticsearch 索引：先删除旧索引再分批写入，重建期间搜索结果可能不完整。未启用搜索时不做任何事。</p>
      <el-button :loading="reindexing" @click="reindex">全量重建索引</el-button>
    </el-card>
  </div>
</template>

<style scoped>
.mb {
  margin-bottom: 16px;
}
.mt {
  margin-top: 16px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}
.radios {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}
.radio {
  height: auto;
}
.radio :deep(.el-radio__label) {
  display: flex;
  flex-direction: column;
  gap: 2px;
  white-space: normal;
}
</style>
