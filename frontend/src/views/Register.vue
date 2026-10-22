<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', nickname: '', password: '', confirm: '' })
const loading = ref(false)
// 前端校验和后端 RegisterReq 上的注解保持一致，后端仍会再校验一次
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{4,32}$/, message: '4–32 位字母、数字或下划线', trigger: 'blur' },
  ],
  nickname: [{ max: 32, message: '昵称最多 32 个字', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度 8–64 位', trigger: 'blur' },
  ],
  confirm: [
    {
      validator: (_r, v: string, cb) => (v === form.password ? cb() : cb(new Error('两次输入的密码不一致'))),
      trigger: 'blur',
    },
  ],
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    await userStore.register(form.username.trim(), form.password, form.nickname.trim() || undefined)
    ElMessage.success('注册成功')
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/'
    router.replace(redirect)
  } catch {
    // 用户名已存在等错误由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth">
    <div class="pa-card box">
      <h2>注册账号</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" placeholder="登录用，注册后不可修改" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="选填，默认与用户名相同" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirm">
          <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" class="btn">注册</el-button>
      </el-form>
      <p class="pa-muted foot">
        已有账号？<RouterLink :to="{ name: 'login', query: route.query }" class="link">去登录</RouterLink>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}
.box {
  width: 380px;
  max-width: 100%;
  padding: 28px 32px;
}
h2 {
  margin: 0 0 20px;
  text-align: center;
}
.btn {
  width: 100%;
  margin-top: 8px;
}
.foot {
  text-align: center;
  margin-top: 16px;
}
.link {
  color: var(--pa-primary);
}
</style>
