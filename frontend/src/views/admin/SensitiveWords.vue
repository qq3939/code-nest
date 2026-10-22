<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { adminApi } from '@/api'
import type { SensitiveWord } from '@/types'

const levels = {
  BLOCK: { text: '拦截', type: 'danger', desc: '包含即拒绝发布' },
  REVIEW: { text: '送审', type: 'warning', desc: '包含则进入人工审核' },
  REPLACE: { text: '替换', type: 'info', desc: '替换成 *** 后发布' },
} as const

const query = reactive({ keyword: '', page: 1, size: 20 })
const rows = ref<SensitiveWord[]>([])
const total = ref(0)
const loading = ref(false)
const dialog = reactive({
  visible: false,
  id: '',
  word: '',
  level: 'BLOCK' as SensitiveWord['level'],
  enabled: true,
})

async function load() {
  loading.value = true
  try {
    const res = await adminApi.words({ keyword: query.keyword || undefined, page: query.page, size: query.size })
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function edit(w?: SensitiveWord) {
  Object.assign(dialog, { visible: true, id: w?.id ?? '', word: w?.word ?? '', level: w?.level ?? 'BLOCK', enabled: w?.enabled ?? true })
}

async function save() {
  if (!dialog.word.trim()) return ElMessage.warning('请输入敏感词')
  const body = { word: dialog.word.trim(), level: dialog.level, enabled: dialog.enabled }
  if (dialog.id) await adminApi.updateWord(dialog.id, body)
  else await adminApi.createWord(body)
  dialog.visible = false
  // 后端改完会广播给所有实例重建前缀树，下一次发文就生效
  ElMessage.success('已保存，所有实例即时生效')
  load()
}

async function toggle(w: SensitiveWord) {
  await adminApi.updateWord(w.id, { word: w.word, level: w.level, enabled: !w.enabled })
  load()
}

async function remove(w: SensitiveWord) {
  await ElMessageBox.confirm(`删除敏感词「${w.word}」？`, '删除', { type: 'warning' })
  await adminApi.deleteWord(w.id)
  load()
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div class="filters">
      <el-input v-model="query.keyword" placeholder="搜索" clearable style="width: 220px"
        @keyup.enter="query.page = 1; load()" @clear="load" />
      <el-button type="primary" @click="edit()">新增敏感词</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="word" label="敏感词" />
      <el-table-column label="级别" width="200">
        <template #default="{ row }">
          <el-tag :type="levels[row.level as SensitiveWord['level']].type" size="small">{{ levels[row.level as SensitiveWord['level']].text }}</el-tag>
          <span class="pa-muted desc">{{ levels[row.level as SensitiveWord['level']].desc }}</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="90">
        <template #default="{ row }"><el-switch :model-value="row.enabled" @change="toggle(row)" /></template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="edit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" class="pager" layout="total, prev, pager, next" :total="total"
      :page-size="query.size" @current-change="load" />

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑敏感词' : '新增敏感词'" width="420px">
      <el-form label-width="60px">
        <el-form-item label="词"><el-input v-model="dialog.word" maxlength="64" /></el-form-item>
        <el-form-item label="级别">
          <el-radio-group v-model="dialog.level">
            <el-radio v-for="(l, k) in levels" :key="k" :value="k">{{ l.text }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="dialog.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.filters {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.desc {
  margin-left: 8px;
}
.pager {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
