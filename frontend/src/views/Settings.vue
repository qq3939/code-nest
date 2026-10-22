<script setup lang="ts">
import { ElMessage, type FormInstance, type UploadRequestOptions } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fileApi, userApi } from '@/api'
import UserAvatar from '@/components/UserAvatar.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()

const tab = ref<'profile' | 'password'>('profile')
const profile = reactive({ nickname: '', bio: '', avatarUrl: '' })
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const pwdRef = ref<FormInstance>()
const saving = ref(false)

onMounted(async () => {
  await userStore.reload().catch(() => undefined)
  const u = userStore.user
  if (u) Object.assign(profile, { nickname: u.nickname, bio: u.bio ?? '', avatarUrl: u.avatarUrl ?? '' })
})

async function uploadAvatar(options: UploadRequestOptions) {
  profile.avatarUrl = (await fileApi.uploadImage(options.file)).url
}

function beforeUpload(file: File) {
  if (!/^image\/(png|jpe?g|gif|webp)$/.test(file.type)) {
    ElMessage.warning('只支持 png、jpg、gif、webp')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5MB')
    return false
  }
  return true
}

async function saveProfile() {
  if (!profile.nickname.trim()) return ElMessage.warning('昵称不能为空')
  saving.value = true
  try {
    const u = await userApi.updateMe({
      nickname: profile.nickname.trim(),
      bio: profile.bio.trim(),
      avatarUrl: profile.avatarUrl || undefined,
    })
    userStore.setUser(u)
    ElMessage.success('资料已更新')
  } finally {
    saving.value = false
  }
}

async function changePassword() {
  if (!(await pwdRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    await userApi.changePassword(pwd.oldPassword, pwd.newPassword)
    // 后端改密后会作废所有 refresh token，这里直接让用户重新登录
    ElMessage.success('密码已修改，请重新登录')
    userStore.clear()
    router.replace({ name: 'login' })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="pa-page">
    <section class="pa-card wrap">
      <el-tabs v-model="tab">
        <el-tab-pane label="个人资料" name="profile">
          <el-form label-width="80px" class="form">
            <el-form-item label="头像">
              <el-upload :show-file-list="false" :http-request="uploadAvatar" :before-upload="beforeUpload" accept="image/*">
                <div class="avatar">
                  <UserAvatar :name="profile.nickname" :src="profile.avatarUrl" :size="72" />
                  <span class="pa-muted">点击更换</span>
                </div>
              </el-upload>
            </el-form-item>
            <el-form-item label="用户名">
              <el-input :model-value="userStore.user?.username" disabled />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="profile.nickname" maxlength="32" show-word-limit />
            </el-form-item>
            <el-form-item label="简介">
              <el-input v-model="profile.bio" type="textarea" :rows="3" maxlength="255" show-word-limit />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="修改密码" name="password">
          <el-form ref="pwdRef" :model="pwd" label-width="80px" class="form">
            <el-form-item label="原密码" prop="oldPassword" :rules="[{ required: true, message: '请输入原密码' }]">
              <el-input v-model="pwd.oldPassword" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword" :rules="[{ required: true, min: 8, max: 64, message: '密码长度 8–64 位' }]">
              <el-input v-model="pwd.newPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirm"
              :rules="[{ validator: (_r: unknown, v: string, cb: (e?: Error) => void) => (v === pwd.newPassword ? cb() : cb(new Error('两次输入不一致'))) }]">
              <el-input v-model="pwd.confirm" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="changePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped>
.wrap {
  padding: 8px 24px 24px;
}
.form {
  max-width: 520px;
  padding-top: 12px;
}
.avatar {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}
</style>
