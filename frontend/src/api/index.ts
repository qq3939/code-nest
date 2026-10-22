// 接口清单与 SPEC 第 10 节一一对应
import { api } from './http'
import type {
  ActiveRank, AdminUser, ArticleDetail, ArticleListItem, ArticleSaveReq, AuditLog, Category, CommentVO, CursorPage,
  Dashboard, DeadLetter, FileVO, HotArticleRank, NotifyType, NotifyVO, PageResult, SearchHit, SensitiveWord, Strategy,
  Tag, TokenVO, UserBrief, UserProfile, UserVO,
} from '@/types'

export const authApi = {
  register: (username: string, password: string, nickname?: string) =>
    api.post<TokenVO>('/auth/register', { username, password, nickname }),
  login: (username: string, password: string) => api.post<TokenVO>('/auth/login', { username, password }),
  logout: (refreshToken: string) => api.post<void>('/auth/logout', { refreshToken }, { silent: true }),
}

export const userApi = {
  me: () => api.get<UserVO>('/users/me'),
  updateMe: (body: { nickname: string; bio?: string; avatarUrl?: string }) => api.put<UserVO>('/users/me', body),
  changePassword: (oldPassword: string, newPassword: string) =>
    api.put<void>('/users/me/password', { oldPassword, newPassword }),
  profile: (id: string) => api.get<UserProfile>(`/users/${id}`),
  articles: (id: string, page = 1, size = 10) =>
    api.get<PageResult<ArticleListItem>>(`/users/${id}/articles`, { page, size }),
  collections: (id: string, page = 1, size = 10) =>
    api.get<PageResult<ArticleListItem>>(`/users/${id}/collections`, { page, size }),
  followers: (id: string, page = 1, size = 20) => api.get<PageResult<UserBrief>>(`/users/${id}/followers`, { page, size }),
  followings: (id: string, page = 1, size = 20) =>
    api.get<PageResult<UserBrief>>(`/users/${id}/followings`, { page, size }),
  follow: (id: string) => api.post<void>(`/users/${id}/follow`),
  unfollow: (id: string) => api.del<void>(`/users/${id}/follow`),
}

