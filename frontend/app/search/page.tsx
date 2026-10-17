import { UnifiedSearchResponse } from '@/types';
import Link from 'next/link'; 

interface SearchPageProps {
  searchParams: { category?: string; query?: string; page?: string };
}

// 백엔드 응답 규격 정의
interface SearchResponse {
  results: UnifiedSearchResponse[];
  totalPages: number;
  totalResults: number;
}

export default async function SearchResultPage({ searchParams }: SearchPageProps) {
  const { category, query } = searchParams;
  const currentPage = Number(searchParams.page) || 1;

  const getCategoryLabel = (category: string) => {
    switch (category) {
      case 'ANIME_MOVIE': return '극장판';
      case 'ANIME_TVA': return 'TVA';
      case 'ANIME': return '애니메이션';
      case 'MOVIE': return '영화';
      case 'TV': return 'TV 프로그램';
      case 'BOOK': return '도서';
      default: return category;
    }
  };

  const res = await fetch(
    `http://localhost:8080/api/search?category=${category}&query=${query}&page=${currentPage}`,
    { cache: 'no-store' }
  );

  if (!res.ok) {
    return <div className="p-6 text-center">검색 결과를 불러오는 데 실패했습니다.</div>;
  }

  // 1. 응답 데이터 구조 분해 할당 (results, totalPages, totalResults)
  const { results, totalPages, totalResults }: SearchResponse = await res.json();

  return (
    <div className="p-6">
      {/* 2. totalResults를 활용해 전체 검색 결과 개수 표시 */}
      <h1 className="text-2xl font-bold mb-6">
        "{query}" 검색 결과 (총 {totalResults.toLocaleString()}개)
      </h1>
      
      <div className="grid gap-6">
        {results.map((item, index) => (
          <div key={`${item.title}-${index}`} className="flex border p-4 rounded-lg shadow-sm gap-4 items-start bg-white dark:bg-neutral-900 border-neutral-200 dark:border-neutral-800">
            {item.thumbnailUrl && (
              <div className="relative w-24 h-36 flex-shrink-0">
                <img 
                  src={item.thumbnailUrl} 
                  alt={item.title} 
                  className="w-full h-full object-cover rounded shadow-sm"
                />
              </div>
            )}

            <div className="flex-1">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-xs font-bold bg-violet-100 text-violet-800 dark:bg-violet-950 dark:text-violet-300 px-2 py-1 rounded">
                  {getCategoryLabel(item.category)}
                </span>
                <h3 className="font-bold text-xl text-neutral-900 dark:text-white">{item.title}</h3>
              </div>
              
              {item.authorOrDirector && (
                <p className="text-sm text-neutral-600 dark:text-neutral-400 mb-1">
                  <span className="font-medium">저자/감독:</span> {item.authorOrDirector}
                </p>
              )}

              <p className="text-sm text-neutral-500 mb-2">출시일: {item.releaseDate}</p>

              {item.voteAverage !== null && item.voteAverage > 0 && (
                <div className="text-yellow-500 font-bold flex items-center gap-1 text-sm">
                  ★ {item.voteAverage?.toFixed(1)}
                </div>
              )}
            </div>
          </div>
        ))}

        {/* 3. 정교해진 페이지네이션 컨트롤 */}
        {results.length > 0 && (
          <div className="flex justify-center items-center gap-6 mt-12 mb-10">
            {/* 이전 버튼 */}
            {currentPage > 1 ? (
              <Link
                href={`/search?category=${category}&query=${query}&page=${currentPage - 1}`}
                className="px-4 py-2 text-sm font-medium text-neutral-600 bg-white border border-neutral-300 rounded-lg hover:bg-neutral-50 transition-colors"
              >
                이전
              </Link>
            ) : (
              <span className="px-4 py-2 text-sm font-medium text-neutral-300 border border-neutral-200 rounded-lg cursor-not-allowed">
                이전
              </span>
            )}

            {/* 현재 페이지 / 전체 페이지 표시 */}
            <span className="text-sm font-semibold text-neutral-700 dark:text-neutral-300">
              {currentPage} / {totalPages} 페이지
            </span>

            {/* 다음 버튼: 현재 페이지가 전체 페이지 수보다 작을 때만 확실히 노출 */}
            {currentPage < totalPages ? (
              <Link
                href={`/search?category=${category}&query=${query}&page=${currentPage + 1}`}
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
        )}
        
        {results.length === 0 && (
          <div className="text-center py-20 text-neutral-500">
            검색 결과가 없습니다.
          </div>
        )}
      </div>
    </div>
  );
}