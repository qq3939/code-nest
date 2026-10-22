<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { articleApi, commentApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type { CommentVO } from '@/types'
import { fromNow } from '@/utils/format'
import UserAvatar from './UserAvatar.vue'

const props = defineProps<{ articleId: string; authorId: string }>()
const emit = defineEmits<{ changed: [delta: number] }>()

const userStore = useUserStore()
const router = useRouter()

const list = ref<CommentVO[]>([])
const cursor = ref<string | null>(null)
const hasMore = ref(false)
const loading = ref(false)
const draft = ref('')
const posting = ref(false)
// 当前正在回复的评论（顶级或二级）
const replyTo = ref<CommentVO | null>(null)
const replyText = ref('')
// 每条顶级评论的"加载更多回复"游标
const replyCursor = ref<Record<string, string | null>>({})

async function loadTop(reset = false) {
  loading.value = true
  try {
    const res = await articleApi.comments(props.articleId, { cursor: reset ? null : cursor.value, size: 10 })
    list.value = reset ? res.items : [...list.value, ...res.items]
    cursor.value = res.nextCursor
    hasMore.value = res.hasMore
  } finally {
    loading.value = false
  }
}

async function loadReplies(top: CommentVO) {
  const res = await articleApi.comments(props.articleId, {
    parentId: top.id,
    cursor: replyCursor.value[top.id] ?? null,
    size: 10,
  })
  // 第一次展开时，顶级评论里已经带了前几条回复，用 ID 去重
  const seen = new Set(top.replies.map((r) => r.id))
  top.replies.push(...res.items.filter((r) => !seen.has(r.id)))
  replyCursor.value[top.id] = res.nextCursor
  if (!res.hasMore) top.replyCount = top.replies.length
}

function requireLogin(): boolean {
  if (userStore.loggedIn) return true
  router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  return false
}

async function post() {
  if (!requireLogin() || !draft.value.trim()) return
  posting.value = true
  try {
    const c = await articleApi.comment(props.articleId, draft.value.trim())
    list.value.unshift({ ...c, replies: c.replies ?? [] })
    draft.value = ''
    emit('changed', 1)
  } finally {
    posting.value = false
  }
}

function startReply(c: CommentVO) {
  if (!requireLogin()) return
  replyTo.value = replyTo.value?.id === c.id ? null : c
  replyText.value = ''
}

async function postReply(top: CommentVO) {
  if (!replyTo.value || !replyText.value.trim()) return
  posting.value = true
  try {
    const c = await articleApi.comment(props.articleId, replyText.value.trim(), replyTo.value.id)
    top.replies.push(c)
    top.replyCount++
    replyTo.value = null
    emit('changed', 1)
  } finally {
    posting.value = false
  }
}

async function toggleLike(c: CommentVO) {
  if (!requireLogin()) return
  if (c.liked) await commentApi.unlike(c.id)
  else await commentApi.like(c.id)
  c.liked = !c.liked
  c.likeCount += c.liked ? 1 : -1
}

async function remove(c: CommentVO, top?: CommentVO) {
  await ElMessageBox.confirm('确定删除这条评论吗？', '提示', { type: 'warning' })
  await commentApi.remove(c.id)
  if (top) {
    top.replies = top.replies.filter((r) => r.id !== c.id)
    top.replyCount--
    emit('changed', -1)
  } else {
    // 后端删除顶级评论时会连同回复一起删掉，计数也一起减
    list.value = list.value.filter((x) => x.id !== c.id)
    emit('changed', -(1 + c.replyCount))
  }
  ElMessage.success('已删除')
}

// 与后端一致：只能删自己的评论，管理员可以删任何评论
const canDelete = (c: CommentVO) => !c.deleted && (c.user.id === userStore.user?.id || userStore.isAdmin)

onMounted(() => loadTop(true))
</script>

<template>
  <section class="comments">
    <h3>评论</h3>
    <div class="editor">
      <el-input v-model="draft" type="textarea" :rows="3" maxlength="1000" show-word-limit
        :placeholder="userStore.loggedIn ? '说点什么…' : '登录后发表评论'" :disabled="!userStore.loggedIn" />
      <div class="editor-actions">
        <el-button v-if="userStore.loggedIn" type="primary" :loading="posting" :disabled="!draft.trim()" @click="post">发表</el-button>
        <el-button v-else @click="requireLogin">登录</el-button>
      </div>
    </div>

    <div v-for="c in list" :key="c.id" class="item">
      <UserAvatar :name="c.user.nickname" :src="c.user.avatarUrl" :size="36" />
      <div class="content">
        <div class="head">
          <RouterLink :to="`/user/${c.user.id}`" class="name">{{ c.user.nickname }}</RouterLink>
          <el-tag v-if="c.user.id === authorId" size="small">作者</el-tag>
          <span class="pa-muted">{{ fromNow(c.createTime) }}</span>
        </div>
        <p :class="{ deleted: c.deleted }">{{ c.content }}</p>
        <div v-if="!c.deleted" class="ops">
          <span :class="{ on: c.liked }" @click="toggleLike(c)"><el-icon><Pointer /></el-icon>{{ c.likeCount || '赞' }}</span>
          <span @click="startReply(c)"><el-icon><ChatLineSquare /></el-icon>回复</span>
          <span v-if="canDelete(c)" @click="remove(c)">删除</span>
        </div>
        <div v-if="replyTo && replyTo.id === c.id" class="reply-box">
          <el-input v-model="replyText" size="small" :placeholder="`回复 ${c.user.nickname}`" maxlength="1000" @keyup.enter="postReply(c)" />
          <el-button size="small" type="primary" :loading="posting" @click="postReply(c)">回复</el-button>
        </div>

        <div v-if="c.replies.length" class="replies">
          <div v-for="r in c.replies" :key="r.id" class="item sub">
            <UserAvatar :name="r.user.nickname" :src="r.user.avatarUrl" :size="28" />
            <div class="content">
              <div class="head">
                <RouterLink :to="`/user/${r.user.id}`" class="name">{{ r.user.nickname }}</RouterLink>
                <template v-if="r.replyToUser">
                  <span class="pa-muted">回复</span>
                  <RouterLink :to="`/user/${r.replyToUser.id}`" class="name">{{ r.replyToUser.nickname }}</RouterLink>
                </template>
                <span class="pa-muted">{{ fromNow(r.createTime) }}</span>
              </div>
              <p>{{ r.content }}</p>
              <div class="ops">
                <span :class="{ on: r.liked }" @click="toggleLike(r)"><el-icon><Pointer /></el-icon>{{ r.likeCount || '赞' }}</span>
                <span @click="startReply(r)"><el-icon><ChatLineSquare /></el-icon>回复</span>
                <span v-if="canDelete(r)" @click="remove(r, c)">删除</span>
              </div>
              <div v-if="replyTo && replyTo.id === r.id" class="reply-box">
                <el-input v-model="replyText" size="small" :placeholder="`回复 ${r.user.nickname}`" maxlength="1000" @keyup.enter="postReply(c)" />
                <el-button size="small" type="primary" :loading="posting" @click="postReply(c)">回复</el-button>
              </div>
            </div>
          </div>
          <el-button v-if="c.replyCount > c.replies.length" link type="primary" @click="loadReplies(c)">
            查看更多回复（共 {{ c.replyCount }} 条）
          </el-button>
        </div>
      </div>
    </div>

    <div class="more">
      <el-button v-if="hasMore" :loading="loading" @click="loadTop()">加载更多评论</el-button>
      <span v-else-if="list.length" class="pa-muted">没有更多评论了</span>
      <el-empty v-else-if="!loading" :image-size="60" description="还没有评论，来抢沙发" />
    </div>
  </section>
</template>

<style scoped>
.comments h3 {
  margin: 0 0 12px;
}
.editor-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
.item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--pa-border);
}
.item.sub {
  border-bottom: none;
  padding: 8px 0;
}
.content {
  flex: 1;
  min-width: 0;
}
.head {
  display: flex;
  gap: 8px;
  align-items: center;
}
.name {
  font-weight: 500;
}
p {
  margin: 6px 0;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
p.deleted {
  color: var(--pa-text-3);
}
.ops {
  display: flex;
  gap: 16px;
  color: var(--pa-text-3);
  font-size: 13px;
}
.ops span {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.ops span:hover,
.ops span.on {
  color: var(--pa-primary);
}
.reply-box {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.replies {
  background: var(--pa-bg);
  border-radius: 6px;
  padding: 4px 12px;
  margin-top: 8px;
}
.more {
  padding: 16px 0 0;
  text-align: center;
}
</style>
