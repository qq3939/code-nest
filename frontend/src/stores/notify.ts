import { fetchEventSource } from '@microsoft/fetch-event-source'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { notifyApi } from '@/api'
import { refreshAccessToken } from '@/api/http'
import { tokenStore } from '@/utils/auth'

class FatalSseError extends Error {}

/**
 * 未读数角标。用 fetch-event-source 而不是原生 EventSource：
 * 原生的不能带 Authorization 请求头（SPEC 9.13）。断线后库会自动重连，401 时先刷新令牌再连。
 */
export const useNotifyStore = defineStore('notify', () => {
  const unread = ref(0)
  let controller: AbortController | null = null

  async function refreshCount() {
    if (!tokenStore.access()) return
    try {
      unread.value = (await notifyApi.unread()).total
    } catch {
      // 角标拿不到不影响页面
    }
  }

  function connect() {
    disconnect()
    if (!tokenStore.access()) return
    controller = new AbortController()
    const signal = controller.signal
    fetchEventSource('/api/notifications/stream', {
      signal,
      // 默认页面隐藏时会断开、显示时重连；未读数需要后台也保持更新，所以关掉
      openWhenHidden: true,
      headers: { Authorization: `Bearer ${tokenStore.access()}` },
      async onopen(resp) {
        if (resp.ok) return
        if (resp.status === 401) {
          // 请求头在建连时就固定了，所以刷新令牌后要用新令牌重新建一个连接，而不是让库原样重试
          try {
            await refreshAccessToken()
          } catch {
            throw new FatalSseError('refresh failed')
          }
          setTimeout(connect, 0)
          throw new FatalSseError('reconnect with new token')
        }
        throw new FatalSseError(`sse open failed: ${resp.status}`)
      },
      onmessage(ev) {
        if (ev.event === 'unread') {
          const n = Number(ev.data)
          if (!Number.isNaN(n)) unread.value = n
        }
      },
      onerror(err) {
        if (err instanceof FatalSseError) throw err
        // 网络断开等可恢复错误：3 秒后自动重连
        return 3000
      },
    }).catch(() => {
      // 致命错误不再重连，退回到页面刷新时拉一次未读数
    })
  }

  function disconnect() {
    controller?.abort()
    controller = null
  }

  return { unread, refreshCount, connect, disconnect }
})
