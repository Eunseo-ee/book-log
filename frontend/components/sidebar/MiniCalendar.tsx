'use client'

import { useState } from 'react'
import { useMiniCalendar } from '@/hooks/useCalendar'
import { ChevronLeft, ChevronRight } from 'lucide-react'

const DOW = ['일', '월', '화', '수', '목', '금', '토']

export default function MiniCalendar() {
  const today = new Date()
  const [year, setYear]   = useState(today.getFullYear())
  const [month, setMonth] = useState(today.getMonth() + 1)

  const { recordedDays } = useMiniCalendar(year, month)

  const firstDay  = new Date(year, month - 1, 1).getDay()
  const lastDate  = new Date(year, month, 0).getDate()

  function changeMonth(dir: 1 | -1) {
    const d = new Date(year, month - 1 + dir, 1)
    setYear(d.getFullYear())
    setMonth(d.getMonth() + 1)
  }

  const isToday = (d: number) =>
    d === today.getDate() &&
    month === today.getMonth() + 1 &&
    year  === today.getFullYear()

  return (
    <div className="px-3 py-3">
      {/* 월 네비게이션 */}
      <div className="flex items-center justify-between mb-3">
        <button
          onClick={() => changeMonth(-1)}
          className="p-1 rounded-md hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-400 transition-colors"
        >
          <ChevronLeft size={14} />
        </button>
        <span className="text-xs font-medium text-neutral-700 dark:text-neutral-300">
          {year}년 {month}월
        </span>
        <button
          onClick={() => changeMonth(1)}
          className="p-1 rounded-md hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-400 transition-colors"
        >
          <ChevronRight size={14} />
        </button>
      </div>

      {/* 요일 헤더 */}
      <div className="grid grid-cols-7 mb-1">
        {DOW.map((d, i) => (
          <div
            key={d}
            className={`text-center text-[10px] font-medium pb-1 ${
              i === 0 ? 'text-rose-400' : 'text-neutral-400'
            }`}
          >
            {d}
          </div>
        ))}
      </div>

      {/* 날짜 그리드 */}
      <div className="grid grid-cols-7 gap-y-[2px]">
        {/* 빈 칸 */}
        {Array.from({ length: firstDay }).map((_, i) => (
          <div key={`empty-${i}`} />
        ))}

        {/* 날짜 */}
        {Array.from({ length: lastDate }, (_, i) => i + 1).map((d) => {
          const hasRecord = recordedDays.has(d)
          const todayFlag = isToday(d)
          return (
            <div
              key={d}
              className={`relative flex items-center justify-center aspect-square rounded-md text-[11px] transition-colors
                ${todayFlag
                  ? 'bg-neutral-900 dark:bg-white text-white dark:text-neutral-900 font-semibold'
                  : 'text-neutral-600 dark:text-neutral-400 hover:bg-neutral-100 dark:hover:bg-neutral-800 cursor-pointer'
                }`}
            >
              {d}
              {/* 기록 dot — GET /api/calendar/activities boolean 응답 */}
              {hasRecord && !todayFlag && (
                <span className="absolute bottom-[2px] left-1/2 -translate-x-1/2 w-[3px] h-[3px] rounded-full bg-violet-500" />
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
