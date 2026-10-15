import type {
  CalendarResponse,
  Category,
  ContentRequestDto,
  ContentResponseDto,
  HighlightRequestDto,
  HighlightWithContent,
  StatisticsResponse,
  UnifiedSearchResponse,
} from '@/types'

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!res.ok) throw new Error(`API Error ${res.status}: ${path}`)
  if (res.status === 204) return undefined as T
  return res.json()
}

// ─── 캘린더 ───────────────────────────────────────────────────────────────────
// GET /api/calendar/activities?year=2026&month=4
export const getCalendarActivities = (year: number, month: number) =>
  request<CalendarResponse>(`/api/calendar/activities?year=${year}&month=${month}`)

// ─── 통계 ─────────────────────────────────────────────────────────────────────
// GET /api/statistics?year=2026&month=4
export const getMonthlyStatistics = (year: number, month: number) =>
  request<StatisticsResponse>(`/api/statistics?year=${year}&month=${month}`)

// ─── 검색 ─────────────────────────────────────────────────────────────────────
// GET /api/search?keyword=...&category=BOOK
export const searchContents = (keyword: string, category?: Category) => {
  const params = new URLSearchParams({ keyword })
  if (category) params.set('category', category)
  return request<UnifiedSearchResponse[]>(`/api/search?${params}`)
}

// ─── 콘텐츠 ──────────────────────────────────────────────────────────────────
// POST /api/contents
export const saveContent = (dto: ContentRequestDto) =>
  request<number>('/api/contents', { method: 'POST', body: JSON.stringify(dto) })

// GET /api/contents/filter?year=2026&category=BOOK
export const getFilteredContents = (year?: number, category?: Category) => {
  const params = new URLSearchParams()
  if (year) params.set('year', String(year))
  if (category) params.set('category', category)
  return request<ContentResponseDto[]>(`/api/contents/filter?${params}`)
}

// GET /api/contents/check?externalId=xxx&category=BOOK
export const checkDuplicate = (externalId: string, category: Category) =>
  request<boolean>(`/api/contents/check?externalId=${externalId}&category=${category}`)

// ─── 하이라이트 ───────────────────────────────────────────────────────────────
// GET /api/highlights?year=2026&category=BOOK  (백엔드 추가 예정 엔드포인트)
export const getHighlights = (year?: number, category?: Category) => {
  const params = new URLSearchParams()
  if (year) params.set('year', String(year))
  if (category && category !== 'ALL') params.set('category', category)
  return request<HighlightWithContent[]>(`/api/highlights?${params}`)
}

// POST /api/highlights
export const saveHighlight = (dto: HighlightRequestDto) =>
  request<number>('/api/highlights', { method: 'POST', body: JSON.stringify(dto) })

// PUT /api/highlights/:id
export const updateHighlight = (id: number, dto: HighlightRequestDto) =>
  request<number>(`/api/highlights/${id}`, { method: 'PUT', body: JSON.stringify(dto) })

// DELETE /api/highlights/:id
export const deleteHighlight = (id: number) =>
  request<void>(`/api/highlights/${id}`, { method: 'DELETE' })
