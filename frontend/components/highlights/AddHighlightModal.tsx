'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { X } from 'lucide-react'
import { getFilteredContents, saveHighlight, toErrorMessages } from '@/lib/api'
import { CATEGORY_BADGE, CATEGORY_LABEL } from '@/lib/constants'
import type { Category, ContentResponseDto, HighlightRequestDto } from '@/types'

interface Props {
  onClose: () => void
  onSaved: () => void
}

const TEXT_MAX = 1000 // HighlightRequestDto.text @Size(max = 1000)
const TIMESTAMP_PATTERN = /^\d{2}:[0-5]\d:[0-5]\d$/ // "01:23:45"

// 작품 카테고리별로 입력받을 위치 필드
type LocationKind = 'page' | 'timestamp' | 'episode'

function locationKindOf(category: Category): LocationKind | null {
  switch (category) {
    case 'BOOK':
      return 'page'
    case 'MOVIE':
    case 'ANIME_MOVIE':
      return 'timestamp'
    case 'TV':
    case 'ANIME_TVA':
    case 'ANIME':
      return 'episode'
    default:
      return null
  }
}

// '' → undefined(보내지 않음), 1 이상 정수 → 숫자, 그 외 → null(검증 실패)
function parsePositiveInt(value: string): number | undefined | null {
  const v = value.trim()
  if (!v) return undefined
  if (!/^\d+$/.test(v)) return null
  const n = Number(v)
  return n >= 1 ? n : null
}

