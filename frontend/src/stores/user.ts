import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi, userApi } from '@/api'
import type { TokenVO, UserVO } from '@/types'
import { tokenStore } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const user = ref<UserVO | null>(tokenStore.user())
  const loggedIn = computed(() => user.value != null && !!tokenStore.access())
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  function applyToken(t: TokenVO) {
    tokenStore.save(t)
    user.value = t.user
  }

  async function login(username: string, password: string) {
    applyToken(await authApi.login(username, password))
  }

  async function register(username: string, password: string, nickname?: string) {
    applyToken(await authApi.register(username, password, nickname))
  }

  async function logout() {
    const refresh = tokenStore.refresh()
    if (refresh) {
      // 让后端作废 refresh token；失败也无所谓，本地照样清掉
      await authApi.logout(refresh).catch(() => undefined)
    }
    clear()
  }

  function clear() {
    tokenStore.clear()
    user.value = null
  }

  async function reload() {
    if (!tokenStore.access()) return
    const me = await userApi.me()
    user.value = me
    tokenStore.saveUser(me)
  }

  function setUser(u: UserVO) {
    user.value = u
    tokenStore.saveUser(u)
  }

  return { user, loggedIn, isAdmin, login, register, logout, clear, reload, setUser }
})
