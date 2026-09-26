import axios from 'axios'
import { getAuth, clearAuth } from './auth'

const api = axios.create({ baseURL: '/api', timeout: 15000 })

// 每个请求自动带上 JWT
api.interceptors.request.use(config => {
  const auth = getAuth()
  if (auth?.token) config.headers.Authorization = `Bearer ${auth.token}`
  return config
})

// 统一解包与错误处理；401 一律清登录态跳登录页
api.interceptors.response.use(
  resp => resp.data,
  err => {
    const status = err.response?.status
    const msg = err.response?.data?.error || err.message || '请求失败'
    if (status === 401 && !err.config?.url?.includes('/auth/')) {
      clearAuth()
      location.href = '/login'
    }
    return Promise.reject(new Error(msg))
  }
)

export default api
