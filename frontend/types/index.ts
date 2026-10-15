// ─── Domain Enums ────────────────────────────────────────────────────────────
// 백엔드 Category enum과 1:1 대응
export type Category = 'BOOK' | 'MOVIE' | 'TV' | 'ANIME' | 'ANIME_MOVIE' | 'ANIME_TVA' | 'ALL'

// 백엔드 Status enum과 1:1 대응
export type Status = 'READING' | 'WATCHING' | 'COMPLETED' | 'WANT_to_WATCH' | 'STOPPED'

// ─── Response DTOs ────────────────────────────────────────────────────────────

// UnifiedSearchResponse.java
export interface UnifiedSearchResponse {
  title: string
  authorOrDirector: string
  releaseDate: string
  voteAverage: number | null
  thumbnailUrl: string
  category: Category
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

// ─── UI-only types ────────────────────────────────────────────────────────────

// 하이라이트 목록 조회용 (GET /api/highlights - 백엔드 추가 예정)
export interface HighlightWithContent {
  id: number
  text: string
  page?: number
  season?: number
  episode?: number
  timestamp?: string
  createdAt: string
  content: {
    id: number
    title: string
    category: Category
    thumbnailUrl?: string
  }
}
