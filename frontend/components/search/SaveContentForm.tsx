'use client'

import { useEffect, useState } from 'react'
import { ImageOff, X } from 'lucide-react'
import { saveContent, toErrorMessages } from '@/lib/api'
import { CATEGORY_BADGE, CATEGORY_LABEL, STATUS_LABEL } from '@/lib/constants'
import type { ContentRequestDto, Status, UnifiedSearchResponse } from '@/types'

interface Props {
  item: UnifiedSearchResponse
  onClose: () => void
  onSaved: (item: UnifiedSearchResponse) => void
}

const COMMENT_MAX = 500 // ContentRequestDto.comment @Size(max = 500)
const RATING_OPTIONS = Array.from({ length: 11 }, (_, i) => i * 0.5) // 0, 0.5, ... 5
const STATUS_OPTIONS = Object.entries(STATUS_LABEL) as [Status, string][]

// 사용자 로컬 기준 오늘 날짜 "YYYY-MM-DD" (toISOString은 UTC라 자정 전후로 하루가 밀릴 수 있음)
function todayString() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export default function SaveContentForm({ item, onClose, onSaved }: Props) {
  const today = todayString()
  // TMDB 결과는 null로 온다. 공백뿐인 값도 '없음'으로 보고 표시/전송 모두 같은 기준을 쓴다
  const author = item.authorOrDirector?.trim() || null

  const [viewDate, setViewDate] = useState(today)
  const [rating, setRating]     = useState('')           // '' = 평점 없음
  const [status, setStatus]     = useState<Status>('COMPLETED')
  const [comment, setComment]   = useState('')
  const [errors, setErrors]     = useState<string[]>([])
  const [submitting, setSubmitting] = useState(false)

  // ESC로 닫기
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !submitting) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, submitting])

  function validate(): string[] {
    const messages: string[] = []
    if (!viewDate) messages.push('관람 날짜를 선택해주세요.')
    else if (viewDate > today) messages.push('미래 날짜의 기록은 저장할 수 없습니다.')
    if (comment.length > COMMENT_MAX) messages.push(`한줄평은 ${COMMENT_MAX}자 이내로 작성해주세요.`)
    return messages
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const messages = validate()
    setErrors(messages)
    if (messages.length > 0) return

    // 비어 있는 선택 필드는 키 자체를 보내지 않는다 (rating 포함)
    const dto: ContentRequestDto = {
      title: item.title,
      category: item.category,
      viewDate,
      status,
      ...(author ? { authorOrDirector: author } : {}),
      ...(item.thumbnailUrl ? { thumbnailUrl: item.thumbnailUrl } : {}),
      ...(item.externalId ? { externalId: item.externalId } : {}),
      ...(rating !== '' ? { rating: Number(rating) } : {}),
      ...(comment.trim() ? { comment } : {}),
    }

    setSubmitting(true)
    try {
      await saveContent(dto)
      onSaved(item)
    } catch (err) {
      setErrors(toErrorMessages(err))
    } finally {
      setSubmitting(false)
    }
  }

  const inputClass =
    'w-full px-3 py-2 text-sm bg-neutral-100 dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-xl outline-none focus:ring-2 focus:ring-violet-500 text-neutral-800 dark:text-neutral-200'
  const labelClass = 'text-xs font-medium text-neutral-500 dark:text-neutral-400'

  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 px-4"
      onClick={() => !submitting && onClose()}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="save-content-title"
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-lg max-h-[90vh] overflow-y-auto bg-white dark:bg-neutral-950 border border-neutral-200 dark:border-neutral-800 rounded-2xl p-5"
      >
        {/* 헤더 */}
        <div className="flex items-center justify-between mb-4">
          <h2 id="save-content-title" className="text-base font-semibold text-neutral-900 dark:text-white">
            기록하기
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

        {/* 검색 결과에서 자동 입력 (수정 불가) */}
        <div className="flex gap-3 p-3 mb-5 rounded-xl bg-neutral-50 dark:bg-neutral-900">
          {item.thumbnailUrl ? (
            <img src={item.thumbnailUrl} alt={item.title} className="w-14 h-20 flex-shrink-0 rounded-md object-cover" />
          ) : (
            <div className="w-14 h-20 flex-shrink-0 rounded-md bg-neutral-100 dark:bg-neutral-800 flex items-center justify-center text-neutral-300">
              <ImageOff size={16} />
            </div>
          )}
          <div className="min-w-0 flex flex-col gap-1">
            <span className={`self-start text-[11px] px-2 py-0.5 rounded-full font-medium ${CATEGORY_BADGE[item.category] ?? 'bg-neutral-100 text-neutral-600'}`}>
              {CATEGORY_LABEL[item.category] ?? item.category}
            </span>
            <p className="text-sm font-semibold text-neutral-900 dark:text-white">{item.title}</p>
            <p className="text-[12px] text-neutral-500">저자/감독 · {author ?? '-'}</p>
            <p className="text-[11px] text-neutral-400">ID · {item.externalId}</p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="viewDate" className={labelClass}>
                날짜 <span className="text-rose-500">*</span>
              </label>
              <input
                id="viewDate"
                type="date"
                required
                max={today}
                value={viewDate}
                onChange={(e) => setViewDate(e.target.value)}
                className={inputClass}
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor="status" className={labelClass}>상태</label>
              <select
                id="status"
                value={status}
                onChange={(e) => setStatus(e.target.value as Status)}
                className={`${inputClass} cursor-pointer`}
              >
                {STATUS_OPTIONS.map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor="rating" className={labelClass}>평점 (선택)</label>
              <select
                id="rating"
                value={rating}
                onChange={(e) => setRating(e.target.value)}
                className={`${inputClass} cursor-pointer`}
              >
                <option value="">평점 없음</option>
                {RATING_OPTIONS.map((r) => (
                  <option key={r} value={String(r)}>★ {r.toFixed(1)}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <label htmlFor="comment" className={labelClass}>한줄평 (선택)</label>
              <span className={`text-[11px] ${comment.length > COMMENT_MAX ? 'text-rose-500' : 'text-neutral-400'}`}>
                {comment.length} / {COMMENT_MAX}
              </span>
            </div>
            <textarea
              id="comment"
              rows={4}
              maxLength={COMMENT_MAX}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="어땠는지 짧게 남겨보세요"
              className={`${inputClass} resize-none placeholder:text-neutral-400`}
            />
          </div>

          {/* 클라이언트 검증 실패 / 서버 400 메시지 (중복 저장 포함) */}
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
              disabled={submitting}
              className="px-4 py-2 text-sm font-medium text-white bg-violet-600 rounded-lg hover:bg-violet-700 disabled:opacity-60 transition-colors"
            >
              {submitting ? '저장 중…' : '저장'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
