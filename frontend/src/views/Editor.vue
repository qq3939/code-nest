<script setup lang="ts">
import { ElMessage, type UploadRequestOptions } from 'element-plus'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import DOMPurify from 'dompurify'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { articleApi, fileApi, metaApi } from '@/api'
import { ApiError } from '@/api/http'
import { useUserStore } from '@/stores/user'
import { mdCommonProps } from '@/utils/mdEditor'
import type { ArticleSaveReq, ArticleStatus, Category, Tag } from '@/types'

const AUTOSAVE_MS = 30_000

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const articleId = ref<string | null>((route.params.id as string) || null)
const status = ref<ArticleStatus>('DRAFT')
const form = reactive({
  title: '',
  summary: '',
  coverUrl: '',
  categoryId: null as string | null,
  tags: [] as string[],
  content: '',
})
const categories = ref<Category[]>([])
const tagOptions = ref<Tag[]>([])
const loading = ref(!!articleId.value)
const saving = ref(false)
const publishing = ref(false)
const lastSaved = ref<string>('')
// 上次保存时的内容快照：没有变化就不发自动保存请求
let savedSnapshot = ''
let timer: number | undefined

const isDraft = computed(() => status.value === 'DRAFT')
const snapshot = () => JSON.stringify(form)

function body(): ArticleSaveReq {
  return {
    title: form.title.trim(),
    summary: form.summary.trim() || undefined,
    coverUrl: form.coverUrl || undefined,
    categoryId: form.categoryId,
    tags: form.tags,
    content: form.content,
  }
}

onMounted(async () => {
  categories.value = await metaApi.categories().catch(() => [])
  if (articleId.value) {
    try {
      const a = await articleApi.forEdit(articleId.value)
      Object.assign(form, {
        title: a.title ?? '',
        summary: a.summary ?? '',
        coverUrl: a.coverUrl ?? '',
        categoryId: a.category?.id ?? null,
        tags: a.tags.map((t) => t.name),
        content: a.content ?? '',
      })
      status.value = a.status
    } catch {
      router.replace({ name: 'home' })
      return
    } finally {
      loading.value = false
    }
  }
  savedSnapshot = snapshot()
  // 只有草稿自动保存；已发布的文章修改要显式点"更新"，避免半成品直接上线
  timer = window.setInterval(() => {
    if (isDraft.value) saveDraft(true)
  }, AUTOSAVE_MS)
})

onBeforeUnmount(() => window.clearInterval(timer))

onBeforeRouteLeave(() => {
  if (snapshot() !== savedSnapshot && !publishing.value) {
    return window.confirm('有未保存的修改，确定离开吗？')
  }
  return true
})

