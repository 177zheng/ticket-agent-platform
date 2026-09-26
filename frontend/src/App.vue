<template>
  <!-- 登录/注册页：不带框架的全屏布局 -->
  <router-view v-if="$route.meta.public" />

  <el-container v-else class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">🎫 工单智能平台</div>
      <el-menu router :default-active="$route.path" class="menu">
        <el-menu-item index="/">
          <el-icon><Odometer /></el-icon><span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/tickets">
          <el-icon><Tickets /></el-icon><span>工单管理</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin()" index="/knowledge">
          <el-icon><Collection /></el-icon><span>知识库</span>
        </el-menu-item>
      </el-menu>
      <div class="aside-footer">
        <el-tag size="small" :type="llmMode === 'mock' ? 'info' : 'success'">
          Agent 模式：{{ llmMode === 'mock' ? 'Mock 规则' : '真实 LLM' }}
        </el-tag>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="title">{{ $route.meta.title }}</span>
        <span v-if="!isAdmin()" class="subtitle">我的工单视图</span>
        <span v-else class="subtitle">Java 21 · Spring Boot 3 · 多智能体流水线</span>

        <el-dropdown class="user" @command="onUserCommand">
          <span class="user-info">
            <el-avatar :size="28" style="background:#409eff">{{ auth?.name?.slice(0, 1) }}</el-avatar>
            <span class="user-name">{{ auth?.name }}</span>
            <el-tag size="small" :type="isAdmin() ? 'danger' : 'success'">{{ auth?.roleLabel }}</el-tag>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>用户名：{{ auth?.username }}</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Odometer, Tickets, Collection } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from './api'
import { getAuth, clearAuth, isAdmin } from './auth'

const router = useRouter()
const auth = ref(getAuth())
const llmMode = ref('mock')

onMounted(async () => {
  try {
    const meta = await api.get('/meta')
    llmMode.value = meta.llmMode || 'mock'
  } catch { /* meta 接口不可用时默认显示 mock */ }
})

function onUserCommand(cmd) {
  if (cmd === 'logout') {
    clearAuth()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background: #1d2b45; display: flex; flex-direction: column; }
.logo { color: #fff; font-size: 17px; font-weight: 600; padding: 18px 16px; }
.menu { background: transparent; border-right: none; flex: 1; }
.menu :deep(.el-menu-item) { color: #b7c3d9; }
.menu :deep(.el-menu-item.is-active) { color: #409eff; background: #16223a; }
.menu :deep(.el-menu-item:hover) { background: #16223a; }
.aside-footer { padding: 14px 16px; }
.header { background: #fff; display: flex; align-items: center; gap: 14px; border-bottom: 1px solid #e4e7ed; }
.title { font-size: 17px; font-weight: 600; }
.subtitle { color: #909399; font-size: 12px; }
.user { margin-left: auto; cursor: pointer; }
.user-info { display: flex; align-items: center; gap: 8px; }
.user-name { font-size: 14px; }
.main { background: #f0f2f5; }
</style>
