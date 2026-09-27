<template>
  <div>
    <el-card shadow="never">
      <div class="toolbar">
        <el-radio-group v-model="statusFilter" @change="fetchList">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button v-for="(label, s) in STATUS_LABEL" :key="s" :value="s">{{ label }}</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="dlg = true">+ 新建工单</el-button>
      </div>

      <el-table :data="filtered" v-loading="loading" @row-click="openDetail" row-class-name="clickable"
                :header-cell-style="{ background: '#f8fafc' }">
        <el-table-column prop="id" label="#" width="64" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="类别" width="110">
          <template #default="{ row }">{{ row.category || '-' }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="84">
          <template #default="{ row }">
            <el-tag v-if="row.priority" :type="priorityTag(row.priority)" size="small">{{ PRIORITY_LABEL[row.priority] }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="assignedTeam" label="处理组" width="110">
          <template #default="{ row }">{{ row.assignedTeam || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="116">
          <template #default="{ row }">
            <el-tag :type="STATUS_TAG[row.status]" size="small">{{ STATUS_LABEL[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建工单（提单人取自登录态，无需填写） -->
    <el-dialog v-model="dlg" title="新建工单" width="560px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="例：VPN 连接后打不开内网系统" />
        </el-form-item>
        <el-form-item label="问题描述" required>
          <el-input v-model="form.description" type="textarea" :rows="4"
                    placeholder="现象、发生时间、影响范围…（提单人：登录账号自动关联）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="create">提交（触发智能体流水线）</el-button>
      </template>
    </el-dialog>

    <!-- 工单详情抽屉 -->
    <el-drawer v-model="drawer" :title="`工单 #${current?.id ?? ''} 详情`" size="46%">
      <template v-if="current">
        <el-alert v-if="current.errorMessage" type="error" :closable="false" show-icon
                  :title="`失败阶段 ${current.failedStep}`" :description="current.errorMessage" style="margin-bottom:12px" />

        <div class="actions" v-if="isAdmin()">
          <el-button v-if="current.status === 'HUMAN_REVIEW'" type="success"
                     @click="review(true)">✓ 审核通过并发送回复</el-button>
          <el-button v-if="current.status === 'HUMAN_REVIEW'" type="warning" plain
                     @click="review(false)">✗ 驳回，转人工</el-button>
          <el-button v-if="current.status === 'FAILED'" type="primary"
                     @click="retry">⟳ 重试（断点续跑）</el-button>
          <el-button v-if="current.status === 'ESCALATED'" type="success" plain
                     @click="closeTicket">人工已处理，关闭工单</el-button>
        </div>

        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="状态">
            <el-tag :type="STATUS_TAG[current.status]" size="small">{{ STATUS_LABEL[current.status] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="优先级">
            <el-tag v-if="current.priority" :type="priorityTag(current.priority)" size="small">{{ PRIORITY_LABEL[current.priority] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="类别">{{ current.category || '-' }}</el-descriptions-item>
          <el-descriptions-item label="处理组">{{ current.assignedTeam || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提单人">{{ current.requesterName }}（{{ current.requesterEmail }}）</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ fmtTime(current.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="分诊置信度">{{ current.triageConfidence ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="回复置信度">{{ current.replyConfidence ?? '-' }}</el-descriptions-item>
        </el-descriptions>

        <h4>📝 问题描述</h4>
        <div class="block">{{ current.description }}</div>

        <template v-if="current.triageSummary">
          <h4>🧭 规划 Agent · 分诊结论</h4>
          <div class="block">{{ current.triageSummary }}</div>
          <ol class="subtasks" v-if="current.subtasks?.length">
            <li v-for="(s, i) in current.subtasks" :key="i">{{ s }}</li>
          </ol>
        </template>

        <template v-if="current.manuals?.length || current.similarTickets?.length">
          <h4>🔍 检索 Agent · 知识库命中</h4>
          <div class="hit" v-for="(m, i) in current.manuals" :key="'m' + i">
            <b>《{{ m.sourceTitle }}》</b><br>{{ m.snippet }}
          </div>
          <div class="hit" v-for="(s, i) in current.similarTickets" :key="'s' + i">
            <b>相似工单 #{{ s.ticketId }}</b> {{ s.title }}<br>解决方式：{{ s.resolution }}
          </div>
        </template>

        <template v-if="current.replyDraft">
          <h4>✉️ 回复 Agent · 草稿
            <el-tag size="small" type="info">建议：{{ current.suggestedActionLabel || '-' }}</el-tag>
          </h4>
          <div class="block draft">{{ current.replyDraft }}</div>
        </template>

        <template v-if="current.resolution">
          <h4>✅ 最终处理方案
            <el-tag size="small" type="success">已记入知识回流，供后续相似工单检索</el-tag>
          </h4>
          <div class="block resolved">{{ current.resolution }}</div>
        </template>

        <h4>📜 流转时间线</h4>
        <el-timeline style="padding-left:4px">
          <el-timeline-item v-for="(e, i) in current.events" :key="i" :timestamp="fmtTime(e.createdAt)"
                            placement="top" :type="e.actor === 'HUMAN' ? 'warning' : 'primary'">
            <b>{{ e.actor }}</b>
            {{ e.fromStatus === 'NONE' ? '' : STATUS_LABEL[e.fromStatus] + ' → ' }}{{ STATUS_LABEL[e.toStatus] }}
            <div v-if="e.note" class="note">{{ e.note }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { STATUS_LABEL, STATUS_TAG, PRIORITY_LABEL, isTerminal, fmtTime } from '../constants'
import { isAdmin } from '../auth'

const tickets = ref([])
const loading = ref(false)
const statusFilter = ref('')
const filtered = computed(() =>
  statusFilter.value ? tickets.value.filter(t => t.status === statusFilter.value) : tickets.value)

const dlg = ref(false)
const submitting = ref(false)
const form = ref({ title: '', description: '' })

const drawer = ref(false)
const current = ref(null)

async function fetchList() {
  loading.value = true
  try {
    tickets.value = await api.get('/tickets')
    if (drawer.value && current.value) {
      const fresh = tickets.value.find(t => t.id === current.value.id)
      if (fresh) current.value = fresh
    }
  } catch (e) {
    ElMessage.error('加载失败：' + e.message)
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!form.value.title || !form.value.description) {
    ElMessage.warning('请填写标题和问题描述')
    return
  }
  submitting.value = true
  try {
    await api.post('/tickets', form.value)
    ElMessage.success('工单已创建，智能体流水线已启动')
    dlg.value = false
    form.value = { title: '', description: '' }
    fetchList()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

function openDetail(row) {
  current.value = row
  drawer.value = true
}

async function review(approve) {
  try {
    const { value } = await ElMessageBox.prompt(
      approve ? '审核意见（可留空）' : '驳回原因（可留空）', '人工审核',
      { confirmButtonText: '确定', cancelButtonText: '取消' })
    await api.post(`/tickets/${current.value.id}/review`, { approve, comment: value || '' })
    ElMessage.success(approve ? '已通过审核并发送回复' : '已驳回并转人工')
    fetchList()
    const fresh = (await api.get(`/tickets/${current.value.id}`))
    current.value = fresh
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function retry() {
  try {
    await api.post(`/tickets/${current.value.id}/retry`)
    ElMessage.success('已触发重试')
    fetchList()
  } catch (e) { ElMessage.error(e.message) }
}

async function closeTicket() {
  try {
    const { value } = await ElMessageBox.prompt(
      '处理方案（必填）：将记录为该工单的最终解决方案，并回流给检索 Agent 用于后续相似工单',
      '关闭工单', { confirmButtonText: '确认关闭', cancelButtonText: '取消',
        inputValidator: v => (v && v.trim()) ? true : '处理方案不能为空' })
    await api.post(`/tickets/${current.value.id}/close`, { comment: value.trim() })
    ElMessage.success('工单已关闭，处理方案已记录')
    fetchList()
    current.value = await api.get(`/tickets/${current.value.id}`)
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

const priorityTag = p => ({ LOW: 'info', MEDIUM: 'primary', HIGH: 'warning', URGENT: 'danger' }[p] || 'info')

let timer
onMounted(() => {
  fetchList()
  timer = setInterval(() => {
    // 存在未到终态的工单时轮询刷新，模拟"实时看 Agent 干活"
    if (tickets.value.some(t => !isTerminal(t.status))) fetchList()
  }, 2500)
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 12px; flex-wrap: wrap; gap: 8px; }
:deep(.clickable) { cursor: pointer; }
.actions { margin-bottom: 12px; display: flex; gap: 8px; flex-wrap: wrap; }
h4 { margin: 18px 0 8px; color: #303133; }
.block { background: #f8fafc; border-radius: 6px; padding: 10px 12px; font-size: 13px; line-height: 1.8; white-space: pre-wrap; }
.draft { border: 1px dashed #c6e2ff; background: #f0f7ff; }
.resolved { border: 1px solid #b3e19d; background: #f0f9eb; }
.subtasks { padding-left: 22px; font-size: 13px; line-height: 2; }
.hit { background: #f8fafc; border-left: 3px solid #79bbff; padding: 8px 10px; margin: 6px 0; font-size: 12.5px; border-radius: 0 6px 6px 0; }
.note { color: #909399; font-size: 12px; margin-top: 2px; }
</style>
