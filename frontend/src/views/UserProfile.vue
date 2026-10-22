<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userApi } from '@/api'
import ArticleCard from '@/components/ArticleCard.vue'
import FollowButton from '@/components/FollowButton.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useUserStore } from '@/stores/user'
import type { ArticleListItem, UserBrief, UserProfile } from '@/types'
import { compact, formatTime } from '@/utils/format'

type Tab = 'articles' | 'collections' | 'followings' | 'followers'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const id = route.params.id as string

const profile = ref<UserProfile | null>(null)
const notFound = ref(false)
const tab = ref<Tab>((route.query.tab as Tab) || 'articles')
const articles = ref<ArticleListItem[]>([])
const users = ref<UserBrief[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const isSelf = computed(() => userStore.user?.id === id)

async function loadTab() {
  loading.value = true
  try {
    if (tab.value === 'articles' || tab.value === 'collections') {
      const res = await (tab.value === 'articles' ? userApi.articles : userApi.collections)(id, page.value, 10)
      articles.value = res.records
      total.value = res.total
    } else {
      const res = await (tab.value === 'followers' ? userApi.followers : userApi.followings)(id, page.value, 20)
      users.value = res.records
      total.value = res.total
    }
  } finally {
    loading.value = false
  }
}

watch(tab, () => {
  page.value = 1
  router.replace({ query: { tab: tab.value } })
  loadTab()
})
watch(page, loadTab)

onMounted(async () => {
  try {
    profile.value = await userApi.profile(id)
    document.title = `${profile.value.user.nickname} - code-nest`
  } catch {
    notFound.value = true
    return
  }
  loadTab()
})

function onFollowChange(followed: boolean) {
  if (profile.value) profile.value.stat.followerCount += followed ? 1 : -1
}
</script>

<template>
  <div class="pa-page">
    <el-result v-if="notFound" icon="warning" title="用户不存在" />
    <template v-else-if="profile">
      <section class="pa-card header">
        <UserAvatar :name="profile.user.nickname" :src="profile.user.avatarUrl" :size="72" />
        <div class="info">
          <h2>{{ profile.user.nickname }}</h2>
          <p class="pa-muted">{{ profile.user.bio || '这个人很懒，什么都没写' }}</p>
          <p class="pa-muted">加入于 {{ formatTime(profile.joinTime) }}</p>
        </div>
        <div class="ops">
          <el-button v-if="isSelf" @click="router.push({ name: 'settings' })">编辑资料</el-button>
          <FollowButton v-else :user-id="id" :followed="profile.followed" @change="onFollowChange" />
        </div>
      </section>
      <section class="pa-card stats">
        <div><b>{{ compact(profile.stat.articleCount) }}</b><span>文章</span></div>
        <div><b>{{ compact(profile.stat.followingCount) }}</b><span>关注</span></div>
        <div><b>{{ compact(profile.stat.followerCount) }}</b><span>粉丝</span></div>
        <div><b>{{ compact(profile.stat.likeReceived) }}</b><span>获赞</span></div>
        <div><b>{{ compact(profile.stat.readReceived) }}</b><span>阅读</span></div>
      </section>
      <section v-loading="loading" class="pa-card list">
        <el-tabs v-model="tab" class="tabs">
          <el-tab-pane label="文章" name="articles" />
          <el-tab-pane label="收藏" name="collections" />
          <el-tab-pane label="关注" name="followings" />
          <el-tab-pane label="粉丝" name="followers" />
        </el-tabs>
        <template v-if="tab === 'articles' || tab === 'collections'">
          <ArticleCard v-for="a in articles" :key="a.id" :article="a" :show-status="isSelf && tab === 'articles'" />
        </template>
        <template v-else>
          <div v-for="u in users" :key="u.id" class="user-row">
            <RouterLink :to="`/user/${u.id}`" class="user-link">
              <UserAvatar :name="u.nickname" :src="u.avatarUrl" :size="40" />
              <div>
                <div>{{ u.nickname }}</div>
                <div class="pa-muted pa-ellipsis">{{ u.bio }}</div>
              </div>
            </RouterLink>
          </div>
        </template>
        <el-empty v-if="!loading && total === 0" description="这里还是空的" />
        <el-pagination v-if="total > (tab.startsWith('follow') ? 20 : 10)" v-model:current-page="page" class="pager"
          layout="prev, pager, next" :total="total" :page-size="tab.startsWith('follow') ? 20 : 10" />
      </section>
    </template>
    <el-skeleton v-else :rows="6" animated />
  </div>
</template>

<style scoped>
.header {
  display: flex;
  gap: 20px;
  align-items: center;
  padding: 24px;
}
.info {
  flex: 1;
  min-width: 0;
}
.info h2 {
  margin: 0 0 6px;
}
.info p {
  margin: 2px 0;
}
.stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  margin-top: 12px;
  padding: 14px 0;
  text-align: center;
}
.stats div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stats b {
  font-size: 18px;
}
.stats span {
  color: var(--pa-text-3);
  font-size: 13px;
}
.list {
  margin-top: 12px;
  min-height: 300px;
}
.tabs {
  padding: 0 20px;
}
.tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.user-row {
  padding: 12px 20px;
  border-bottom: 1px solid var(--pa-border);
}
.user-link {
  display: flex;
  gap: 12px;
  align-items: center;
}
.pager {
  justify-content: center;
  padding: 16px 0;
}
</style>
