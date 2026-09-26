import { createRouter, createWebHistory } from 'vue-router'
import { getAuth } from './auth'
import Dashboard from './views/Dashboard.vue'
import Tickets from './views/Tickets.vue'
import Knowledge from './views/Knowledge.vue'
import Login from './views/Login.vue'
import Register from './views/Register.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: Dashboard, meta: { title: '仪表盘' } },
    { path: '/tickets', name: 'tickets', component: Tickets, meta: { title: '工单管理' } },
    { path: '/knowledge', name: 'knowledge', component: Knowledge, meta: { title: '知识库', adminOnly: true } },
    { path: '/login', name: 'login', component: Login, meta: { title: '登录', public: true } },
    { path: '/register', name: 'register', component: Register, meta: { title: '注册', public: true } }
  ]
})

// 路由守卫：未登录 → 登录页；已登录访问登录页 → 首页；员工访问管理员页 → 首页
router.beforeEach(to => {
  const auth = getAuth()
  if (!to.meta.public && !auth) return '/login'
  if (to.meta.public && auth) return '/'
  if (to.meta.adminOnly && auth?.role !== 'ADMIN') return '/'
})

export default router
