'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Search } from 'lucide-react';

export default function SearchForm() {
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('ALL');
  const router = useRouter();

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;
    router.push(`/search?category=${category}&query=${encodeURIComponent(query)}`);
  };

  return (
    <form onSubmit={handleSearch} className="relative flex items-center w-full group">
      <select
        value={category}
        onChange={(e) => setCategory(e.target.value)}
        className="absolute left-2 z-10 bg-transparent text-[12px] font-medium text-neutral-500 dark:text-neutral-400 focus:outline-none cursor-pointer hover:text-violet-600 transition-colors"
      >
        <option value="ALL">전체</option>
        <option value="BOOK">책</option>
        <option value="MOVIE">영화</option>
        <option value="TV">TV</option>
        <option value="ANIME">애니</option>
      </select>

      {/* 입력창 */}
      <input
        type="text"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="작품명을 검색해보세요"
        className="w-full h-10 pl-20 pr-10 bg-neutral-50 dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-lg text-[13px] placeholder:text-neutral-400 focus:outline-none focus:border-violet-500 dark:focus:border-violet-500 focus:ring-1 focus:ring-violet-500 transition-all gap-6"
      />

      {/* 검색 버튼 (돋보기 아이콘) */}
      <button 
        type="submit" 
        className="absolute right-3 text-neutral-400 group-focus-within:text-violet-600 transition-colors"
      >
        <Search size={16} />
      </button>
    </form>
  );
}