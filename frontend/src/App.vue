<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">🎫 工单智能平台</div>
      <el-menu router :default-active="$route.path" class="menu">
        <el-menu-item index="/">
          <el-icon><Odometer /></el-icon><span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/tickets">
          <el-icon><Tickets /></el-icon><span>工单管理</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
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
        <span class="subtitle">Java 21 · Spring Boot 3 · 多智能体流水线</span>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Odometer, Tickets, Collection } from '@element-plus/icons-vue'
import api from './api'

const llmMode = ref('mock')
onMounted(async () => {
  try {
    const meta = await api.get('/meta')
    llmMode.value = meta.llmMode || 'mock'
  } catch { /* meta 接口不可用时默认显示 mock */ }
})
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
.header { background: #fff; display: flex; align-items: baseline; gap: 14px; border-bottom: 1px solid #e4e7ed; }
.title { font-size: 17px; font-weight: 600; }
.subtitle { color: #909399; font-size: 12px; }
.main { background: #f0f2f5; }
</style>
