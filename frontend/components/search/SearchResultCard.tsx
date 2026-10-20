import { Check, ImageOff, PenLine } from 'lucide-react'
import type { UnifiedSearchResponse } from '@/types'
import { CATEGORY_BADGE, CATEGORY_LABEL } from '@/lib/constants'

interface Props {
  item: UnifiedSearchResponse
  saved: boolean
  onSelect: (item: UnifiedSearchResponse) => void
}

export default function SearchResultCard({ item, saved, onSelect }: Props) {
  const { title, authorOrDirector, releaseDate, voteAverage, thumbnailUrl, category, externalId } = item
  // externalId가 없으면 저장 여부를 판별할 수 없어 저장을 막는다
  const savable = !saved && !!externalId
  // TMDB 결과는 감독 정보가 없어 null로 온다. 빈 값이면 줄을 숨기지 않고 '-'로 자리를 맞춘다
  const author = authorOrDirector?.trim() || '-'

  return (
    <article className="flex gap-4 bg-white dark:bg-neutral-950 border border-neutral-200 dark:border-neutral-800 rounded-2xl p-5 hover:border-neutral-300 dark:hover:border-neutral-700 transition-colors">
      {/* 썸네일 */}
      {thumbnailUrl ? (
        <img
          src={thumbnailUrl}
          alt={title}
          className="w-20 h-28 flex-shrink-0 rounded-lg object-cover bg-neutral-100 dark:bg-neutral-900"
        />
      ) : (
        <div className="w-20 h-28 flex-shrink-0 rounded-lg bg-neutral-100 dark:bg-neutral-900 flex items-center justify-center text-neutral-300 dark:text-neutral-700">
          <ImageOff size={18} />
        </div>
      )}

      {/* 정보 */}
      <div className="flex-1 min-w-0 flex flex-col gap-1">
        <div className="flex items-center gap-2">
          <span className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${CATEGORY_BADGE[category] ?? 'bg-neutral-100 text-neutral-600'}`}>
            {CATEGORY_LABEL[category] ?? category}
          </span>
          {saved && (
            <span className="flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full font-medium bg-violet-600 text-white">
              <Check size={11} /> 저장됨
            </span>
          )}
        </div>

        <h3 className="text-[15px] font-semibold text-neutral-900 dark:text-white truncate">{title}</h3>

        <p className="text-[12px] text-neutral-500 truncate">저자/감독 · {author}</p>
        {releaseDate && <p className="text-[12px] text-neutral-400">출시일 · {releaseDate}</p>}
        {voteAverage !== null && voteAverage > 0 && (
          <p className="text-[12px] font-medium text-yellow-500">★ {voteAverage.toFixed(1)}</p>
        )}
      </div>

      {/* 액션 */}
      <div className="flex items-start">
        <button
          onClick={() => onSelect(item)}
          disabled={!savable}
          title={saved ? '이미 저장된 콘텐츠예요' : !externalId ? '식별자가 없어 저장할 수 없어요' : undefined}
          className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors bg-violet-600 text-white hover:bg-violet-700 disabled:bg-neutral-100 disabled:text-neutral-400 dark:disabled:bg-neutral-900 disabled:cursor-not-allowed"
        >
          <PenLine size={12} /> {saved ? '저장됨' : '기록하기'}
        </button>
      </div>
    </article>
  )
}
