import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from './views/Dashboard.vue'
import Tickets from './views/Tickets.vue'
import Knowledge from './views/Knowledge.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: Dashboard, meta: { title: '仪表盘' } },
    { path: '/tickets', name: 'tickets', component: Tickets, meta: { title: '工单管理' } },
    { path: '/knowledge', name: 'knowledge', component: Knowledge, meta: { title: '知识库' } }
  ]
})

export default router
