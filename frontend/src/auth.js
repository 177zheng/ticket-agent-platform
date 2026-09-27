// 登录态：localStorage 持久化 + Vue 响应式共享（个人信息修改后顶栏即时刷新）
import { ref } from 'vue'

const KEY = 'tp_auth'

function read() {
  try {
    return JSON.parse(localStorage.getItem(KEY))
  } catch {
    return null
  }
}

const authState = ref(read())

export function getAuth() {
  return authState.value
}

/** 登录/注册后调用：落盘并更新共享状态 */
export function setAuth(data) {
  localStorage.setItem(KEY, JSON.stringify(data))
  authState.value = data
}

/** 修改个人信息后调用：只更新部分字段（token 不变） */
export function patchAuth(patch) {
  const next = { ...(authState.value || {}), ...patch }
  localStorage.setItem(KEY, JSON.stringify(next))
  authState.value = next
}

export function clearAuth() {
  localStorage.removeItem(KEY)
  authState.value = null
}

export const isAdmin = () => authState.value?.role === 'ADMIN'
