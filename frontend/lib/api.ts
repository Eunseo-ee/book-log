import type {
  CalendarResponse,
  Category,
  ContentRequestDto,
  ContentResponseDto,
  HighlightRequestDto,
  HighlightWithContent,
  SearchResponse,
  StatisticsResponse,
} from '@/types'

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'

// 브라우저에서 호출하는 검색/저장/캘린더 API는 next.config.js의 rewrites 프록시(/api/* → Spring)를 거친다.
// 같은 출처 요청이라 백엔드 CORS 설정이 필요 없다. (백엔드 @CrossOrigin은 StatisticsController에만 있음)
const PROXY_BASE_URL = ''

// 실패 응답의 상태 코드와 본문을 함께 들고 있는 에러
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: unknown,
    path: string,
  ) {
    super(`API Error ${status}: ${path}`)
    this.name = 'ApiError'
  }
}

async function request<T>(path: string, init?: RequestInit, baseUrl: string = BASE_URL): Promise<T> {
  const res = await fetch(`${baseUrl}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!res.ok) throw new ApiError(res.status, await readBody(res), path)
  if (res.status === 204) return undefined as T
  return res.json()
}

async function readBody(res: Response): Promise<unknown> {
  const text = await res.text().catch(() => '')
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

// 에러를 화면에 보여줄 문구 목록으로 변환
// - ErrorResponse { status, message }: 중복 저장, 평점/미래 날짜 검증 실패, 500 등
// - @Valid 실패: { 필드명: 메시지 } 맵
export function toErrorMessages(err: unknown): string[] {
  if (err instanceof ApiError) {
    const body = err.body
    if (body && typeof body === 'object') {
      const message = (body as { message?: unknown }).message
      if (typeof message === 'string') return [message]

      const fieldMessages = Object.values(body).filter((v): v is string => typeof v === 'string')
      if (fieldMessages.length > 0) return fieldMessages
    }
    return [`요청에 실패했습니다. (HTTP ${err.status})`]
  }
  return ['서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.']
}

// ─── 캘린더 ───────────────────────────────────────────────────────────────────
// GET /api/calendar/activities?year=2026&month=4
export const getCalendarActivities = (year: number, month: number) =>
  request<CalendarResponse>(`/api/calendar/activities?year=${year}&month=${month}`, undefined, PROXY_BASE_URL)

// ─── 통계 ─────────────────────────────────────────────────────────────────────
// GET /api/statistics?year=2026&month=4
export const getMonthlyStatistics = (year: number, month: number) =>
  request<StatisticsResponse>(`/api/statistics?year=${year}&month=${month}`)

// ─── 검색 ─────────────────────────────────────────────────────────────────────
// GET /api/search?category=BOOK&query=...&page=1
// 각 결과의 isSaved는 백엔드가 캐시 바깥에서 매 요청마다 채운다
export const searchContents = (category: Category, query: string, page: number, signal?: AbortSignal) => {
  const params = new URLSearchParams({ category, query, page: String(page) })
  return request<SearchResponse>(`/api/search?${params}`, { signal }, PROXY_BASE_URL)
}

// ─── 콘텐츠 ──────────────────────────────────────────────────────────────────
// POST /api/contents → 201 Created, 본문은 저장된 id
export const saveContent = (dto: ContentRequestDto) =>
  request<number>('/api/contents', { method: 'POST', body: JSON.stringify(dto) }, PROXY_BASE_URL)

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
