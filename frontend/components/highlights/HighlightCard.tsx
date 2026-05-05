import type { HighlightWithContent } from '@/types'
import { CATEGORY_BADGE, CATEGORY_ACCENT, CATEGORY_LABEL } from '@/lib/constants'
import { Pencil, Trash2 } from 'lucide-react'

interface Props {
  highlight: HighlightWithContent
  onDelete?: (id: number) => void
  onEdit?: (h: HighlightWithContent) => void
}

export default function HighlightCard({ highlight, onDelete, onEdit }: Props) {
  const { id, text, content, createdAt, page, season, episode, timestamp } = highlight
  const category = content.category

  // 위치 정보 — 백엔드 Highlight 엔티티의 page/season/episode/timestamp
  const locationParts: string[] = []
  if (page)      locationParts.push(`p.${page}`)
  if (season)    locationParts.push(`S${season}`)
  if (episode)   locationParts.push(`E${episode}`)
  if (timestamp) locationParts.push(timestamp)
  const location = locationParts.join(' · ')

  const dateStr = createdAt
    ? new Date(createdAt).toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
    : ''

  return (
    <article className="group bg-white dark:bg-neutral-950 border border-neutral-200 dark:border-neutral-800 rounded-2xl p-5 hover:border-neutral-300 dark:hover:border-neutral-700 transition-colors">
      {/* 메타 */}
      <div className="flex items-center gap-2 mb-3">
        <span className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${CATEGORY_BADGE[category] ?? 'bg-neutral-100 text-neutral-600'}`}>
          {CATEGORY_LABEL[category] ?? category}
        </span>
        <span className="text-[12px] text-neutral-500">{content.title}</span>
        {location && (
          <>
            <span className="text-neutral-300 dark:text-neutral-700 text-xs">·</span>
            <span className="text-[11px] text-neutral-400">{location}</span>
          </>
        )}
        <span className="ml-auto text-[11px] text-neutral-400">{dateStr}</span>
      </div>

      {/* 본문 — 카테고리별 accent */}
      <blockquote className={`border-l-2 pl-4 text-[14px] text-neutral-800 dark:text-neutral-200 leading-relaxed ${CATEGORY_ACCENT[category] ?? 'border-neutral-300'}`}>
        {text}
      </blockquote>

      {/* 액션 */}
      <div className="flex justify-end gap-1 mt-3 opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          onClick={() => onEdit?.(highlight)}
          className="flex items-center gap-1 px-2 py-1 rounded-md text-xs text-neutral-400 hover:text-neutral-700 dark:hover:text-neutral-200 hover:bg-neutral-100 dark:hover:bg-neutral-800 transition-colors"
        >
          <Pencil size={12} /> 수정
        </button>
        <button
          onClick={() => onDelete?.(id)}
          className="flex items-center gap-1 px-2 py-1 rounded-md text-xs text-neutral-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950 transition-colors"
        >
          <Trash2 size={12} /> 삭제
        </button>
      </div>
    </article>
  )
}
