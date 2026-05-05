'use client'

import { useState } from 'react'
import { useHighlights } from '@/hooks/useHighlights'
import { deleteHighlight } from '@/lib/api'
import HighlightCard from './HighlightCard'
import type { Category, HighlightWithContent } from '@/types'
import { CATEGORY_FILTER_TABS, YEAR_OPTIONS } from '@/lib/constants'

export default function HighlightList() {
  const [year, setYear]         = useState<number | undefined>(undefined)
  const [category, setCategory] = useState<Category | 'ALL'>('ALL')
  const [search, setSearch]     = useState('')

  // GET /api/highlights?year=...&category=...
  const { highlights, loading, refetch } = useHighlights(
    year,
    category === 'ALL' ? undefined : category,
  )

  async function handleDelete(id: number) {
    if (!confirm('정말 삭제할까요?')) return
    await deleteHighlight(id)  // DELETE /api/highlights/:id
    refetch()
  }

  // 검색은 클라이언트 필터 (추후 서버 검색 API 추가 가능)
  const filtered = search.trim()
    ? highlights.filter(
        (h) =>
          h.text.includes(search) ||
          h.content.title.includes(search),
      )
    : highlights

  return (
    <div className="flex flex-col gap-5">
      {/* 헤더 */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <h1 className="text-xl font-semibold text-neutral-900 dark:text-white">
          나만의 문장 모음집
        </h1>
        <input
          type="text"
          placeholder="문장 또는 작품 검색..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full sm:w-56 px-3 py-2 text-sm bg-neutral-100 dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-xl outline-none focus:ring-2 focus:ring-violet-500 placeholder:text-neutral-400 text-neutral-800 dark:text-neutral-200"
        />
      </div>

      {/* 필터 바 */}
      <div className="flex flex-wrap items-center gap-2">
        {/* 카테고리 탭 */}
        <div className="flex gap-1 bg-neutral-100 dark:bg-neutral-900 rounded-xl p-1">
          {CATEGORY_FILTER_TABS.map(({ label, value }) => (
            <button
              key={value}
              onClick={() => setCategory(value)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                category === value
                  ? 'bg-white dark:bg-neutral-800 text-neutral-900 dark:text-white shadow-sm'
                  : 'text-neutral-500 hover:text-neutral-700 dark:hover:text-neutral-300'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {/* 연도 선택 — 백엔드 ?year= 파라미터 */}
        <select
          value={year ?? ''}
          onChange={(e) => setYear(e.target.value ? Number(e.target.value) : undefined)}
          className="px-3 py-1.5 text-xs bg-neutral-100 dark:bg-neutral-900 border-0 rounded-xl text-neutral-600 dark:text-neutral-400 outline-none cursor-pointer"
        >
          <option value="">전체 연도</option>
          {YEAR_OPTIONS.map((y) => (
            <option key={y} value={y}>{y}년</option>
          ))}
        </select>

        <span className="ml-auto text-xs text-neutral-400">
          {filtered.length}개
        </span>
      </div>

      {/* 목록 */}
      {loading ? (
        <div className="flex flex-col gap-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-28 rounded-2xl bg-neutral-100 dark:bg-neutral-900 animate-pulse" />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <div className="py-20 text-center text-neutral-400 text-sm">
          저장된 하이라이트가 없어요
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {filtered.map((h) => (
            <HighlightCard
              key={h.id}
              highlight={h}
              onDelete={handleDelete}
            />
          ))}
        </div>
      )}
    </div>
  )
}
