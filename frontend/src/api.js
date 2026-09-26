import axios from 'axios'

const api = axios.create({ baseURL: '/api', timeout: 15000 })

// 统一解包与错误提示
api.interceptors.response.use(
  resp => resp.data,
  err => {
    const msg = err.response?.data?.error || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

export default api
