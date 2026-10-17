'use client';

import SearchForm from '../search/SearchForm';

export default function Header() {
  return (
    <header className="sticky top-0 z-20 h-16 w-full border-b border-neutral-100 dark:border-neutral-800 bg-white/80 dark:bg-neutral-950/80 backdrop-blur-md">
      {/* 
         이 div가 본문의 여백(주황색 마진)을 똑같이 만들어줍니다.
         - max-w-4xl: 너비 통일
         - mx-auto: 중앙 정렬 마진(주황색 영역) 생성
         - px-6: 본문의 패딩(초록색 영역)과 일치
      */}
      <div className="max-w-4xl mx-auto px-6 h-full flex items-center">
        <div className="w-full max-w-md">
          <SearchForm />
        </div>
      </div>
    </header>
  );
}