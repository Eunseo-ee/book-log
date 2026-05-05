import type { Category, Status } from '@/types'

export const CATEGORY_LABEL: Record<string, string> = {
  BOOK: '책',
  MOVIE: '영화',
  TV: '드라마',
  ANIME: '애니',
  ANIME_MOVIE: '애니 극장판',
  ANIME_TVA: '애니 TV',
  ALL: '전체',
}

export const STATUS_LABEL: Record<Status, string> = {
  READING: '읽는 중',
  WATCHING: '보는 중',
  COMPLETED: '다 봄',
  WANT_to_WATCH: '다시 보고 싶음',
  STOPPED: '중단',
}

// Tailwind 클래스 — category badge 색상
export const CATEGORY_BADGE: Record<string, string> = {
  BOOK:        'bg-violet-100 text-violet-800',
  MOVIE:       'bg-orange-100 text-orange-800',
  TV:          'bg-orange-100 text-orange-800',
  ANIME:       'bg-emerald-100 text-emerald-800',
  ANIME_MOVIE: 'bg-emerald-100 text-emerald-800',
  ANIME_TVA:   'bg-emerald-100 text-emerald-800',
}

// highlight 카드 왼쪽 accent 색
export const CATEGORY_ACCENT: Record<string, string> = {
  BOOK:        'border-violet-500',
  MOVIE:       'border-orange-500',
  TV:          'border-orange-500',
  ANIME:       'border-emerald-500',
  ANIME_MOVIE: 'border-emerald-500',
  ANIME_TVA:   'border-emerald-500',
}

// 미니 캘린더 dot 색
export const CATEGORY_DOT: Record<string, string> = {
  BOOK:        'bg-violet-500',
  MOVIE:       'bg-orange-500',
  TV:          'bg-orange-500',
  ANIME:       'bg-emerald-500',
  ANIME_MOVIE: 'bg-emerald-500',
  ANIME_TVA:   'bg-emerald-500',
}

export const CATEGORY_FILTER_TABS: { label: string; value: Category | 'ALL' }[] = [
  { label: '전체',        value: 'ALL'   },
  { label: '책',          value: 'BOOK'  },
  { label: '영화/드라마', value: 'MOVIE' },
  { label: '애니',        value: 'ANIME' },
]

export const YEAR_OPTIONS = [2023, 2024, 2025, 2026]
