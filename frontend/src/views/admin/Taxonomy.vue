<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { adminApi } from '@/api'
import type { Category, Tag } from '@/types'

// ---------- 分类 ----------
const categories = ref<Category[]>([])
const catDialog = reactive({ visible: false, id: '' as string, name: '', sort: 0 })

async function loadCategories() {
  categories.value = await adminApi.categories()
}

function editCategory(c?: Category) {
  Object.assign(catDialog, { visible: true, id: c?.id ?? '', name: c?.name ?? '', sort: c?.sort ?? categories.value.length + 1 })
}

async function saveCategory() {
  if (!catDialog.name.trim()) return ElMessage.warning('请输入分类名')
  if (catDialog.id) await adminApi.updateCategory(catDialog.id, catDialog.name.trim(), catDialog.sort)
  else await adminApi.createCategory(catDialog.name.trim(), catDialog.sort)
  catDialog.visible = false
  ElMessage.success('已保存')
  loadCategories()
}

async function removeCategory(c: Category) {
  await ElMessageBox.confirm(`删除分类「${c.name}」？该分类下有文章时无法删除。`, '删除分类', { type: 'warning' })
  await adminApi.deleteCategory(c.id)
  ElMessage.success('已删除')
  loadCategories()
}

// ---------- 标签 ----------
const tagQuery = reactive({ keyword: '', page: 1, size: 20 })
const tags = ref<Tag[]>([])
const tagTotal = ref(0)
const selected = ref<Tag[]>([])
const mergeDialog = reactive({ visible: false, targetId: '' })

async function loadTags() {
  const res = await adminApi.tags({ keyword: tagQuery.keyword || undefined, page: tagQuery.page, size: tagQuery.size })
  tags.value = res.records
  tagTotal.value = res.total
}

async function removeTag(t: Tag) {
  await ElMessageBox.confirm(`删除标签「${t.name}」？文章上的这个标签也会被去掉。`, '删除标签', { type: 'warning' })
  await adminApi.deleteTag(t.id)
  ElMessage.success('已删除')
  loadTags()
}

function openMerge() {
  if (selected.value.length < 2) return ElMessage.warning('至少选择两个标签')
  mergeDialog.targetId = selected.value[0]!.id
  mergeDialog.visible = true
}

async function doMerge() {
  const sources = selected.value.map((t) => t.id).filter((id) => id !== mergeDialog.targetId)
  await adminApi.mergeTags(sources, mergeDialog.targetId)
  mergeDialog.visible = false
  ElMessage.success('已合并')
  loadTags()
}

onMounted(() => {
  loadCategories()
  loadTags()
})
</script>

<template>
  <div class="grid">
    <el-card shadow="never" header="分类">
      <el-button type="primary" size="small" class="mb" @click="editCategory()">新增分类</el-button>
      <el-table :data="categories" size="small">
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="editCategory(row)">编辑</el-button>
            <el-button link type="danger" @click="removeCategory(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" header="标签">
      <div class="filters">
        <el-input v-model="tagQuery.keyword" size="small" placeholder="搜索标签" clearable style="width: 180px"
          @keyup.enter="tagQuery.page = 1; loadTags()" @clear="loadTags" />
        <el-button size="small" :disabled="selected.length < 2" @click="openMerge">合并选中（{{ selected.length }}）</el-button>
      </div>
      <el-table :data="tags" size="small" @selection-change="(v: Tag[]) => (selected = v)">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="name" label="名称" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeTag(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="tagQuery.page" class="pager" small layout="total, prev, pager, next"
        :total="tagTotal" :page-size="tagQuery.size" @current-change="loadTags" />
    </el-card>

    <el-dialog v-model="catDialog.visible" :title="catDialog.id ? '编辑分类' : '新增分类'" width="400px">
      <el-form label-width="60px">
        <el-form-item label="名称"><el-input v-model="catDialog.name" maxlength="32" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="catDialog.sort" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mergeDialog.visible" title="合并标签" width="420px">
      <p>选中的其他标签会合并到下面这个标签，文章上的标签会自动替换。</p>
      <el-radio-group v-model="mergeDialog.targetId" class="targets">
        <el-radio v-for="t in selected" :key="t.id" :value="t.id">{{ t.name }}</el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="mergeDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="doMerge">合并</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 16px;
  align-items: start;
}
.mb {
  margin-bottom: 12px;
}
.filters {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.pager {
  justify-content: flex-end;
  margin-top: 12px;
}
.targets {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
</style>