export const fileApi = {
  uploadImage(file: File) {
    const form = new FormData()
    form.append('file', file)
    return api.post<FileVO>('/files/images', form, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
}

export const articleApi = {
  list: (params: { sort: 'latest' | 'hot'; categoryId?: string; tagId?: string; page: number; size?: number }) =>
    api.get<PageResult<ArticleListItem>>('/articles', params),
  detail: (id: string) => api.get<ArticleDetail>(`/articles/${id}`),
  forEdit: (id: string) => api.get<ArticleDetail>(`/articles/${id}/edit`),
  create: (body: ArticleSaveReq) => api.post<{ id: string }>('/articles', body),
  update: (id: string, body: ArticleSaveReq) => api.put<{ status: string }>(`/articles/${id}`, body),
  saveDraft: (id: string, body: ArticleSaveReq) => api.put<void>(`/articles/${id}/draft`, body, { silent: true }),
  remove: (id: string) => api.del<void>(`/articles/${id}`),
  submitToken: () => api.get<{ token: string; expiresIn: number }>('/articles/submit-token'),
  publish: (id: string, token: string, body?: ArticleSaveReq) =>
    api.post<{ status: string }>(`/articles/${id}/publish`, body ?? null, { headers: { 'Idempotency-Token': token } }),
  like: (id: string) => api.post<void>(`/articles/${id}/like`),
  unlike: (id: string) => api.del<void>(`/articles/${id}/like`),
  collect: (id: string) => api.post<void>(`/articles/${id}/collect`),
  uncollect: (id: string) => api.del<void>(`/articles/${id}/collect`),
  comments: (id: string, params: { parentId?: string; cursor?: string | null; size?: number }) =>
    api.get<CursorPage<CommentVO>>(`/articles/${id}/comments`, params),
  comment: (id: string, content: string, replyCommentId?: string) =>
    api.post<CommentVO>(`/articles/${id}/comments`, { content, replyCommentId }),
}

export const commentApi = {
  remove: (id: string) => api.del<void>(`/comments/${id}`),
  like: (id: string) => api.post<void>(`/comments/${id}/like`),
  unlike: (id: string) => api.del<void>(`/comments/${id}/like`),
}

export const metaApi = {
  categories: () => api.get<Category[]>('/categories'),
  tags: (keyword?: string) => api.get<Tag[]>('/tags', { keyword }),
}

export const feedApi = {
  read: (cursor?: string | null, size = 10) => api.get<CursorPage<ArticleListItem>>('/feed', { cursor, size }),
}

export const notifyApi = {
  list: (type?: NotifyType | '', cursor?: string | null, size = 20) =>
    api.get<CursorPage<NotifyVO>>('/notifications', { type: type || undefined, cursor, size }),
  unread: () => api.get<{ total: number }>('/notifications/unread-count', undefined, { silent: true }),
  markRead: (ids: string[] | null, all = false) => api.post<void>('/notifications/read', { ids, all }),
}

export const rankApi = {
  active: (period: 'day' | 'week' | 'month', limit = 20) => api.get<ActiveRank[]>('/rank/active', { period, limit }),
  hotArticles: (limit = 20) => api.get<HotArticleRank[]>('/rank/hot-articles', { limit }),
}

export const searchApi = {
  search: (q: string, page = 1, size = 10) => api.get<PageResult<SearchHit>>('/search', { q, page, size }),
}

export const adminApi = {
  dashboard: () => api.get<Dashboard>('/admin/dashboard'),
  articles: (params: { status?: string; keyword?: string; page: number; size?: number }) =>
    api.get<PageResult<ArticleListItem>>('/admin/articles', params),
  articleAction: (id: string, action: 'approve' | 'reject' | 'offline' | 'restore', reason?: string) =>
    api.put<void>(`/admin/articles/${id}/${action}`, { reason }),
  users: (params: { keyword?: string; page: number; size?: number }) =>
    api.get<PageResult<AdminUser>>('/admin/users', params),
  ban: (id: string, reason?: string) => api.put<void>(`/admin/users/${id}/ban`, { reason }),
  unban: (id: string) => api.put<void>(`/admin/users/${id}/unban`, {}),
  categories: () => api.get<Category[]>('/admin/categories'),
  createCategory: (name: string, sort: number) => api.post<Category>('/admin/categories', { name, sort }),
  updateCategory: (id: string, name: string, sort: number) => api.put<Category>(`/admin/categories/${id}`, { name, sort }),
  deleteCategory: (id: string) => api.del<void>(`/admin/categories/${id}`),
  tags: (params: { keyword?: string; page: number; size?: number }) => api.get<PageResult<Tag>>('/admin/tags', params),
  mergeTags: (sourceIds: string[], targetId: string) => api.post<void>('/admin/tags/merge', { sourceIds, targetId }),
  deleteTag: (id: string) => api.del<void>(`/admin/tags/${id}`),
  words: (params: { keyword?: string; page: number; size?: number }) =>
    api.get<PageResult<SensitiveWord>>('/admin/sensitive-words', params),
  createWord: (body: Omit<SensitiveWord, 'id'>) => api.post<SensitiveWord>('/admin/sensitive-words', body),
  updateWord: (id: string, body: Omit<SensitiveWord, 'id'>) => api.put<SensitiveWord>(`/admin/sensitive-words/${id}`, body),
  deleteWord: (id: string) => api.del<void>(`/admin/sensitive-words/${id}`),
  deadLetters: (params: { status?: string; page: number; size?: number }) =>
    api.get<PageResult<DeadLetter>>('/admin/dead-letters', params),
  replay: (id: string) => api.post<void>(`/admin/dead-letters/${id}/replay`),
  ignore: (id: string) => api.post<void>(`/admin/dead-letters/${id}/ignore`),
  auditLogs: (params: { action?: string; page: number; size?: number }) =>
    api.get<PageResult<AuditLog>>('/admin/audit-logs', params),
  reindex: () => api.post<Record<string, number>>('/admin/search/reindex'),
  strategies: () => api.get<Strategy>('/admin/dev/strategies', undefined, { silent: true }),
  updateStrategies: (body: Partial<Strategy>) => api.put<Strategy>('/admin/dev/strategies', body),
}
