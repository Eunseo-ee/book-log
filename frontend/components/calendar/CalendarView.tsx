'use client'

import { useState } from 'react'
import { ChevronLeft, ChevronRight, TrendingUp, TrendingDown, Star, BookOpen, Tv, Clapperboard } from 'lucide-react'
import { useCalendar } from '@/hooks/useCalendar'
import { getMonthlyStatistics } from '@/lib/api'
import { useEffect } from 'react'
import type { StatisticsResponse } from '@/types'
import { CATEGORY_LABEL } from '@/lib/constants'

const DOW = ['일', '월', '화', '수', '목', '금', '토']

export default function CalendarView() {
  const today = new Date()
  const [year, setYear]   = useState(today.getFullYear())
  const [month, setMonth] = useState(today.getMonth() + 1)
  const [stats, setStats] = useState<StatisticsResponse | null>(null)

  const { data } = useCalendar(year, month)

  // GET /api/statistics?year=2026&month=4
  useEffect(() => {
    getMonthlyStatistics(year, month)
      .then(setStats)
      .catch(() => setStats(null))
  }, [year, month])

  function changeMonth(dir: 1 | -1) {
    const d = new Date(year, month - 1 + dir, 1)
    setYear(d.getFullYear())
    setMonth(d.getMonth() + 1)
  }

  const firstDay = new Date(year, month - 1, 1).getDay()
  const lastDate = new Date(year, month, 0).getDate()

  // day → DayActivity 맵
  const dayMap = new Map(data?.days.map((d) => [d.day, d]) ?? [])

  const isToday = (d: number) =>
    d === today.getDate() && month === today.getMonth() + 1 && year === today.getFullYear()

  return (
    <div className="flex flex-col gap-6">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-neutral-900 dark:text-white">캘린더</h1>
        <div className="flex items-center gap-1">
          <button
            onClick={() => changeMonth(-1)}
            className="p-2 rounded-lg hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-400 transition-colors"
          >
            <ChevronLeft size={16} />
          </button>
          <span className="px-3 text-sm font-medium text-neutral-700 dark:text-neutral-300 min-w-[96px] text-center">
            {year}년 {month}월
          </span>
          <button
            onClick={() => changeMonth(1)}
            className="p-2 rounded-lg hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-400 transition-colors"
          >
            <ChevronRight size={16} />
          </button>
        </div>
      </div>

      {/* 월별 리캡 — GET /api/statistics */}
      {stats && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <RecapCard
            label="이번 달 기록"
            value={`${stats.activity.currentCount}개`}
            sub={
              stats.activity.growthRate >= 0
                ? <span className="flex items-center gap-1 text-emerald-600 text-xs"><TrendingUp size={11} />전월 대비 +{Math.abs(stats.activity.growthRate).toFixed(0)}%</span>
                : <span className="flex items-center gap-1 text-rose-500 text-xs"><TrendingDown size={11} />전월 대비 {stats.activity.growthRate.toFixed(0)}%</span>
            }
          />
          <RecapCard
            label="평균 별점"
            value={stats.averageRating ? `${stats.averageRating.toFixed(1)} ★` : '—'}
            sub={<span className="text-xs text-neutral-400">이번 달 평균</span>}
          />
          <RecapCard
            label="최다 카테고리"
            value={CATEGORY_LABEL[stats.topCategory] ?? stats.topCategory}
            sub={<span className="text-xs text-neutral-400">{stats.tasteAnalysis}</span>}
          />
          <RecapCard
            label="가장 많이 본 장르"
            value={stats.genreStats[0]?.genre ?? '—'}
            sub={<span className="text-xs text-neutral-400">{stats.genreStats[0]?.count ?? 0}개 기록</span>}
          />
        </div>
      )}

      {/* 캘린더 그리드 */}
      <div className="bg-white dark:bg-neutral-950 rounded-2xl border border-neutral-200 dark:border-neutral-800 p-5">
        {/* 요일 헤더 */}
        <div className="grid grid-cols-7 mb-2">
          {DOW.map((d, i) => (
            <div
              key={d}
              className={`text-center text-xs font-medium pb-2 ${
                i === 0 ? 'text-rose-400' : 'text-neutral-400'
              }`}
            >
              {d}
            </div>
          ))}
        </div>

        {/* 날짜 */}
        <div className="grid grid-cols-7 gap-1">
          {Array.from({ length: firstDay }).map((_, i) => (
            <div key={`e-${i}`} />
          ))}

          {Array.from({ length: lastDate }, (_, i) => i + 1).map((d) => {
            const activity = dayMap.get(d)
            const todayFlag = isToday(d)

            return (
              <div
                key={d}
                className={`relative aspect-square flex flex-col items-center justify-start pt-[6px] rounded-xl text-xs transition-colors
                  ${todayFlag
                    ? 'ring-2 ring-violet-500 ring-inset'
                    : activity ? 'hover:bg-violet-50 dark:hover:bg-violet-950/40 cursor-pointer' : ''
                  }`}
              >
                <span className={`font-medium leading-none ${todayFlag ? 'text-violet-600' : 'text-neutral-700 dark:text-neutral-300'}`}>
                  {d}
                </span>

                {/* 썸네일 — DayActivity.thumbnail (현재 null, 추후 확장) */}
                {activity?.thumbnail ? (
                  <img
                    src={activity.thumbnail}
                    alt=""
                    className="mt-1 w-7 h-7 rounded-md object-cover"
                  />
                ) : activity ? (
                  <span className="mt-1 w-[6px] h-[6px] rounded-full bg-violet-400" />
                ) : null}

                {/* count 뱃지 */}
                {activity?.count && activity.count > 1 && (
                  <span className="absolute bottom-1 right-1 text-[9px] bg-violet-100 dark:bg-violet-900 text-violet-700 dark:text-violet-300 rounded-full px-1 leading-4">
                    {activity.count}
                  </span>
                )}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

function RecapCard({ label, value, sub }: { label: string; value: string; sub: React.ReactNode }) {
  return (
    <div className="bg-white dark:bg-neutral-950 rounded-xl border border-neutral-200 dark:border-neutral-800 p-4">
      <p className="text-xs text-neutral-400 mb-1">{label}</p>
      <p className="text-lg font-semibold text-neutral-900 dark:text-white leading-tight">{value}</p>
      <div className="mt-1">{sub}</div>
    </div>
  )
}
