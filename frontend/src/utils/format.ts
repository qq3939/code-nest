import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import relativeTime from 'dayjs/plugin/relativeTime'
import timezone from 'dayjs/plugin/timezone'
import utc from 'dayjs/plugin/utc'
import DOMPurify from 'dompurify'

dayjs.extend(utc)
dayjs.extend(timezone)
dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

// 后端统一用 Asia/Shanghai 的 LocalDateTime（不带时区），这里按北京时间解析，
// 否则浏览器不在东八区时"3 小时前"会算错（见 docs/pitfalls.md）
const SERVER_TZ = 'Asia/Shanghai'
const parse = (t: string) => dayjs.tz(t, SERVER_TZ)

/** 一周内显示"3 小时前"，更早的显示日期。 */
export function fromNow(t?: string | null): string {
  if (!t) return ''
  const d = parse(t)
  return dayjs().diff(d, 'day') < 7 ? d.fromNow() : d.tz(SERVER_TZ).format('YYYY-MM-DD')
}

export function formatTime(t?: string | null): string {
  return t ? parse(t).format('YYYY-MM-DD HH:mm') : ''
}

export function compact(n: number): string {
  if (n >= 10000) return `${(n / 10000).toFixed(1)}万`
  return String(n)
}

/**
 * 搜索高亮片段里只允许 <em>：后端已经转义了正文，这里再用 DOMPurify 兜底，
 * 防止 v-html 渲染出脚本（SPEC 9.15）。
 */
export function safeHighlight(html: string): string {
  return DOMPurify.sanitize(html, { ALLOWED_TAGS: ['em'], ALLOWED_ATTR: [] })
}

/** 头像缺省时用昵称首字。 */
export function initial(name?: string | null): string {
  return (name ?? '?').trim().charAt(0).toUpperCase() || '?'
}
