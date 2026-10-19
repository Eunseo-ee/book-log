import { Suspense } from 'react'
import SearchResults from '@/components/search/SearchResults'

// 검색어/카테고리/페이지는 URL(/search?category=&query=&page=)에서 읽는다 — Header의 SearchForm이 이동시킴
// useSearchParams를 쓰는 클라이언트 컴포넌트는 Suspense 경계가 필요하다
export default function SearchPage() {
  return (
    <Suspense fallback={null}>
      <SearchResults />
    </Suspense>
  )
}
