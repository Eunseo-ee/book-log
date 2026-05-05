'use client'

import { useEffect, useState } from 'react'
import { getHighlights } from '@/lib/api'
import type { Category, HighlightWithContent } from '@/types'

export function useHighlights(year?: number, category?: Category) {
  const [highlights, setHighlights] = useState<HighlightWithContent[]>([])
  const [loading, setLoading] = useState(true)

  const refetch = () => {
    setLoading(true)
    getHighlights(year, category)
      .then(setHighlights)
      .catch(() => setHighlights([]))
      .finally(() => setLoading(false))
  }

  useEffect(() => { refetch() }, [year, category])

  return { highlights, loading, refetch }
}