async function saveDraft(auto = false) {
  const current = snapshot()
  if (auto && current === savedSnapshot) return
  if (!form.title.trim() && !form.content.trim()) return
  saving.value = true
  try {
    if (!articleId.value) {
      const res = await articleApi.create(body())
      articleId.value = res.id
      // 地址换成 /editor/:id，刷新页面也不会丢
      router.replace({ name: 'editor', params: { id: res.id } })
    } else {
      await articleApi.saveDraft(articleId.value, body())
    }
    savedSnapshot = current
    lastSaved.value = new Date().toLocaleTimeString()
    if (!auto) ElMessage.success('草稿已保存')
  } catch (e) {
    if (!auto && e instanceof ApiError) ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

function validate(): boolean {
  if (!form.title.trim()) return fail('请填写标题')
  if (!form.categoryId) return fail('请选择分类')
  if (form.content.trim().length < 10) return fail('正文至少 10 个字')
  return true
}

function fail(msg: string): false {
  ElMessage.warning(msg)
  return false
}

async function submit() {
  if (!validate()) return
  publishing.value = true
  try {
    let result: string
    if (isDraft.value) {
      if (!articleId.value) {
        articleId.value = (await articleApi.create(body())).id
      }
      // 防重复提交：每次发布先领一个一次性令牌，双击或网络重试时第二次会被后端拒绝（SPEC 9.11）
      const { token } = await articleApi.submitToken()
      result = (await articleApi.publish(articleId.value, token, body())).status
    } else {
      result = (await articleApi.update(articleId.value!, body())).status
    }
    savedSnapshot = snapshot()
    if (result === 'PENDING') {
      ElMessage.warning('内容包含需要审核的词，已提交人工审核')
      // 审核中的文章只有作者自己能在个人主页看到
      router.push(`/user/${userStore.user?.id}`)
    } else {
      ElMessage.success(isDraft.value ? '发布成功' : '已更新')
      router.push(`/article/${articleId.value}`)
    }
  } catch (e) {
    if (e instanceof ApiError && e.code === 10006) ElMessage.warning('请勿重复提交')
  } finally {
    publishing.value = false
  }
}

async function searchTags(keyword: string) {
  tagOptions.value = await metaApi.tags(keyword).catch(() => [])
}

async function uploadCover(options: UploadRequestOptions) {
  const res = await fileApi.uploadImage(options.file)
  form.coverUrl = res.url
}

function beforeCoverUpload(file: File) {
  if (!/^image\/(png|jpe?g|gif|webp)$/.test(file.type)) return fail('只支持 png、jpg、gif、webp')
  if (file.size > 5 * 1024 * 1024) return fail('图片不能超过 5MB')
  return true
}

// 正文里粘贴、拖入的图片也走同一个上传接口
async function onUploadImg(files: File[], callback: (urls: string[]) => void) {
  const urls: string[] = []
  for (const f of files) {
    if (beforeCoverUpload(f)) urls.push((await fileApi.uploadImage(f)).url)
  }
  callback(urls)
}

const sanitize = (html: string) => DOMPurify.sanitize(html)
</script>

<template>
  <div v-loading="loading" class="pa-page editor">
    <div class="pa-card head">
      <el-input v-model="form.title" class="title" placeholder="输入文章标题…" maxlength="128" />
      <span class="pa-muted save-tip">
        <template v-if="saving">保存中…</template>
        <template v-else-if="lastSaved">已于 {{ lastSaved }} 保存</template>
        <template v-else-if="isDraft">草稿每 30 秒自动保存</template>
      </span>
      <el-button v-if="isDraft" :loading="saving" @click="saveDraft(false)">存草稿</el-button>
      <el-button type="primary" :loading="publishing" @click="submit">{{ isDraft ? '发布' : '更新' }}</el-button>
    </div>
    <div class="pa-card form">
      <el-form label-width="64px">
        <el-form-item label="分类" required>
          <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 220px">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="form.tags" multiple filterable allow-create remote default-first-option
            :remote-method="searchTags" :multiple-limit="5" placeholder="输入后回车，最多 5 个" style="width: 100%">
            <el-option v-for="t in tagOptions" :key="t.id" :label="t.name" :value="t.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="512" show-word-limit placeholder="不填则自动截取正文前 200 字" />
        </el-form-item>
        <el-form-item label="封面">
          <el-upload :show-file-list="false" :http-request="uploadCover" :before-upload="beforeCoverUpload" accept="image/*">
            <img v-if="form.coverUrl" :src="form.coverUrl" class="cover" alt="封面" />
            <div v-else class="cover-empty"><el-icon><Plus /></el-icon><span>上传封面</span></div>
          </el-upload>
          <el-button v-if="form.coverUrl" link type="danger" @click="form.coverUrl = ''">移除</el-button>
        </el-form-item>
      </el-form>
    </div>
    <MdEditor v-model="form.content" v-bind="mdCommonProps" no-prettier class="md" language="zh-CN" :sanitize="sanitize" :on-upload-img="onUploadImg"
      :toolbars-exclude="['github', 'save']" placeholder="开始写作，支持 Markdown…" />
  </div>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
}
.title {
  flex: 1;
}
.title :deep(.el-input__wrapper) {
  box-shadow: none;
  font-size: 20px;
  font-weight: 600;
}
.save-tip {
  white-space: nowrap;
}
.form {
  padding: 16px 16px 0;
}
.cover,
.cover-empty {
  width: 180px;
  height: 100px;
  border-radius: 6px;
  object-fit: cover;
}
.cover-empty {
  border: 1px dashed var(--pa-border);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--pa-text-3);
  gap: 4px;
}
.md {
  height: 640px;
}
</style>
