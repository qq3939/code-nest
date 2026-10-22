// 与后端 project-a-api 模块里的 VO / DTO 一一对应。
// 后端把所有 Long 序列化成字符串（避免 JS 丢精度，见 docs/pitfalls.md P-001），所以 ID 一律是 string。

export interface Result<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
  hasMore: boolean
}

export interface UserVO {
  id: string
  username: string
  nickname: string
  avatarUrl: string | null
  bio: string | null
  role: 'USER' | 'ADMIN'
}

export interface UserBrief {
  id: string
  nickname: string
  avatarUrl: string | null
  bio: string | null
}

export interface TokenVO {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserVO
}

export interface UserStat {
  articleCount: number
  followerCount: number
  followingCount: number
  likeReceived: number
  readReceived: number
}

export interface UserProfile {
  user: UserBrief
  stat: UserStat
  followed: boolean
  joinTime: string
}

export interface Category {
  id: string
  name: string
  sort: number
}

export interface Tag {
  id: string
  name: string
}

export interface ArticleCounts {
  read: number
  like: number
  collect: number
  comment: number
}

export type ArticleStatus = 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'OFFLINE'

export interface ArticleListItem {
  id: string
  title: string
  summary: string
  coverUrl: string | null
  category: Category | null
  tags: Tag[]
  author: UserBrief
  status: ArticleStatus
  publishTime: string | null
  updateTime: string
  counts: ArticleCounts
}

export interface ArticleDetail extends ArticleListItem {
  content: string
  liked: boolean
  collected: boolean
  authorFollowed: boolean
}

export interface ArticleSaveReq {
  title?: string
  summary?: string
  coverUrl?: string
  categoryId?: string | null
  tags?: string[]
  content?: string
}

export interface CommentVO {
  id: string
  articleId: string
  parentId: string
  user: UserBrief
  replyToUser: UserBrief | null
  content: string
  likeCount: number
  liked: boolean
  deleted: boolean
  createTime: string
  replies: CommentVO[]
  replyCount: number
}

export type NotifyType = 'COMMENT' | 'REPLY' | 'LIKE' | 'COLLECT' | 'FOLLOW' | 'SYSTEM'

export interface NotifyVO {
  id: string
  type: NotifyType
  sender: UserBrief | null
  targetType: string
  targetId: string
  content: string
  read: boolean
  createTime: string
}

export interface ActiveRank {
  rank: number
  user: UserBrief
  score: number
}

export interface HotArticleRank {
  rank: number
  article: ArticleListItem
  score: number
}

export interface SearchHit {
  id: string
  title: string
  summary: string
  contentSnippet: string
  tags: string[]
  authorName: string
  authorId: string
  publishTime: string
}

export interface FileVO {
  url: string
  objectName: string
}

export interface DailyStat {
  date: string
  dau: number
  newUsers: number
  newArticles: number
  reads: number
  likes: number
}

export interface Dashboard {
  days: DailyStat[]
  totalUsers: number
  totalArticles: number
  pendingArticles: number
}

export interface AdminUser {
  id: string
  username: string
  nickname: string
  avatarUrl: string | null
  role: string
  status: 'NORMAL' | 'BANNED'
  createTime: string
}

export interface SensitiveWord {
  id: string
  word: string
  level: 'BLOCK' | 'REVIEW' | 'REPLACE'
  enabled: boolean
}

export interface DeadLetter {
  id: string
  msgId: string
  queue: string
  payload: string
  error: string
  failedTime: string
  status: 'NEW' | 'REPLAYED' | 'IGNORED'
}

export interface AuditLog {
  id: string
  admin: UserBrief | null
  action: string
  targetType: string
  targetId: string
  reason: string
  createTime: string
}

export interface Strategy {
  countStrategy: string
  feedMode: string
  cacheMode: string
}
