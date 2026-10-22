<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { notifyApi } from '@/api'
import UserAvatar from '@/components/UserAvatar.vue'
import { useNotifyStore } from '@/stores/notify'
import type { NotifyType, NotifyVO } from '@/types'
import { fromNow } from '@/utils/format'

const router = useRouter()
const notifyStore = useNotifyStore()

const tabs: { name: NotifyType | ''; label: string }[] = [
  { name: '', label: '全部' },
  { name: 'COMMENT', label: '评论' },
  { name: 'REPLY', label: '回复' },
  { name: 'LIKE', label: '点赞' },
  { name: 'COLLECT', label: '收藏' },
  { name: 'FOLLOW', label: '关注' },
  { name: 'SYSTEM', label: '系统' },
]
const type = ref<NotifyType | ''>('')
const items = ref<NotifyVO[]>([])
const cursor = ref<string | null>(null)
const hasMore = ref(false)
const loading = ref(false)

async function load(reset = false) {
  loading.value = true
  try {
    const res = await notifyApi.list(type.value, reset ? null : cursor.value)
    items.value = reset ? res.items : [...items.value, ...res.items]
    cursor.value = res.nextCursor
    hasMore.value = res.hasMore
  } finally {
    loading.value = false
  }
}

watch(type, () => load(true))
onMounted(() => load(true))

async function open(n: NotifyVO) {
  if (!n.read) {
    await notifyApi.markRead([n.id])
    n.read = true
    notifyStore.refreshCount()
  }
  if (n.targetType === 'USER' && n.sender) router.push(`/user/${n.sender.id}`)
  else if (n.targetType === 'ARTICLE' || n.targetType === 'COMMENT') router.push(`/article/${n.targetId}`)
}

async function readAll() {
  await notifyApi.markRead(null, true)
  items.value.forEach((n) => (n.read = true))
  notifyStore.unread = 0
}
</script>

<template>
  <div class="pa-page">
    <section class="pa-card wrap">
      <div class="top">
        <el-tabs v-model="type" class="tabs">
          <el-tab-pane v-for="t in tabs" :key="t.name" :label="t.label" :name="t.name" />
        </el-tabs>
        <el-button link type="primary" :disabled="notifyStore.unread === 0" @click="readAll">全部标为已读</el-button>
      </div>
      <div v-for="n in items" :key="n.id" class="item" :class="{ unread: !n.read }" @click="open(n)">
        <UserAvatar v-if="n.sender" :name="n.sender.nickname" :src="n.sender.avatarUrl" :size="36" />
        <el-avatar v-else :size="36" icon="Bell" />
        <div class="body">
          <div>
            <b v-if="n.sender">{{ n.sender.nickname }}</b>
            {{ n.content }}
          </div>
          <span class="pa-muted">{{ fromNow(n.createTime) }}</span>
        </div>
        <span v-if="!n.read" class="dot" />
      </div>
      <div class="more">
        <el-button v-if="hasMore" :loading="loading" @click="load()">加载更多</el-button>
        <el-empty v-else-if="!items.length && !loading" description="暂无通知" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.wrap {
  min-height: 400px;
}
.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  border-bottom: 1px solid var(--pa-border);
}
.tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}
.item {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 14px 20px;
  border-bottom: 1px solid var(--pa-border);
  cursor: pointer;
}
.item:hover {
  background: var(--pa-bg);
}
.item.unread {
  background: var(--el-color-primary-light-9);
}
.body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-danger);
}
.more {
  padding: 16px;
  text-align: center;
}
</style>
