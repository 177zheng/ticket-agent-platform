<template>
  <el-row :gutter="16">
    <!-- 基本信息 -->
    <el-col :span="12">
      <el-card shadow="hover">
        <template #header>👤 个人信息</template>
        <el-form label-width="90px" style="max-width:420px">
          <el-form-item label="用户名">
            <el-input :model-value="me.username" disabled />
            <div class="tip">用户名与角色不可自行修改</div>
          </el-form-item>
          <el-form-item label="角色">
            <el-input :model-value="me.roleLabel" disabled />
          </el-form-item>
          <el-form-item label="注册时间">
            <el-input :model-value="fmtTime(me.createdAt)" disabled />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input v-model="form.name" placeholder="工单提单人显示名" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="form.email" placeholder="用于接收工单回复" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveProfile">保存修改</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </el-col>

    <!-- 修改密码 -->
    <el-col :span="12">
      <el-card shadow="hover">
        <template #header>🔒 修改密码</template>
        <el-form label-width="90px" style="max-width:420px">
          <el-form-item label="原密码">
            <el-input v-model="pwd.oldPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="pwd.newPassword" type="password" show-password placeholder="至少 6 位" />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input v-model="pwd.confirm" type="password" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="warning" :loading="changingPwd" @click="savePassword">修改密码</el-button>
          </el-form-item>
        </el-form>
        <el-alert type="info" :closable="false"
                  title="修改成功后无需重新登录；下次登录请使用新密码。" />
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { getAuth, patchAuth } from '../auth'
import { fmtTime } from '../constants'

const me = ref({ ...getAuth() })
const form = ref({ name: '', email: '' })
const pwd = ref({ oldPassword: '', newPassword: '', confirm: '' })
const saving = ref(false)
const changingPwd = ref(false)

onMounted(async () => {
  // 以服务端为准刷新一次（拿注册时间等本地没有的字段）
  try {
    const fresh = await api.get('/auth/me')
    me.value = fresh
    form.value = { name: fresh.name, email: fresh.email }
  } catch {
    form.value = { name: me.value.name, email: me.value.email }
  }
})

async function saveProfile() {
  if (!form.value.name || !form.value.email) {
    ElMessage.warning('姓名和邮箱不能为空')
    return
  }
  saving.value = true
  try {
    const updated = await api.put('/auth/profile', form.value)
    me.value = { ...me.value, ...updated }
    patchAuth({ name: updated.name, email: updated.email, roleLabel: updated.roleLabel })
    ElMessage.success('个人信息已更新（顶栏同步刷新）')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function savePassword() {
  const p = pwd.value
  if (!p.oldPassword || !p.newPassword) {
    ElMessage.warning('请填写原密码和新密码')
    return
  }
  if (p.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (p.newPassword !== p.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  changingPwd.value = true
  try {
    await api.put('/auth/password', { oldPassword: p.oldPassword, newPassword: p.newPassword })
    ElMessage.success('密码修改成功')
    pwd.value = { oldPassword: '', newPassword: '', confirm: '' }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    changingPwd.value = false
  }
}
</script>

<style scoped>
.tip { font-size: 12px; color: #909399; line-height: 1.6; }
</style>
