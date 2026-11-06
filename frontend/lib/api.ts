import type {
  CalendarResponse,
  Category,
  ContentRequestDto,
  ContentResponseDto,
  HighlightRequestDto,
  HighlightResponseDto,
  LoginRequestDto,
  LoginResponseDto,
  MemberResponseDto,
  RegisterRequestDto,
  SearchResponse,
  StatisticsResponse,
} from '@/types'
import { clearSession, getAccessToken, getGeneration, getRefreshToken, setAccessToken } from '@/lib/authStore'

// 백엔드 주소. next.config.js가 NEXT_PUBLIC_API_URL을 검증·정규화(끝 슬래시 제거)해 빌드 시점에 번들에 박는다
// 런타임 환경변수로는 바꿀 수 없다. production 빌드에서 값이 없으면 빌드가 실패한다
const BASE_URL = process.env.NEXT_PUBLIC_API_URL as string

// 검색/저장/캘린더/인증 API는 next.config.js의 rewrites 프록시(/api/* → Spring)를 거친다 (같은 출처, 서버끼리 호출).
// 통계·하이라이트·contents filter/check는 BASE_URL로 브라우저가 백엔드를 직접 호출하므로 백엔드 CORS(APP_CORS_ALLOWED_ORIGINS)가 필요하다
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

// /api/auth/* 는 토큰을 붙이지 않고, 401이어도 재발급을 시도하지 않는다
// (로그인 401은 "아이디/비밀번호 불일치"이고, refresh 401은 "refresh 만료"라 재시도하면 무한 루프가 된다)
const AUTH_PATH_PREFIX = '/api/auth/'

function send(path: string, init: RequestInit | undefined, baseUrl: string, accessToken: string | null) {
  const headers = new Headers(init?.headers)
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (accessToken && !path.startsWith(AUTH_PATH_PREFIX)) headers.set('Authorization', `Bearer ${accessToken}`)
  return fetch(`${baseUrl}${path}`, { ...init, headers })
}

async function request<T>(path: string, init?: RequestInit, baseUrl: string = BASE_URL): Promise<T> {
  const tokenUsed = getAccessToken()
  let res = await send(path, init, baseUrl, tokenUsed)

  // 401이면 access 토큰을 재발급받아 원래 요청을 딱 1회만 다시 보낸다 (재시도 응답이 또 401이면 그대로 실패)
  if (res.status === 401 && !path.startsWith(AUTH_PATH_PREFIX) && (await renewAccessToken(tokenUsed))) {
    res = await send(path, init, baseUrl, getAccessToken())
  }

  if (!res.ok) throw new ApiError(res.status, await readBody(res), path)
  if (res.status === 204) return undefined as T
  return res.json()
}

// 401을 받은 요청이 쓴 토큰(tokenUsed)을 기준으로 재발급이 필요한지 판단한다
async function renewAccessToken(tokenUsed: string | null): Promise<boolean> {
  const current = getAccessToken()
  // 이 요청이 나간 뒤 다른 요청이 이미 재발급을 끝냈다면 새 토큰으로 재시도만 하면 된다 (refresh를 또 부르지 않는다)
  if (current !== null && current !== tokenUsed) return true
  return refreshAccessToken()
}

// single-flight: 동시에 여러 요청이 401을 받아도 refresh 호출은 한 번이고 나머지는 같은 Promise를 기다린다
let refreshInFlight: Promise<boolean> | null = null

export function refreshAccessToken(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = doRefresh().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

// 성공하면 true. 실패(refresh 없음/거절/네트워크 오류)면 토큰을 모두 지우고 false → AuthProvider가 로그인 화면으로 보낸다
// request()를 거치지 않고 fetch를 직접 써서 refresh 자신의 401이 다시 refresh를 부르는 일이 없다
async function doRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  const generation = getGeneration()
  if (!refreshToken) {
    clearSession()
    return false
  }
  try {
    const res = await fetch(`${PROXY_BASE_URL}${AUTH_PATH_PREFIX}refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!res.ok) throw new ApiError(res.status, null, `${AUTH_PATH_PREFIX}refresh`)
    const { accessToken } = (await res.json()) as { accessToken: string }
    // 기다리는 동안 로그아웃/재로그인이 있었다면 그 결과를 덮어쓰지 않는다
    if (generation !== getGeneration()) return false
    setAccessToken(accessToken)
    return true
  } catch {
    if (generation === getGeneration()) clearSession()
    return false
  }
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

// ─── 인증 ─────────────────────────────────────────────────────────────────────
// POST /api/auth/register → 201 { id, username }. 400(@Valid 맵), 409(아이디 중복)
export const register = (dto: RegisterRequestDto) =>
  request<MemberResponseDto>('/api/auth/register', { method: 'POST', body: JSON.stringify(dto) }, PROXY_BASE_URL)

// POST /api/auth/login → 200 { id, username, accessToken, refreshToken }. 401은 아이디/비밀번호 불일치(구분 없음)
export const login = (dto: LoginRequestDto) =>
  request<LoginResponseDto>('/api/auth/login', { method: 'POST', body: JSON.stringify(dto) }, PROXY_BASE_URL)

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
// GET /api/highlights?year=2026&category=BOOK
export const getHighlights = (year?: number, category?: Category) => {
  const params = new URLSearchParams()
  if (year) params.set('year', String(year))
  if (category && category !== 'ALL') params.set('category', category)
  return request<HighlightResponseDto[]>(`/api/highlights?${params}`)
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
