<template>
  <div>
    <el-card shadow="never" style="margin-bottom:16px">
      <div class="toolbar">
        <el-input v-model="query" placeholder="输入关键词测试知识库检索（模拟检索 Agent 的召回）" clearable
                  style="max-width:420px" @keyup.enter="doSearch">
          <template #append>
            <el-button @click="doSearch">检索</el-button>
          </template>
        </el-input>
        <el-button type="primary" @click="dlg = true">+ 导入运维手册</el-button>
      </div>
      <template v-if="hits.length">
        <div class="hit" v-for="(h, i) in hits" :key="i">
          <b>《{{ h.sourceTitle }}》</b><br>{{ h.snippet }}
        </div>
      </template>
    </el-card>

    <el-card shadow="never">
      <template #header>运维手册（{{ manuals.length }} 篇，共 {{ totalChunks }} 块）</template>
      <el-table :data="manuals" :header-cell-style="{ background: '#f8fafc' }">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="title" label="手册标题" min-width="260" />
        <el-table-column prop="chunks" label="知识块数" width="110" />
        <el-table-column label="入库时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg" title="导入运维手册" width="600px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="例：《视频会议系统故障处理手册》" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="form.content" type="textarea" :rows="10"
                    placeholder="粘贴手册全文，系统会自动按 400 字/块、80 字重叠切块入库…" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="ingest">导入并切块</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { fmtTime } from '../constants'

const manuals = ref([])
const hits = ref([])
const query = ref('')
const dlg = ref(false)
const submitting = ref(false)
const form = ref({ title: '', content: '' })
const totalChunks = computed(() => manuals.value.reduce((s, m) => s + m.chunks, 0))

async function load() {
  try {
    manuals.value = await api.get('/knowledge/manuals')
  } catch (e) {
    ElMessage.error('加载失败：' + e.message)
  }
}

async function doSearch() {
  if (!query.value.trim()) return
  try {
    hits.value = await api.get('/knowledge/search', { params: { q: query.value.trim(), k: 5 } })
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function ingest() {
  if (!form.value.title || !form.value.content) {
    ElMessage.warning('标题和内容都要填')
    return
  }
  submitting.value = true
  try {
    const r = await api.post('/knowledge/manual', form.value)
    ElMessage.success(`《${r.title}》已导入，切成 ${r.chunks} 块`)
    dlg.value = false
    form.value = { title: '', content: '' }
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.hit { background: #f0f9eb; border-left: 3px solid #95d475; padding: 8px 10px; margin: 6px 0; font-size: 12.5px; border-radius: 0 6px 6px 0; }
</style>
