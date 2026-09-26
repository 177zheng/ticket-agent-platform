<template>
  <div class="page">
    <el-card class="card">
      <h2 class="logo">注册员工账号</h2>
      <p class="sub">注册即为「员工」角色：可提交工单并跟踪自己工单的处理进度。<br>
        管理员账号由系统预置，不开放注册。</p>

      <el-form :model="form" label-width="72px" @keyup.enter="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="3-20 位字母/数字/下划线" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.name" placeholder="真实姓名（工单提单人显示名）" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="用于接收工单回复" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="至少 6 位" show-password />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="submit">
          注册并登录
        </el-button>
      </el-form>

      <div class="links">
        <el-link type="primary" @click="$router.push('/login')">← 返回登录</el-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'
import { saveAuth } from '../auth'

const router = useRouter()
const form = ref({ username: '', name: '', email: '', password: '' })
const loading = ref(false)

async function submit() {
  const f = form.value
  if (!f.username || !f.name || !f.email || !f.password) {
    ElMessage.warning('请填写完整')
    return
  }
  if (f.password.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  loading.value = true
  try {
    const data = await api.post('/auth/register', f)
    saveAuth(data)
    ElMessage.success(`注册成功，欢迎 ${data.name}`)
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
.card { width: 430px; padding: 12px 8px; }
.logo { text-align: center; margin: 4px 0; }
.sub { text-align: center; color: #909399; font-size: 12px; margin-bottom: 18px; line-height: 1.7; }
.links { text-align: center; margin-top: 14px; }
</style>
