<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    await userStore.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/'
    router.replace(redirect)
  } catch {
    // 错误提示（密码错误、账号锁定、封禁）由请求拦截器统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth">
    <div class="pa-card box">
      <h2>登录 project-a</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" class="btn">登录</el-button>
      </el-form>
      <p class="pa-muted foot">
        还没有账号？<RouterLink :to="{ name: 'register', query: route.query }" class="link">立即注册</RouterLink>
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