export default function AddHighlightModal({ onClose, onSaved }: Props) {
  const [contents, setContents]       = useState<ContentResponseDto[]>([])
  const [loadingContents, setLoadingContents] = useState(true)
  const [loadError, setLoadError]     = useState<string | null>(null)

  const [contentId, setContentId] = useState('')   // '' = 미선택
  const [text, setText]           = useState('')
  const [page, setPage]           = useState('')
  const [season, setSeason]       = useState('')
  const [episode, setEpisode]     = useState('')
  const [timestamp, setTimestamp] = useState('')
  const [errors, setErrors]       = useState<string[]>([])
  const [submitting, setSubmitting] = useState(false)

  // 작품 선택 목록: 저장된 콘텐츠 전체 (GET /api/contents/filter, 조건 없음)
  useEffect(() => {
    let cancelled = false
    getFilteredContents()
      .then((list) => {
        if (cancelled) return
        // 최근에 본 작품이 위로 오게
        const sorted = [...list].sort(
          (a, b) => (b.viewDate ?? '').localeCompare(a.viewDate ?? '') || b.id - a.id,
        )
        setContents(sorted)
      })
      .catch((err) => {
        if (!cancelled) setLoadError(toErrorMessages(err)[0])
      })
      .finally(() => {
        if (!cancelled) setLoadingContents(false)
      })
    return () => { cancelled = true }
  }, [])

  // ESC로 닫기
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !submitting) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, submitting])

  const selected = contents.find((c) => String(c.id) === contentId) ?? null
  const kind = selected ? locationKindOf(selected.category) : null
  const noContents = !loadingContents && !loadError && contents.length === 0

  // 검증과 DTO 생성을 한 번에: 지금 보이는 위치 필드만 담는다 (작품을 바꿔서 숨은 필드의 값은 버림)
  function buildDto(): { dto: HighlightRequestDto | null; messages: string[] } {
    const messages: string[] = []
    if (!selected) messages.push('작품을 선택해주세요.')
    if (!text.trim()) messages.push('문장을 입력해주세요.')
    else if (text.length > TEXT_MAX) messages.push(`문장은 ${TEXT_MAX}자 이내로 입력해주세요.`)

    const location: Partial<HighlightRequestDto> = {}
    if (kind === 'page') {
      const p = parsePositiveInt(page)
      if (p === null) messages.push('페이지는 1 이상의 정수로 입력해주세요.')
      else if (p !== undefined) location.page = p
    } else if (kind === 'episode') {
      const s = parsePositiveInt(season)
      const e = parsePositiveInt(episode)
      if (s === null) messages.push('시즌은 1 이상의 정수로 입력해주세요.')
      else if (s !== undefined) location.season = s
      if (e === null) messages.push('화수는 1 이상의 정수로 입력해주세요.')
      else if (e !== undefined) location.episode = e
    } else if (kind === 'timestamp') {
      const t = timestamp.trim()
      if (t && !TIMESTAMP_PATTERN.test(t)) messages.push('시간은 01:23:45 형식으로 입력해주세요.')
      else if (t) location.timestamp = t
    }

    if (messages.length > 0 || !selected) return { dto: null, messages }
    return { dto: { text, contentId: selected.id, ...location }, messages }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const { dto, messages } = buildDto()
    setErrors(messages)
    if (!dto) return

    setSubmitting(true)
    try {
      await saveHighlight(dto)
      onSaved()
    } catch (err) {
      setErrors(toErrorMessages(err))
    } finally {
      setSubmitting(false)
    }
  }

  const inputClass =
    'w-full px-3 py-2 text-sm bg-neutral-100 dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-xl outline-none focus:ring-2 focus:ring-violet-500 text-neutral-800 dark:text-neutral-200 placeholder:text-neutral-400'
  const labelClass = 'text-xs font-medium text-neutral-500 dark:text-neutral-400'

  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 px-4"
      onClick={() => !submitting && onClose()}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="add-highlight-title"
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-lg max-h-[90vh] overflow-y-auto bg-white dark:bg-neutral-950 border border-neutral-200 dark:border-neutral-800 rounded-2xl p-5"
      >
        {/* 헤더 */}
        <div className="flex items-center justify-between mb-4">
          <h2 id="add-highlight-title" className="text-base font-semibold text-neutral-900 dark:text-white">
            하이라이트 추가
          </h2>
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            aria-label="닫기"
            className="p-2 rounded-lg hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-400 transition-colors"
          >
            <X size={16} />
          </button>
        </div>

        {noContents ? (
          <div className="flex flex-col items-center gap-3 py-10 text-center">
            <p className="text-sm text-neutral-500">먼저 기록을 저장하세요.</p>
            <p className="text-xs text-neutral-400">검색에서 작품을 기록하면 여기서 골라 문장을 남길 수 있어요.</p>
            <Link
              href="/search"
              className="mt-1 px-4 py-2 text-sm font-medium text-white bg-violet-600 rounded-lg hover:bg-violet-700 transition-colors"
            >
              작품 검색하러 가기
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
            {/* 작품 선택 */}
            <div className="flex flex-col gap-1.5">
              <label htmlFor="highlight-content" className={labelClass}>
                작품 <span className="text-rose-500">*</span>
              </label>
              {loadError ? (
                <p role="alert" className="px-3 py-2 rounded-xl bg-rose-50 dark:bg-rose-950 text-xs text-rose-600 dark:text-rose-300">
                  작품 목록을 불러오지 못했어요. {loadError}
                </p>
              ) : (
                <select
                  id="highlight-content"
                  value={contentId}
                  onChange={(e) => setContentId(e.target.value)}
                  disabled={loadingContents}
                  className={`${inputClass} cursor-pointer`}
                >
                  <option value="">{loadingContents ? '불러오는 중…' : '작품을 선택하세요'}</option>
                  {contents.map((c) => (
                    <option key={c.id} value={String(c.id)}>
                      [{CATEGORY_LABEL[c.category] ?? c.category}] {c.title}
                    </option>
                  ))}
                </select>
              )}
              {selected && (
                <div className="flex items-center gap-2 mt-1">
                  <span className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${CATEGORY_BADGE[selected.category] ?? 'bg-neutral-100 text-neutral-600'}`}>
                    {CATEGORY_LABEL[selected.category] ?? selected.category}
                  </span>
                  <span className="text-[12px] text-neutral-500 truncate">{selected.title}</span>
                </div>
              )}
            </div>

            {/* 문장 */}
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between">
                <label htmlFor="highlight-text" className={labelClass}>
                  문장 <span className="text-rose-500">*</span>
                </label>
                <span className={`text-[11px] ${text.length > TEXT_MAX ? 'text-rose-500' : 'text-neutral-400'}`}>
                  {text.length} / {TEXT_MAX}
                </span>
              </div>
              <textarea
                id="highlight-text"
                rows={5}
                maxLength={TEXT_MAX}
                value={text}
                onChange={(e) => setText(e.target.value)}
                placeholder="기억에 남는 문장이나 대사를 남겨보세요"
                className={`${inputClass} resize-none`}
              />
            </div>

            {/* 위치 (선택) — 작품 카테고리에 따라 다름 */}
            {kind === 'page' && (
              <div className="flex flex-col gap-1.5">
                <label htmlFor="highlight-page" className={labelClass}>페이지 (선택)</label>
                <input
                  id="highlight-page"
                  type="number"
                  inputMode="numeric"
                  min={1}
                  value={page}
                  onChange={(e) => setPage(e.target.value)}
                  placeholder="예: 123"
                  className={inputClass}
                />
              </div>
            )}

            {kind === 'timestamp' && (
              <div className="flex flex-col gap-1.5">
                <label htmlFor="highlight-timestamp" className={labelClass}>시간 (선택)</label>
                <input
                  id="highlight-timestamp"
                  type="text"
                  value={timestamp}
                  onChange={(e) => setTimestamp(e.target.value)}
                  placeholder="예: 01:23:45"
                  className={inputClass}
                />
              </div>
            )}

            {kind === 'episode' && (
              <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="highlight-season" className={labelClass}>시즌 (선택)</label>
                  <input
                    id="highlight-season"
                    type="number"
                    inputMode="numeric"
                    min={1}
                    value={season}
                    onChange={(e) => setSeason(e.target.value)}
                    placeholder="예: 1"
                    className={inputClass}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="highlight-episode" className={labelClass}>화 (선택)</label>
                  <input
                    id="highlight-episode"
                    type="number"
                    inputMode="numeric"
                    min={1}
                    value={episode}
                    onChange={(e) => setEpisode(e.target.value)}
                    placeholder="예: 12"
                    className={inputClass}
                  />
                </div>
              </div>
            )}

            {/* 클라이언트 검증 실패 / 서버 400 메시지 ({ status, message } 또는 { 필드명: 메시지 }) */}
            {errors.length > 0 && (
              <ul role="alert" className="flex flex-col gap-1 px-3 py-2 rounded-xl bg-rose-50 dark:bg-rose-950 text-xs text-rose-600 dark:text-rose-300">
                {errors.map((m) => <li key={m}>{m}</li>)}
              </ul>
            )}

            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={onClose}
                disabled={submitting}
                className="px-4 py-2 text-sm font-medium text-neutral-600 dark:text-neutral-300 bg-white dark:bg-neutral-900 border border-neutral-300 dark:border-neutral-700 rounded-lg hover:bg-neutral-50 dark:hover:bg-neutral-800 transition-colors"
              >
                취소
              </button>
              <button
                type="submit"
                disabled={submitting || loadingContents || !!loadError}
                className="px-4 py-2 text-sm font-medium text-white bg-violet-600 rounded-lg hover:bg-violet-700 disabled:opacity-60 transition-colors"
              >
                {submitting ? '저장 중…' : '저장'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
