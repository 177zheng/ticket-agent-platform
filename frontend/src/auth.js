// 登录态存取：token + 用户信息（含角色）保存在 localStorage
const KEY = 'tp_auth'

export function saveAuth(data) {
  localStorage.setItem(KEY, JSON.stringify(data))
}

export function getAuth() {
  try {
    return JSON.parse(localStorage.getItem(KEY))
  } catch {
    return null
  }
}

export function clearAuth() {
  localStorage.removeItem(KEY)
}

export const isAdmin = () => getAuth()?.role === 'ADMIN'
