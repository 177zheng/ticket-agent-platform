<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat">
            <div class="num" :style="{ color: card.color }">{{ card.value }}</div>
            <div class="label">{{ card.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>工单状态分布</template>
          <div ref="pieRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>工单类别分布（Agent 分诊结果）</template>
          <div ref="barRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import api from '../api'
import { STATUS_LABEL } from '../constants'

const pieRef = ref(null)
const barRef = ref(null)
const cards = ref([
  { label: '总工单', value: 0, color: '#409eff' },
  { label: '已解决', value: 0, color: '#67c23a' },
  { label: '待人工审核', value: 0, color: '#e6a23c' },
  { label: '转人工 / 失败', value: 0, color: '#f56c6c' }
])
let pie, bar

async function refresh() {
  try {
    const s = await api.get('/tickets/stats')
    cards.value[0].value = s.total
    cards.value[1].value = s.byStatus.RESOLVED || 0
    cards.value[2].value = s.byStatus.HUMAN_REVIEW || 0
    cards.value[3].value = (s.byStatus.ESCALATED || 0) + (s.byStatus.FAILED || 0)

    pie.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie', radius: ['40%', '65%'], center: ['50%', '45%'],
        label: { formatter: '{b}: {c}' },
        data: Object.entries(s.byStatus)
          .filter(([, v]) => v > 0)
          .map(([k, v]) => ({ name: STATUS_LABEL[k] || k, value: v }))
      }]
    })

    const cat = s.byCategory || {}
    bar.setOption({
      tooltip: {},
      grid: { left: 90, right: 20, top: 10, bottom: 24 },
      xAxis: { type: 'value', minInterval: 1 },
      yAxis: { type: 'category', data: Object.keys(cat) },
      series: [{ type: 'bar', barWidth: 16, itemStyle: { color: '#409eff', borderRadius: 4 }, data: Object.values(cat) }]
    })
  } catch { /* 后端未启动时静默 */ }
}

const onResize = () => { pie?.resize(); bar?.resize() }
onMounted(() => {
  pie = echarts.init(pieRef.value)
  bar = echarts.init(barRef.value)
  window.addEventListener('resize', onResize)
  refresh()
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  pie?.dispose(); bar?.dispose()
})
</script>

<style scoped>
.stat { text-align: center; padding: 8px 0; }
.num { font-size: 34px; font-weight: 700; }
.label { color: #909399; margin-top: 4px; }
.chart { height: 320px; }
</style>
