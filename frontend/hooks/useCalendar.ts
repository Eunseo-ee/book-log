'use client'

import { useEffect, useState } from 'react'
import { getCalendarActivities } from '@/lib/api'

// 사이드바용: 기록 있는 날짜 Set만 반환 (썸네일/count 없음 - 경량 쿼리)
export function useMiniCalendar(year: number, month: number) {
  const [recordedDays, setRecordedDays] = useState<Set<number>>(new Set())
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    getCalendarActivities(year, month)
      .then((res) => {
        // DayActivity.of(day) → day만 있고 thumbnail·count는 null
        setRecordedDays(new Set(res.days.map((d) => d.day)))
      })
      .catch(() => setRecordedDays(new Set()))
      .finally(() => setLoading(false))
  }, [year, month])

  return { recordedDays, loading }
}

// 캘린더 페이지용: 썸네일·count 포함 전체 데이터
export function useCalendar(year: number, month: number) {
  const [data, setData] = useState<Awaited<ReturnType<typeof getCalendarActivities>> | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    getCalendarActivities(year, month)
      .then(setData)
      .catch(() => setData(null))
      .finally(() => setLoading(false))
  }, [year, month])

  return { data, loading }
}
