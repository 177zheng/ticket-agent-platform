// 工单状态与优先级的展示映射（前端唯一维护点）
export const STATUS_LABEL = {
  NEW: '待处理', TRIAGING: '分类中', RETRIEVING: '检索中', DRAFTING: '生成回复中',
  HUMAN_REVIEW: '待人工审核', RESOLVED: '已解决', ESCALATED: '已转人工', FAILED: '处理失败'
}

export const STATUS_TAG = {
  NEW: 'info', TRIAGING: 'primary', RETRIEVING: 'primary', DRAFTING: 'primary',
  HUMAN_REVIEW: 'warning', RESOLVED: 'success', ESCALATED: 'danger', FAILED: 'danger'
}

export const PRIORITY_LABEL = { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }

export const isTerminal = s => s === 'RESOLVED'

export const fmtTime = t => (t ? String(t).replace('T', ' ').slice(0, 19) : '-')
