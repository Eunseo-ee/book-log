// ─── Domain Enums ────────────────────────────────────────────────────────────
// 백엔드 Category enum과 1:1 대응
export type Category = 'BOOK' | 'MOVIE' | 'TV' | 'ANIME' | 'ANIME_MOVIE' | 'ANIME_TVA' | 'ALL'

// 백엔드 Status enum과 1:1 대응
export type Status = 'READING' | 'WATCHING' | 'COMPLETED' | 'WANT_to_WATCH' | 'STOPPED'

// ─── Response DTOs ────────────────────────────────────────────────────────────

// UnifiedSearchResponse.java
export interface UnifiedSearchResponse {
  externalId: string | null   // 책: ISBN13(없으면 ISBN10), 영화/드라마/애니: TMDB id
  title: string
  authorOrDirector: string
  releaseDate: string
  voteAverage: number | null
  thumbnailUrl: string | null
  category: Category
  isSaved?: boolean | null    // 응답에 없거나 null이면 false로 취급
}

// SearchResponse.java — GET /api/search 응답 (currentPage는 백엔드가 채우지 않아 항상 0)
export interface SearchResponse {
  results: UnifiedSearchResponse[]
  currentPage: number
  totalPages: number
  totalResults: number
}

// ErrorResponse.java — GlobalExceptionHandler의 400/500 응답
export interface ErrorResponse {
  status: number
  message: string
}

// ContentResponseDto.java
export interface ContentResponseDto {
  id: number
  title: string
  author: string
  category: Category
  viewDate: string       // LocalDate → ISO string "2026-04-12"
  rating: number | null
  imgUrl: string | null
}

// HighlightResponseDto.java — GET /api/highlights 응답 (부모 콘텐츠 정보가 평평하게 펼쳐져 옴)
// 선택 필드는 값이 없으면 null로 온다. content_id FK에 NOT NULL이 없어 content* 도 null 가능
export interface HighlightResponseDto {
  id: number
  text: string
  page: number | null
  season: number | null
  episode: number | null
  timestamp: string | null
  createdAt: string               // LocalDateTime → "2026-10-21T13:05:12.345" (시간대 없음)
  contentId: number | null
  contentTitle: string | null
  contentCategory: Category | null
}

// CalendarResponse.java
export interface CalendarResponse {
  year: number
  month: number
  days: DayActivity[]
}
export interface DayActivity {
  day: number
  thumbnail: string | null  // 사이드바 미니캘린더는 null
  count: number | null
}

// StatisticsResponse.java
export interface StatisticsResponse {
  targetMonth: string
  categoryStats: CategoryStat[]
  genreStats: GenreStat[]
  averageRating: number | null
  activity: ActivityComparison
  mostActiveDay: string
  topCategory: string
  tasteAnalysis: string
}
export interface CategoryStat { category: string; count: number }
export interface GenreStat    { genre: string;    count: number }
export interface ActivityComparison {
  currentCount: number
  previousCount: number
  growthRate: number
}

// ─── Request DTOs ─────────────────────────────────────────────────────────────

// ContentRequestDto.java
export interface ContentRequestDto {
  title: string
  authorOrDirector?: string
  thumbnailUrl?: string
  externalId?: string
  category: Category
  genre?: string
  viewDate: string       // "YYYY-MM-DD"
  rating?: number
  comment?: string
  status: Status
}

// HighlightRequestDto.java
export interface HighlightRequestDto {
  text: string
  contentId: number
  page?: number
  season?: number
  episode?: number
  timestamp?: string    // "01:23:45"
}
