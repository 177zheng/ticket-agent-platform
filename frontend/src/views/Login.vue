<template>
  <div class="page">
    <el-card class="card">
      <h2 class="logo">🎫 工单智能平台</h2>
      <p class="sub">Java 21 · Spring Boot 3 · 多智能体流水线</p>

      <el-form :model="form" @keyup.enter="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>

      <div class="links">
        <el-link type="primary" @click="$router.push('/register')">没有账号？注册员工账号 →</el-link>
      </div>
      <el-alert type="info" :closable="false" class="hint"
                title="演示管理员：admin / admin123（员工请自助注册）" />
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'
import { setAuth } from '../auth'

const router = useRouter()
const form = ref({ username: '', password: '' })
const loading = ref(false)

async function submit() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const data = await api.post('/auth/login', form.value)
    setAuth(data)
    ElMessage.success(`欢迎，${data.name}（${data.roleLabel}）`)
    router.push('/')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { height: 100vh; display: flex; align-items: center; justify-content: center; background: #1d2b45; }
.card { width: 400px; padding: 12px 8px; }
.logo { text-align: center; margin: 4px 0; }
.sub { text-align: center; color: #909399; font-size: 12px; margin-bottom: 18px; }
.links { text-align: center; margin: 14px 0 4px; }
.hint { margin-top: 8px; }
</style>
