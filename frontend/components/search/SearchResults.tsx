'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { searchContents, toErrorMessages } from '@/lib/api'
import { CATEGORY_LABEL } from '@/lib/constants'
import type { Category, UnifiedSearchResponse } from '@/types'
import SearchResultCard from './SearchResultCard'
import SaveContentForm from './SaveContentForm'

const CATEGORIES: Category[] = ['ALL', 'BOOK', 'MOVIE', 'TV', 'ANIME', 'ANIME_MOVIE', 'ANIME_TVA']

type SearchState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'success'; results: UnifiedSearchResponse[]; totalPages: number; totalResults: number }

// isSaved 필드가 없거나 null이면 저장 안 된 것으로 취급
export const isSavedItem = (item: UnifiedSearchResponse) => item.isSaved === true

// 저장 여부 판별 키와 같은 기준: (externalId, category)
const isSameItem = (a: UnifiedSearchResponse, b: UnifiedSearchResponse) =>
  a.externalId === b.externalId && a.category === b.category

export default function SearchResults() {
  const searchParams = useSearchParams()
  const query = searchParams.get('query')?.trim() ?? ''
  const rawCategory = searchParams.get('category') as Category | null
  const category: Category = rawCategory && CATEGORIES.includes(rawCategory) ? rawCategory : 'ALL'
  const page = Math.max(1, Number(searchParams.get('page')) || 1)

  const [state, setState]       = useState<SearchState>({ status: 'idle' })
  const [selected, setSelected] = useState<UnifiedSearchResponse | null>(null)
  const [retry, setRetry]       = useState(0)

  // 가장 최근에 보낸 요청 번호 — 늦게 도착한 이전 응답이 최신 결과를 덮어쓰지 않게 한다
  const latestRequestId = useRef(0)

  useEffect(() => {
    if (!query) {
      setState({ status: 'idle' })
      return
    }

    const requestId = ++latestRequestId.current
    const controller = new AbortController()
    setState({ status: 'loading' })

    searchContents(category, query, page, controller.signal)
      .then((res) => {
        if (requestId !== latestRequestId.current) return
        setState({
          status: 'success',
          results: res.results ?? [],
          totalPages: res.totalPages,
          totalResults: res.totalResults,
        })
      })
      .catch((err) => {
        if (requestId !== latestRequestId.current) return
        if (err instanceof Error && err.name === 'AbortError') return
        setState({ status: 'error', message: toErrorMessages(err).join(' ') })
      })

    // 검색 조건이 바뀌면 진행 중인 이전 요청은 취소
    return () => controller.abort()
  }, [category, query, page, retry])

  // 저장 성공 시 재검색 없이 해당 항목만 '저장됨'으로 갱신
  function markSaved(target: UnifiedSearchResponse) {
    setState((prev) =>
      prev.status === 'success'
        ? { ...prev, results: prev.results.map((r) => (isSameItem(r, target) ? { ...r, isSaved: true } : r)) }
        : prev,
    )
    setSelected(null)
  }

  const pageHref = (p: number) => `/search?${new URLSearchParams({ category, query, page: String(p) })}`

  return (
    <div className="flex flex-col gap-5">
      {/* 헤더 */}
      <div>
        <h1 className="text-xl font-semibold text-neutral-900 dark:text-white">검색</h1>
        {query && (
          <p className="text-sm text-neutral-400 mt-1">
            {CATEGORY_LABEL[category]} · &ldquo;{query}&rdquo;
            {state.status === 'success' && ` · 총 ${state.totalResults.toLocaleString()}개`}
          </p>
        )}
      </div>

      {state.status === 'idle' && (
        <div className="py-20 text-center text-neutral-400 text-sm">
          상단 검색창에서 작품명을 검색해보세요
        </div>
      )}

      {state.status === 'loading' && (
        <div className="flex flex-col gap-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-36 rounded-2xl bg-neutral-100 dark:bg-neutral-900 animate-pulse" />
          ))}
        </div>
      )}

      {state.status === 'error' && (
        <div className="py-16 flex flex-col items-center gap-3 text-sm">
          <p className="text-rose-500">{state.message}</p>
          <button
            onClick={() => setRetry((n) => n + 1)}
            className="px-4 py-2 text-sm font-medium text-neutral-600 dark:text-neutral-300 bg-white dark:bg-neutral-900 border border-neutral-300 dark:border-neutral-700 rounded-lg hover:bg-neutral-50 dark:hover:bg-neutral-800 transition-colors"
          >
            다시 시도
          </button>
        </div>
      )}

      {state.status === 'success' && state.results.length === 0 && (
        <div className="py-20 text-center text-neutral-400 text-sm">검색 결과가 없어요</div>
      )}

      {state.status === 'success' && state.results.length > 0 && (
        <>
          <div className="flex flex-col gap-3">
            {state.results.map((item, index) => (
              <SearchResultCard
                key={`${item.category}-${item.externalId ?? `idx-${index}`}`}
                item={item}
                saved={isSavedItem(item)}
                onSelect={setSelected}
              />
            ))}
          </div>

          {/* 페이지네이션 — 백엔드 currentPage는 항상 0이라 URL의 page를 기준으로 한다 */}
          <div className="flex justify-center items-center gap-6 mt-6 mb-4">
            {page > 1 ? (
              <Link
                href={pageHref(page - 1)}
                className="px-4 py-2 text-sm font-medium text-neutral-600 bg-white border border-neutral-300 rounded-lg hover:bg-neutral-50 transition-colors"
              >
                이전
              </Link>
            ) : (
              <span className="px-4 py-2 text-sm font-medium text-neutral-300 border border-neutral-200 rounded-lg cursor-not-allowed">
                이전
              </span>
            )}

            <span className="text-sm font-semibold text-neutral-700 dark:text-neutral-300">
              {page} / {Math.max(state.totalPages, 1)} 페이지
            </span>

            {page < state.totalPages ? (
              <Link
                href={pageHref(page + 1)}
                className="px-4 py-2 text-sm font-medium text-white bg-violet-600 rounded-lg hover:bg-violet-700 transition-colors"
              >
                다음
              </Link>
            ) : (
              <span className="px-4 py-2 text-sm font-medium text-neutral-300 border border-neutral-200 rounded-lg cursor-not-allowed">
                다음
              </span>
            )}
          </div>
        </>
      )}

      {selected && !isSavedItem(selected) && (
        <SaveContentForm item={selected} onClose={() => setSelected(null)} onSaved={markSaved} />
      )}
    </div>
  )
}
