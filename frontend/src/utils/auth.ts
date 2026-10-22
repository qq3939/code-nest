// 令牌只存 localStorage、只放请求头，不用 Cookie 鉴权，所以不需要 CSRF 令牌（SPEC 9.15）。
// 单独放一个模块，axios 拦截器和 Pinia store 都依赖它，避免两者互相 import。
import type { TokenVO, UserVO } from '@/types'

const ACCESS = 'pa.access'
const REFRESH = 'pa.refresh'
const USER = 'pa.user'

function read(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function write(key: string, value: string | null) {
  try {
    if (value == null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {
    // 隐私模式等情况下 localStorage 不可用，退化为仅本次页面有效
  }
}

export const tokenStore = {
  access: () => read(ACCESS),
  refresh: () => read(REFRESH),
  user(): UserVO | null {
    const raw = read(USER)
    if (!raw) return null
    try {
      return JSON.parse(raw) as UserVO
    } catch {
      return null
    }
  },
  save(t: TokenVO) {
    write(ACCESS, t.accessToken)
    write(REFRESH, t.refreshToken)
    write(USER, JSON.stringify(t.user))
  },
  saveUser(u: UserVO) {
    write(USER, JSON.stringify(u))
  },
  clear() {
    write(ACCESS, null)
    write(REFRESH, null)
    write(USER, null)
  },
}
